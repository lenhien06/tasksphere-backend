package com.zone.tasksphere.dto.request;

import com.zone.tasksphere.entity.enums.DependencyType;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
@Schema(description = "Add Dependency / Link Request")
public class AddDependencyRequest {

    @Schema(description = "Target task id (preferred)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID targetTaskId;

    @Schema(description = "Link type from current task's perspective", example = "BLOCKS",
            allowableValues = {"BLOCKS", "BLOCKED_BY", "RELATES_TO", "DUPLICATES", "IS_DUPLICATED_BY"})
    private DependencyType linkType = DependencyType.BLOCKS;

    /**
     * @deprecated use targetTaskId instead. Kept for backward compatibility.
     * If targetTaskId is null, dependsOnTaskId is used and linkType defaults to BLOCKS.
     */
    @Deprecated
    @Schema(description = "Depends on task id (deprecated, use targetTaskId)", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID dependsOnTaskId;

    /** Resolve the effective target task id */
    public UUID resolveTargetTaskId() {
        return targetTaskId != null ? targetTaskId : dependsOnTaskId;
    }
}
