package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@Slf4j
public class ExcelExportService {

    private static final String[] HEADERS = {
        "Task Code", "Title", "Type", "Status", "Priority",
        "Assignee", "Reporter", "Sprint", "Story Points",
        "Due Date", "Created At", "Updated At"
    };

    public byte[] exportTasksToExcel(List<Task> tasks, Project project) {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Tasks - " + project.getProjectKey());

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            Font headerFont = wb.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);

            // Header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            CellStyle dateCellStyle = wb.createCellStyle();
            CreationHelper createHelper = wb.getCreationHelper();
            dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));

            int rowNum = 1;
            for (Task task : tasks) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(task.getTaskCode());
                row.createCell(1).setCellValue(task.getTitle());
                row.createCell(2).setCellValue(task.getType() != null ? task.getType().name() : "");
                row.createCell(3).setCellValue(task.getTaskStatus() != null ? task.getTaskStatus().name() : "");
                row.createCell(4).setCellValue(task.getPriority() != null ? task.getPriority().name() : "");
                row.createCell(5).setCellValue(
                    task.getAssignee() != null ? task.getAssignee().getFullName() : "Unassigned");
                row.createCell(6).setCellValue(
                    task.getReporter() != null ? task.getReporter().getFullName() : "");
                row.createCell(7).setCellValue(
                    task.getSprint() != null ? task.getSprint().getName() : "Backlog");
                row.createCell(8).setCellValue(
                    task.getStoryPoints() != null ? task.getStoryPoints() : 0);
                row.createCell(9).setCellValue(
                    task.getDueDate() != null ? task.getDueDate().toString() : "");
                row.createCell(10).setCellValue(
                    task.getCreatedAt() != null ? task.getCreatedAt().toString() : "");
                row.createCell(11).setCellValue(
                    task.getUpdatedAt() != null ? task.getUpdatedAt().toString() : "");
            }

            // Auto-size columns
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
                // Cap max width
                int width = sheet.getColumnWidth(i);
                if (width > 8000) sheet.setColumnWidth(i, 8000);
            }

            wb.write(out);
            return out.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("Export Excel thất bại: " + e.getMessage(), e);
        }
    }
}
