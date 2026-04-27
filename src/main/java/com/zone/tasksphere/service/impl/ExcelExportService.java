package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.Sprint;
import com.zone.tasksphere.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class ExcelExportService {

    private static final String[] TASK_HEADERS = {
        "Task Code", "Title", "Priority", "Assignee", "Sprint", "Actual Hours"
    };
    private static final String[] VELOCITY_HEADERS = {
        "Task Code", "Title", "Priority", "Assignee", "Due Date"
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
            for (int i = 0; i < TASK_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(TASK_HEADERS[i]);
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
                createTextCell(row, 2, task.getPriority() != null ? task.getPriority().name() : "", textStyle);
                createTextCell(row, 3, task.getAssignee() != null ? task.getAssignee().getFullName() : "Unassigned", textStyle);
                createTextCell(row, 4, task.getSprint() != null ? task.getSprint().getName() : "Backlog", textStyle);

                Cell actualHoursCell = row.createCell(5);
                actualHoursCell.setCellValue(task.getActualHours() != null ? task.getActualHours().doubleValue() : 0D);
                actualHoursCell.setCellStyle(numberStyle);
            }

            int[] columnWidths = { 4200, 14000, 4800, 7200, 7200, 4200 };
            for (int i = 0; i < TASK_HEADERS.length; i++) {
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

    public byte[] exportVelocityToExcel(List<Task> tasks, Project project, List<Sprint> sprints) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Velocity - " + project.getProjectKey());
            sheet.createFreezePane(0, 1);

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

            CellStyle numberStyle = wb.createCellStyle();
            numberStyle.cloneStyleFrom(textStyle);
            numberStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle sprintStyle = wb.createCellStyle();
            sprintStyle.cloneStyleFrom(textStyle);
            sprintStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            sprintStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            sprintStyle.setBorderTop(BorderStyle.THIN);
            sprintStyle.setBorderBottom(BorderStyle.THIN);
            Font sprintFont = wb.createFont();
            sprintFont.setFontName("Arial");
            sprintFont.setBold(true);
            sprintFont.setFontHeightInPoints((short) 10);
            sprintStyle.setFont(sprintFont);

            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(22);
            for (int i = 0; i < VELOCITY_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(VELOCITY_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            double hoursPerStoryPoint = computeHoursPerStoryPoint(sprints);
            List<Sprint> orderedSprints = sprints == null ? List.of() : sprints.stream()
                .sorted(Comparator
                    .comparing(Sprint::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Sprint::getCompletedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

            int rowNum = 1;
            for (Sprint sprint : orderedSprints) {
                Row sprintRow = sheet.createRow(rowNum++);
                sprintRow.setHeightInPoints(20);
                for (int i = 0; i < VELOCITY_HEADERS.length; i++) {
                    Cell cell = sprintRow.createCell(i);
                    cell.setCellStyle(sprintStyle);
                }
                sprintRow.getCell(0).setCellValue(sprint.getName());
                sheet.addMergedRegion(new CellRangeAddress(sprintRow.getRowNum(), sprintRow.getRowNum(), 0, VELOCITY_HEADERS.length - 1));

                List<Task> sprintTasks = tasks.stream()
                    .filter(task -> task.getSprint() != null && sprint.getId().equals(task.getSprint().getId()))
                    .sorted(Comparator.comparing(Task::getTaskCode, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                    .toList();

                for (Task task : sprintTasks) {
                    Row row = sheet.createRow(rowNum++);
                    row.setHeightInPoints(20);

                    createTextCell(row, 0, task.getTaskCode(), textStyle);
                    createTextCell(row, 1, task.getTitle(), textStyle);
                    createTextCell(row, 2, task.getPriority() != null ? task.getPriority().name() : "", textStyle);
                    createTextCell(row, 3, task.getAssignee() != null ? task.getAssignee().getFullName() : "Unassigned", textStyle);

                    Cell convertedHoursCell = row.createCell(4);
                    convertedHoursCell.setCellValue(estimateHoursFromStoryPoints(task.getStoryPoints(), hoursPerStoryPoint));
                    convertedHoursCell.setCellStyle(numberStyle);
                }
            }

            int[] columnWidths = { 4200, 16000, 4800, 7200, 4200 };
            for (int i = 0; i < VELOCITY_HEADERS.length; i++) {
                sheet.setColumnWidth(i, columnWidths[i]);
            }

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Export Excel tháº¥t báº¡i: " + e.getMessage(), e);
        }
    }

    private void createTextCell(Row row, int index, String value, CellStyle style) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value != null ? value : "");
        cell.setCellStyle(style);
    }

    private double computeHoursPerStoryPoint(List<Sprint> sprints) {
        double averagePointsPerDay = (sprints == null ? List.<Sprint>of() : sprints).stream()
            .filter(sprint -> sprint.getVelocity() != null && sprint.getVelocity() > 0)
            .filter(sprint -> sprint.getStartDate() != null && sprint.getEndDate() != null)
            .mapToDouble(sprint -> {
                long days = ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate()) + 1;
                long safeDays = Math.max(days, 1);
                return sprint.getVelocity() / (double) safeDays;
            })
            .filter(pointsPerDay -> pointsPerDay > 0)
            .average()
            .orElse(0.0);

        if (averagePointsPerDay <= 0.0) {
            return 2.0;
        }
        return roundToOneDecimal(8.0 / averagePointsPerDay);
    }

    private double estimateHoursFromStoryPoints(Integer storyPoints, double hoursPerStoryPoint) {
        if (storyPoints == null || storyPoints <= 0) {
            return 0.0;
        }
        return roundToOneDecimal(storyPoints * hoursPerStoryPoint);
    }

    private double roundToOneDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
