package com.zone.tasksphere.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SuggestAssignmentsResponse {
    private int totalTasks;
    private int totalSuggestions;
    private List<TaskAssignmentSuggestion> suggestions;
}
