package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.Sprint;
import com.zone.tasksphere.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class ExcelExportService {

    private static final String[] HEADERS = {
        "Task Code", "Title", "Type", "Status", "Priority",
        "Assignee", "Reporter", "Sprint", "Story Points",
        "Due Date", "Created At", "Updated At"
    };

    public byte[] exportTasksToExcel(List<Task> tasks, Project project, List<Sprint> sprints) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Tasks - " + project.getProjectKey());
            sheet.createFreezePane(0, 1);

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            Font headerFont = wb.createFont();
            headerFont.setFontName("Arial");
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);

            CellStyle textStyle = wb.createCellStyle();
            textStyle.setWrapText(true);
            textStyle.setVerticalAlignment(VerticalAlignment.TOP);
            Font textFont = wb.createFont();
            textFont.setFontName("Arial");
            textFont.setFontHeightInPoints((short) 10);
            textStyle.setFont(textFont);

            // Header row
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            CellStyle numberStyle = wb.createCellStyle();
            numberStyle.cloneStyleFrom(textStyle);
            numberStyle.setAlignment(HorizontalAlignment.RIGHT);

            int rowNum = 1;
            for (Task task : tasks) {
                Row row = sheet.createRow(rowNum++);
                row.setHeightInPoints(20);

                createTextCell(row, 0, task.getTaskCode(), textStyle);
                createTextCell(row, 1, task.getTitle(), textStyle);
                createTextCell(row, 2, task.getType() != null ? task.getType().name() : "", textStyle);
                createTextCell(row, 3, task.getTaskStatus() != null ? task.getTaskStatus().name() : "", textStyle);
                createTextCell(row, 4, task.getPriority() != null ? task.getPriority().name() : "", textStyle);
                createTextCell(row, 5, task.getAssignee() != null ? task.getAssignee().getFullName() : "Unassigned", textStyle);
                createTextCell(row, 6, task.getReporter() != null ? task.getReporter().getFullName() : "", textStyle);
                createTextCell(row, 7, task.getSprint() != null ? task.getSprint().getName() : "Backlog", textStyle);

                Cell storyPointCell = row.createCell(8);
                storyPointCell.setCellValue(task.getStoryPoints() != null ? task.getStoryPoints() : 0);
                storyPointCell.setCellStyle(numberStyle);

                createTextCell(row, 9, task.getDueDate() != null ? task.getDueDate().toString() : "", textStyle);
                createTextCell(row, 10, task.getCreatedAt() != null ? task.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDateTime().toString() : "", textStyle);
                createTextCell(row, 11, task.getUpdatedAt() != null ? task.getUpdatedAt().atZone(ZoneOffset.UTC).toLocalDateTime().toString() : "", textStyle);
            }

            int[] columnWidths = { 4200, 14000, 4200, 5200, 4800, 7200, 7200, 7200, 4200, 4200, 6200, 6200 };
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, columnWidths[i]);
            }

            Sheet sprintSheet = wb.createSheet("Sprints");
            sprintSheet.createFreezePane(0, 1);
            String[] sprintHeaders = { "Sprint", "Status", "Goal", "Start Date", "End Date", "Velocity", "Task Count" };
            Row sprintHeaderRow = sprintSheet.createRow(0);
            sprintHeaderRow.setHeightInPoints(22);
            for (int i = 0; i < sprintHeaders.length; i++) {
                Cell cell = sprintHeaderRow.createCell(i);
                cell.setCellValue(sprintHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            List<Sprint> orderedSprints = sprints == null ? List.of() : sprints.stream()
                    .sorted(Comparator.comparing(Sprint::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            int sprintRowNum = 1;
            for (Sprint sprint : orderedSprints) {
                Row row = sprintSheet.createRow(sprintRowNum++);
                createTextCell(row, 0, sprint.getName(), textStyle);
                createTextCell(row, 1, sprint.getStatus() != null ? sprint.getStatus().name() : "", textStyle);
                createTextCell(row, 2, sprint.getGoal(), textStyle);
                createTextCell(row, 3, sprint.getStartDate() != null ? sprint.getStartDate().toString() : "", textStyle);
                createTextCell(row, 4, sprint.getEndDate() != null ? sprint.getEndDate().toString() : "", textStyle);

                Cell velocityCell = row.createCell(5);
                velocityCell.setCellValue(sprint.getVelocity() != null ? sprint.getVelocity() : 0);
                velocityCell.setCellStyle(numberStyle);

                long sprintTaskCount = tasks.stream()
                        .filter(task -> task.getSprint() != null && sprint.getId().equals(task.getSprint().getId()))
                        .count();
                Cell countCell = row.createCell(6);
                countCell.setCellValue(sprintTaskCount);
                countCell.setCellStyle(numberStyle);
            }

            int[] sprintColumnWidths = { 9000, 4200, 12000, 4200, 4200, 4200, 4200 };
            for (int i = 0; i < sprintHeaders.length; i++) {
                sprintSheet.setColumnWidth(i, sprintColumnWidths[i]);
            }

            wb.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Export Excel thất bại: " + e.getMessage(), e);
        }
    }

    private void createTextCell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }
}
