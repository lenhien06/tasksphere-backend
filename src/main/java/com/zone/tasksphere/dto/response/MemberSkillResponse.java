package com.zone.tasksphere.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MemberSkillResponse {
    private String userId;
    private String fullName;
    private String avatarUrl;
    private String role;
    private List<String> skillTags;
    private int activeTaskCount;
    private BigDecimal avgStoryPoints;
    private int workCapacityHours;
}
