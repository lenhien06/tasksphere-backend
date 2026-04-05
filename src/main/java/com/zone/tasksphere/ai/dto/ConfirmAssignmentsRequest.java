package com.zone.tasksphere.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ConfirmAssignmentsRequest {

    @NotEmpty(message = "assignments must not be empty")
    private List<AssignmentItem> assignments;

    @Data
    public static class AssignmentItem {
        private String taskId;
        private String assigneeId;
        /** null → PM manually chose someone not from AI top-3 */
        private String suggestionId;
    }
}
