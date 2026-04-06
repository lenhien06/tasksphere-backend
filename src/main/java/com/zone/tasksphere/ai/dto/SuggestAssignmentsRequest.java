package com.zone.tasksphere.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class SuggestAssignmentsRequest {
    /** Optional — if provided, only these task IDs are scored. null/empty = all unassigned tasks. */
    private List<String> taskIds;
}
