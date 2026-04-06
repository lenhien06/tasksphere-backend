package com.zone.tasksphere.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ConfirmTasksResponse {
    private List<String> createdTaskIds;
    private int          count;
    private int          memberCount;
    private String       projectId;
}
