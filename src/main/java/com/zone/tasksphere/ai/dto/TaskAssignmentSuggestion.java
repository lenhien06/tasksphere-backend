package com.zone.tasksphere.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TaskAssignmentSuggestion {
    private String taskId;
    private String taskTitle;
    private String taskType;
    private String taskPriority;
    private Integer storyPoints;
    private List<String> skillTagsRequired;
    private String noSuggestionReason;

    /** Top-3 member suggestions sorted by total_score DESC */
    private List<MemberSuggestion> topSuggestions;
}
