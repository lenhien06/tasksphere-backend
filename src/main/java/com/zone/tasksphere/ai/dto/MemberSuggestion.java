package com.zone.tasksphere.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MemberSuggestion {

    private String suggestionId;
    private String userId;
    private String fullName;
    private String avatarUrl;
    private List<String> skillTags;
    private int activeTaskCount;

    @JsonProperty("skill_score")
    private BigDecimal skillScore;

    @JsonProperty("workload_score")
    private BigDecimal workloadScore;

    @JsonProperty("difficulty_score")
    private BigDecimal difficultyScore;

    @JsonProperty("total_score")
    private BigDecimal totalScore;

    private String reasonText;
}
