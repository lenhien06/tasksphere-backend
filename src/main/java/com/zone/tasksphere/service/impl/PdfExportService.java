package com.zone.tasksphere.service.impl;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.Task;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

@Service
@Slf4j
public class PdfExportService {

    private static final Font HEADER_FONT = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
    private static final Font DATA_FONT = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, BaseColor.DARK_GRAY);
    private static final Font TITLE_FONT = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, BaseColor.DARK_GRAY);
    private static final BaseColor HEADER_BG = new BaseColor(30, 60, 120);
    private static final BaseColor ALT_ROW_BG = new BaseColor(245, 245, 245);

    public byte[] exportTasksToPdf(List<Task> tasks, Project project) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4.rotate(), 20, 20, 30, 30);
            PdfWriter.getInstance(document, out);
            document.open();

            // Title
            Paragraph title = new Paragraph(
                "Project Tasks: " + project.getName(), TITLE_FONT);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(5f);
            document.add(title);

            Paragraph subtitle = new Paragraph(
                "Export Date: " + LocalDate.now() + " | Total: " + tasks.size() + " tasks",
                new Font(Font.FontFamily.HELVETICA, 9, Font.ITALIC));
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(15f);
            document.add(subtitle);

            // Table
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1.5f, 3.5f, 1.2f, 1.5f, 1.2f, 2f, 1.5f, 1f});

            // Headers
            String[] headers = {"Code", "Title", "Type", "Status", "Priority", "Assignee", "Sprint", "Story Pts"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, HEADER_FONT));
                cell.setBackgroundColor(HEADER_BG);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(6f);
                table.addCell(cell);
            }

            // Data rows
            int rowIndex = 0;
            for (Task task : tasks) {
                BaseColor bg = (rowIndex % 2 == 0) ? BaseColor.WHITE : ALT_ROW_BG;

                addCell(table, task.getTaskCode(), bg);
                addCell(table, task.getTitle(), bg);
                addCell(table, task.getType() != null ? task.getType().name() : "", bg);
                addCell(table, task.getTaskStatus() != null ? task.getTaskStatus().name() : "", bg);
                addCell(table, task.getPriority() != null ? task.getPriority().name() : "", bg);
                addCell(table, task.getAssignee() != null ? task.getAssignee().getFullName() : "Unassigned", bg);
                addCell(table, task.getSprint() != null ? task.getSprint().getName() : "Backlog", bg);
                addCell(table, task.getStoryPoints() != null ? task.getStoryPoints().toString() : "0", bg);
                rowIndex++;
            }

            document.add(table);
            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Export PDF thất bại: " + e.getMessage(), e);
        }
    }

    private void addCell(PdfPTable table, String text, BaseColor bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", DATA_FONT));
        cell.setBackgroundColor(bg);
        cell.setPadding(4f);
        table.addCell(cell);
    }
}
