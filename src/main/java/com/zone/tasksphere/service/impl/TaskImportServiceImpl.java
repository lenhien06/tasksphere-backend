package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.CreateTaskRequest;
import com.zone.tasksphere.dto.request.TaskImportRowDto;
import com.zone.tasksphere.dto.response.TaskImportErrorDto;
import com.zone.tasksphere.dto.response.TaskImportResultResponse;
import com.zone.tasksphere.entity.Sprint;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskType;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.SprintRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.service.TaskImportService;
import com.zone.tasksphere.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TaskImportServiceImpl implements TaskImportService {

    private final TaskService taskService;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;
    private final SprintRepository sprintRepository;

    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String[] HEADERS = {
        "Title", "Description", "Type", "Priority", "DueDate",
        "StartDate", "StoryPoints", "EstimatedHours", "AssigneeEmail", "SprintName"
    };

    private static final String[] HEADER_NOTES = {
        "Bắt buộc. Tiêu đề task, tối đa 255 ký tự.",
        "Tùy chọn. Mô tả chi tiết cho task.",
        "Tùy chọn. Loại task: TASK, BUG, FEATURE, STORY, EPIC. Mặc định: TASK.",
        "Tùy chọn. Độ ưu tiên: LOW, MEDIUM, HIGH, CRITICAL. Mặc định: MEDIUM.",
        "Tùy chọn. Ngày hết hạn, định dạng yyyy-MM-dd (vd: 2026-05-15). Phải >= ngày hôm nay.",
        "Tùy chọn. Ngày bắt đầu, định dạng yyyy-MM-dd (vd: 2026-05-01).",
        "Tùy chọn. Story points, số nguyên từ 1 đến 100.",
        "Tùy chọn. Số giờ ước tính, số thập phân >= 0 (vd: 8.5).",
        "Tùy chọn. Email của thành viên trong dự án.",
        "Tùy chọn. Tên sprint trong dự án. Để trống = Backlog."
    };

    // ── Template generation ────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public byte[] generateTemplate() throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Tasks");
            sheet.createFreezePane(0, 1);

            XSSFCellStyle titleColStyle = buildHeaderStyle(workbook,
                new byte[]{(byte) 245, (byte) 158, (byte) 11}); // amber for required col
            XSSFCellStyle defaultHeaderStyle = buildHeaderStyle(workbook,
                new byte[]{(byte) 59, (byte) 130, (byte) 246}); // blue

            XSSFRow headerRow = sheet.createRow(0);
            headerRow.setHeight((short) 700);

            CreationHelper helper = workbook.getCreationHelper();
            XSSFDrawing drawing = sheet.createDrawingPatriarch();

            for (int col = 0; col < HEADERS.length; col++) {
                XSSFCell cell = headerRow.createCell(col);
                cell.setCellValue(HEADERS[col]);
                cell.setCellStyle(col == 0 ? titleColStyle : defaultHeaderStyle);

                ClientAnchor anchor = helper.createClientAnchor();
                anchor.setCol1(col);
                anchor.setRow1(0);
                anchor.setCol2(col + 3);
                anchor.setRow2(4);
                Comment comment = drawing.createCellComment(anchor);
                comment.setString(helper.createRichTextString(HEADER_NOTES[col]));
                comment.setAuthor("TaskSphere");
                cell.setCellComment(comment);
            }

            int[] colWidths = {8000, 10000, 4000, 4000, 4500, 4500, 4000, 5000, 10000, 6000};
            for (int i = 0; i < colWidths.length; i++) {
                sheet.setColumnWidth(i, colWidths[i]);
            }

            addDropdown(sheet, 2, new String[]{"TASK", "BUG", "FEATURE", "STORY", "EPIC"});
            addDropdown(sheet, 3, new String[]{"LOW", "MEDIUM", "HIGH", "CRITICAL"});
            addIntegerConstraint(sheet, 6, 1, 100);
            addDecimalConstraint(sheet, 7);

            XSSFCellStyle dateStyle = buildDateStyle(workbook);
            sheet.setDefaultColumnStyle(4, dateStyle);
            sheet.setDefaultColumnStyle(5, dateStyle);

            addExampleRows(sheet, helper, dateStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private XSSFCellStyle buildHeaderStyle(XSSFWorkbook wb, byte[] rgb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFColor color = new XSSFColor(rgb, null);
        style.setFillForegroundColor(color);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        return style;
    }

    private XSSFCellStyle buildDateStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        CreationHelper helper = wb.getCreationHelper();
        style.setDataFormat(helper.createDataFormat().getFormat("yyyy-MM-dd"));
        return style;
    }

    private void addDropdown(XSSFSheet sheet, int col, String[] values) {
        XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(sheet);
        DataValidationConstraint constraint = dvHelper.createExplicitListConstraint(values);
        CellRangeAddressList range = new CellRangeAddressList(1, 1000, col, col);
        DataValidation dv = dvHelper.createValidation(constraint, range);
        dv.setShowDropDownArrow(true);
        dv.setSuppressDropDownArrow(false);
        sheet.addValidationData(dv);
    }

    private void addIntegerConstraint(XSSFSheet sheet, int col, int min, int max) {
        XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(sheet);
        DataValidationConstraint constraint = dvHelper.createIntegerConstraint(
            DataValidationConstraint.OperatorType.BETWEEN,
            String.valueOf(min), String.valueOf(max)
        );
        CellRangeAddressList range = new CellRangeAddressList(1, 1000, col, col);
        DataValidation dv = dvHelper.createValidation(constraint, range);
        dv.setShowErrorBox(true);
        dv.createErrorBox("Lỗi nhập liệu", "Story Points phải là số nguyên từ " + min + " đến " + max);
        sheet.addValidationData(dv);
    }

    private void addDecimalConstraint(XSSFSheet sheet, int col) {
        XSSFDataValidationHelper dvHelper = new XSSFDataValidationHelper(sheet);
        DataValidationConstraint constraint = dvHelper.createDecimalConstraint(
            DataValidationConstraint.OperatorType.GREATER_OR_EQUAL, "0", null
        );
        CellRangeAddressList range = new CellRangeAddressList(1, 1000, col, col);
        DataValidation dv = dvHelper.createValidation(constraint, range);
        dv.setShowErrorBox(true);
        dv.createErrorBox("Lỗi nhập liệu", "Số giờ ước tính phải >= 0");
        sheet.addValidationData(dv);
    }

    private void addExampleRows(XSSFSheet sheet, CreationHelper helper, XSSFCellStyle dateStyle) {
        String[][] examples = {
            {"Thiết kế giao diện đăng nhập", "Thiết kế UI/UX cho form đăng nhập", "TASK", "HIGH", "2026-06-15", "2026-06-01", "5", "8", "", ""},
            {"Sửa lỗi form đăng ký", "Bug: validation bỏ qua ký tự đặc biệt", "BUG", "CRITICAL", "2026-06-10", "", "2", "3", "", "Sprint 1"},
            {"Implement API xác thực", "REST API JWT authentication", "FEATURE", "MEDIUM", "", "", "8", "16", "", ""},
        };
        for (int r = 0; r < examples.length; r++) {
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < examples[r].length; c++) {
                Cell cell = row.createCell(c);
                String val = examples[r][c];
                if (val.isEmpty()) continue;
                if (c == 6) {
                    cell.setCellValue(Double.parseDouble(val));
                } else if (c == 7) {
                    cell.setCellValue(Double.parseDouble(val));
                } else if ((c == 4 || c == 5) && !val.isEmpty()) {
                    cell.setCellValue(val);
                    cell.setCellStyle(dateStyle);
                } else {
                    cell.setCellValue(val);
                }
            }
        }
    }

    // ── Import ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public TaskImportResultResponse importTasks(UUID projectId, MultipartFile file, UUID currentUserId) throws IOException {
        // 1. Permission check
        var memberOpt = projectMemberRepository.findByProject_IdAndUser_Id(projectId, currentUserId);
        if (memberOpt.isEmpty()) {
            Forbidden ex = new Forbidden();
            ex.setMessage("Bạn không phải thành viên của dự án này");
            throw ex;
        }
        if (memberOpt.get().getProjectRole() == ProjectRole.VIEWER) {
            Forbidden ex = new Forbidden();
            ex.setMessage("Chỉ Project Manager hoặc Member mới có thể import task");
            throw ex;
        }

        // 2. File validation
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (!filename.endsWith(".xlsx")) {
            throw new IllegalArgumentException("Chỉ chấp nhận file .xlsx. Vui lòng sử dụng đúng định dạng file.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File vượt quá giới hạn 5MB. Vui lòng chia nhỏ file và thử lại.");
        }

        // 3. Pre-load lookup data
        List<Sprint> projectSprints = sprintRepository.findByProject_IdAndDeletedAtIsNull(projectId);
        Map<String, UUID> sprintNameToId = projectSprints.stream()
            .collect(Collectors.toMap(
                s -> s.getName().toLowerCase().trim(),
                Sprint::getId,
                (a, b) -> a
            ));
        Map<String, Optional<UUID>> emailCache = new HashMap<>();

        // 4. Parse rows
        List<TaskImportRowDto> rows = new ArrayList<>();
        List<TaskImportErrorDto> errors = new ArrayList<>();

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            XSSFSheet sheet = workbook.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();

            for (int i = 1; i <= lastRow; i++) {
                XSSFRow row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                int rowNum = i + 1;
                TaskImportRowDto dto = parseRow(row, rowNum);
                rows.add(dto);
                validateRow(dto, projectId, sprintNameToId, emailCache, errors);
            }
        }

        int totalRows = rows.size();
        if (!errors.isEmpty()) {
            return TaskImportResultResponse.builder()
                .totalRows(totalRows)
                .createdCount(0)
                .errors(errors)
                .build();
        }

        // 5. Create tasks (all-or-nothing in one transaction)
        int createdCount = 0;
        for (TaskImportRowDto row : rows) {
            CreateTaskRequest request = buildCreateRequest(row, projectId, sprintNameToId, emailCache);
            taskService.createTask(projectId, request, currentUserId);
            createdCount++;
        }

        log.info("Import tasks: {} rows created for project {}", createdCount, projectId);
        return TaskImportResultResponse.builder()
            .totalRows(totalRows)
            .createdCount(createdCount)
            .errors(Collections.emptyList())
            .build();
    }

    // ── Row parsing ────────────────────────────────────────────────────

    private TaskImportRowDto parseRow(XSSFRow row, int rowNum) {
        TaskImportRowDto dto = new TaskImportRowDto();
        dto.setRowNumber(rowNum);
        dto.setTitle(getCellString(row.getCell(0)));
        dto.setDescription(getCellString(row.getCell(1)));
        dto.setType(getCellString(row.getCell(2)));
        dto.setPriority(getCellString(row.getCell(3)));
        dto.setDueDate(getCellString(row.getCell(4)));
        dto.setStartDate(getCellString(row.getCell(5)));
        dto.setStoryPoints(getCellString(row.getCell(6)));
        dto.setEstimatedHours(getCellString(row.getCell(7)));
        dto.setAssigneeEmail(getCellString(row.getCell(8)));
        dto.setSprintName(getCellString(row.getCell(9)));
        return dto;
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield date.format(DATE_FMT);
                }
                double val = cell.getNumericCellValue();
                if (val == Math.floor(val) && !Double.isInfinite(val)) {
                    yield String.valueOf((long) val);
                }
                yield String.valueOf(val);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) {
                    double v = cell.getNumericCellValue();
                    yield (v == Math.floor(v) && !Double.isInfinite(v))
                        ? String.valueOf((long) v) : String.valueOf(v);
                }
            }
            default -> "";
        };
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = 0; i < 10; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String val = getCellString(cell);
                if (!val.isBlank()) return false;
            }
        }
        return true;
    }

    // ── Validation ─────────────────────────────────────────────────────

    private void validateRow(
            TaskImportRowDto dto,
            UUID projectId,
            Map<String, UUID> sprintNameToId,
            Map<String, Optional<UUID>> emailCache,
            List<TaskImportErrorDto> errors) {

        int row = dto.getRowNumber();

        // Title
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            errors.add(new TaskImportErrorDto(row, "Title", "Tiêu đề không được để trống"));
        } else if (dto.getTitle().length() > 255) {
            errors.add(new TaskImportErrorDto(row, "Title", "Tiêu đề tối đa 255 ký tự (hiện tại: " + dto.getTitle().length() + ")"));
        }

        // Type
        if (!dto.getType().isBlank()) {
            Set<String> validTypes = Set.of("TASK", "BUG", "FEATURE", "STORY", "EPIC");
            if (!validTypes.contains(dto.getType().toUpperCase())) {
                errors.add(new TaskImportErrorDto(row, "Type",
                    "Type không hợp lệ '" + dto.getType() + "', các giá trị cho phép: TASK, BUG, FEATURE, STORY, EPIC"));
            }
        }

        // Priority
        if (!dto.getPriority().isBlank()) {
            Set<String> validPriorities = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
            if (!validPriorities.contains(dto.getPriority().toUpperCase())) {
                errors.add(new TaskImportErrorDto(row, "Priority",
                    "Priority không hợp lệ '" + dto.getPriority() + "', các giá trị cho phép: LOW, MEDIUM, HIGH, CRITICAL"));
            }
        }

        // DueDate
        if (!dto.getDueDate().isBlank()) {
            try {
                LocalDate due = LocalDate.parse(dto.getDueDate(), DATE_FMT);
                if (due.isBefore(LocalDate.now())) {
                    errors.add(new TaskImportErrorDto(row, "DueDate",
                        "DueDate '" + dto.getDueDate() + "' phải >= ngày hôm nay (" + LocalDate.now().format(DATE_FMT) + ")"));
                }
            } catch (DateTimeParseException e) {
                errors.add(new TaskImportErrorDto(row, "DueDate",
                    "Ngày phải có format yyyy-MM-dd, ví dụ: 2026-05-15 (nhận được: '" + dto.getDueDate() + "')"));
            }
        }

        // StartDate
        if (!dto.getStartDate().isBlank()) {
            try {
                LocalDate.parse(dto.getStartDate(), DATE_FMT);
            } catch (DateTimeParseException e) {
                errors.add(new TaskImportErrorDto(row, "StartDate",
                    "Ngày phải có format yyyy-MM-dd, ví dụ: 2026-05-01 (nhận được: '" + dto.getStartDate() + "')"));
            }
        }

        // StoryPoints
        if (!dto.getStoryPoints().isBlank()) {
            try {
                int sp = Integer.parseInt(dto.getStoryPoints());
                if (sp < 1 || sp > 100) {
                    errors.add(new TaskImportErrorDto(row, "StoryPoints",
                        "StoryPoints phải là số nguyên từ 1 đến 100 (nhận được: " + sp + ")"));
                }
            } catch (NumberFormatException e) {
                errors.add(new TaskImportErrorDto(row, "StoryPoints",
                    "StoryPoints phải là số nguyên (nhận được: '" + dto.getStoryPoints() + "')"));
            }
        }

        // EstimatedHours
        if (!dto.getEstimatedHours().isBlank()) {
            try {
                BigDecimal hours = new BigDecimal(dto.getEstimatedHours());
                if (hours.compareTo(BigDecimal.ZERO) < 0) {
                    errors.add(new TaskImportErrorDto(row, "EstimatedHours",
                        "EstimatedHours phải >= 0 (nhận được: " + dto.getEstimatedHours() + ")"));
                }
            } catch (NumberFormatException e) {
                errors.add(new TaskImportErrorDto(row, "EstimatedHours",
                    "EstimatedHours phải là số thập phân (nhận được: '" + dto.getEstimatedHours() + "')"));
            }
        }

        // AssigneeEmail
        if (!dto.getAssigneeEmail().isBlank()) {
            String email = dto.getAssigneeEmail().toLowerCase().trim();
            Optional<UUID> assigneeId = emailCache.computeIfAbsent(email, e ->
                userRepository.findByEmail(e)
                    .filter(u -> projectMemberRepository.existsByProject_IdAndUser_Id(projectId, u.getId()))
                    .map(u -> u.getId())
            );
            if (assigneeId.isEmpty()) {
                errors.add(new TaskImportErrorDto(row, "AssigneeEmail",
                    "Email '" + dto.getAssigneeEmail() + "' không phải thành viên của dự án"));
            }
        }

        // SprintName
        if (!dto.getSprintName().isBlank()) {
            String nameKey = dto.getSprintName().toLowerCase().trim();
            if (!sprintNameToId.containsKey(nameKey)) {
                errors.add(new TaskImportErrorDto(row, "SprintName",
                    "Sprint '" + dto.getSprintName() + "' không tồn tại trong dự án"));
            }
        }
    }

    // ── Build CreateTaskRequest ────────────────────────────────────────

    private CreateTaskRequest buildCreateRequest(
            TaskImportRowDto row,
            UUID projectId,
            Map<String, UUID> sprintNameToId,
            Map<String, Optional<UUID>> emailCache) {

        CreateTaskRequest req = new CreateTaskRequest();
        req.setTitle(row.getTitle());

        if (!row.getDescription().isBlank()) req.setDescription(row.getDescription());

        if (!row.getType().isBlank()) {
            req.setType(TaskType.valueOf(row.getType().toUpperCase()));
        }

        if (!row.getPriority().isBlank()) {
            req.setPriority(TaskPriority.valueOf(row.getPriority().toUpperCase()));
        }

        if (!row.getDueDate().isBlank()) {
            req.setDueDate(LocalDate.parse(row.getDueDate(), DATE_FMT));
        }

        if (!row.getStartDate().isBlank()) {
            req.setStartDate(LocalDate.parse(row.getStartDate(), DATE_FMT));
        }

        if (!row.getStoryPoints().isBlank()) {
            req.setStoryPoints(Integer.parseInt(row.getStoryPoints()));
        }

        if (!row.getEstimatedHours().isBlank()) {
            req.setEstimatedHours(new BigDecimal(row.getEstimatedHours()));
        }

        if (!row.getAssigneeEmail().isBlank()) {
            emailCache.get(row.getAssigneeEmail().toLowerCase().trim())
                .ifPresent(req::setAssigneeId);
        }

        if (!row.getSprintName().isBlank()) {
            UUID sprintId = sprintNameToId.get(row.getSprintName().toLowerCase().trim());
            if (sprintId != null) req.setSprintId(sprintId);
        }

        return req;
    }
}
