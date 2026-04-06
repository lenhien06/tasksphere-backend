package com.zone.tasksphere.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zone.tasksphere.entity.enums.WorkspacePlan;
import com.zone.tasksphere.entity.enums.WorkspaceRole;
import com.zone.tasksphere.entity.enums.WorkspaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Workspace Response")
public class WorkspaceResponse {

    private UUID id;
    private String name;
    private String slug;
    private String description;
    private String avatarUrl;
    private WorkspacePlan plan;
    private WorkspaceType type;
    private int memberCount;
    private int projectCount;

    /** Role của user hiện tại trong workspace (null nếu không phải member). */
    private WorkspaceRole role;

    private UUID ownerId;
    private String ownerName;
    private Instant createdAt;
    private Instant updatedAt;
}
