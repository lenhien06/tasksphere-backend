package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.UserStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "User Detail")
public class UserDetail implements Serializable {

    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    UUID id;
    @Schema(description = "Full name", example = "John Doe")
    String fullName;
    @Schema(description = "Email", example = "user@example.com")
    String email;
    @Schema(description = "Avatar url", example = "https://example.com/image.png")
    String avatarUrl;
    @Schema(description = "System role", example = "example")
    SystemRole systemRole;
    @Schema(description = "Status", example = "ACTIVE")
    UserStatus status;
    @Schema(description = "Role", example = "example")
    RoleDto role;
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    Instant createdAt;
    @Schema(description = "Updated at", example = "2023-12-31T23:59:59Z")
    Instant updatedAt;

    public Collection<Object> getRoles() {
        if (this.role == null) {
            return java.util.Collections.emptyList();
        }
        return java.util.Collections.singletonList(this.role);
    }
}
