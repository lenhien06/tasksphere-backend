package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.zone.tasksphere.entity.enums.ProjectStatus;
import com.zone.tasksphere.entity.enums.ProjectVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Project Response")
public class ProjectResponse {
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Name", example = "John Doe")
    private String name;
    @Schema(description = "Project key", example = "PROJ-123")
    private String projectKey;
    @Schema(description = "Description", example = "Description of the item")
    private String description;
    @Schema(description = "Status", example = "ACTIVE")
    private ProjectStatus status;
    @Schema(description = "Visibility", example = "example")
    private ProjectVisibility visibility;
    @Schema(description = "Owner id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID ownerId;
    @Schema(description = "Owner name", example = "John Doe")
    private String ownerName;
    @Schema(description = "Start date", example = "2023-12-31T23:59:59Z")
    private Instant startDate;
    @Schema(description = "End date", example = "2023-12-31T23:59:59Z")
    private Instant endDate;
    @Schema(description = "Members", example = "[]")
    private List<ProjectMemberResponse> members;
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;
    @Schema(description = "Updated at", example = "2023-12-31T23:59:59Z")
    private Instant updatedAt;

    // Computed fields for project list/summary
    @Schema(description = "Progress", example = "10.5")
    private Double progress;
    @Schema(description = "Member count", example = "10")
    private Long memberCount;
    @Schema(description = "Task stats", example = "example")
    private TaskStats taskStats;
    @Schema(description = "My role", example = "string")
    private String myRole;
    @JsonProperty("isOwner")
    @Schema(description = "Current user is owner", example = "true")
    private boolean owner;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
@Schema(description = "Task Stats")
public static class TaskStats {
        @Schema(description = "Total", example = "10")
        private Long total;
        @Schema(description = "Done", example = "1")
        private Long done;
        @Schema(description = "Overdue", example = "1")
        private Long overdue;
    }
}
