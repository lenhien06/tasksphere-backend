package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.Role;
import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Role Dto")
public class RoleDto {

    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    Long id;
    @Schema(description = "Slug", example = "string")
    String slug;
    @Schema(description = "Display name", example = "John Doe")
    String displayName;
    @Schema(description = "Description", example = "Description of the item")
    String description;
    @Schema(description = "Is system", example = "true")
    Boolean isSystem;

    // Constructor map từ Entity sang DTO
    public RoleDto(Role role) {
        if (role != null) {
            this.id = role.getId();
            this.slug = role.getSlug();
            this.displayName = role.getDisplayName();
            this.description = role.getDescription();
            this.isSystem = role.getIsSystem();
        }
    }
}