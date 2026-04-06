package com.zone.tasksphere.dto.request;

import com.zone.tasksphere.entity.enums.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Mời thành viên vào workspace")
public class WorkspaceInviteMemberRequest {

    @Email(message = "Email không hợp lệ")
    @NotBlank(message = "Email không được để trống")
    @Schema(description = "Email người được mời", example = "user@example.com")
    private String email;

    @NotNull(message = "Role không được để trống")
    @Schema(description = "Vai trò trong workspace: ADMIN hoặc MEMBER", example = "MEMBER")
    private WorkspaceRole role;

    @Schema(description = "Danh sách kỹ năng (dùng cho AI scoring)", example = "[\"React\",\"TypeScript\"]")
    private List<String> skillTags;
}
