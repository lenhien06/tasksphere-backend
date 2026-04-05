package com.zone.tasksphere.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AssignmentConfirmResult {
    private int totalAssigned;
    private int aiConfirmed;
    private int pmOverridden;
    /** Task IDs that failed validation (e.g. assignee not a project member – BR-16) */
    private List<String> failedTaskIds;
}
