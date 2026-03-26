package com.zone.tasksphere.dto.request;

import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import lombok.Data;

import java.util.UUID;

/**
 * Filter parameters for Calendar View.
 */
@Data
public class CalendarFilterParams {
    private UUID projectId;
    private int year;
    private int month;
    private String q;
    private TaskStatus status;
    private String assigneeId;
    private UUID sprintId;
    private TaskPriority priority;
}
