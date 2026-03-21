package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Response cho GET /api/v1/tasks/{taskId}/dependencies */
@Data
@Builder
@Schema(description = "Task Dependencies Response")
public class TaskDependenciesResponse {

    /** Tasks đang block task này (task này phụ thuộc vào chúng) */
    @Schema(description = "Blocked by", example = "[]")
    private List<DependencyItem> blockedBy;

    /** Tasks mà task này đang block (chúng phụ thuộc vào task này) */
    @Schema(description = "Blocking", example = "[]")
    private List<DependencyItem> blocking;

    /** RELATES_TO, DUPLICATES, IS_DUPLICATED_BY links */
    @Schema(description = "Other links (RELATES_TO, DUPLICATES, IS_DUPLICATED_BY)", example = "[]")
    private List<DependencyItem> others;

    /** true nếu tất cả blockedBy đã DONE hoặc CANCELLED */
    @Schema(description = "Can transition to done", example = "true")
    private boolean canTransitionToDone;

    @Data
    @Builder
    @Schema(description = "Dependency Item")
    public static class DependencyItem {
        @Schema(description = "Link id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID depId;
        @Schema(description = "Link type", example = "BLOCKS")
        private String linkType;
        private DependencyResponse.TaskRef task;
    }
}
