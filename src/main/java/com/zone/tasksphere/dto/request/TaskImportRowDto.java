package com.zone.tasksphere.dto.request;

import lombok.Data;

@Data
public class TaskImportRowDto {
    private int rowNumber;
    private String title;
    private String description;
    private String type;
    private String priority;
    private String dueDate;
    private String startDate;
    private String storyPoints;
    private String estimatedHours;
    private String assigneeEmail;
    private String sprintName;
}
