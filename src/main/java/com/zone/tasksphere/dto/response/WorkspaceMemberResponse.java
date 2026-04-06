package com.zone.tasksphere.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zone.tasksphere.entity.enums.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Workspace Member Response")
public class WorkspaceMemberResponse {

    private UUID userId;
    private String fullName;
    private String email;
    private String avatarUrl;
    private WorkspaceRole role;
    private List<String> skillTags;
    private int activeTaskCount;
    private BigDecimal avgStoryPoints;
    private Instant joinedAt;
}
