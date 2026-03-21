package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Request create new user")
public class CreateUserRequest {
    @NotBlank(message = "Họ tên không được để trống")
    @Schema(description = "Họ tên", example = "Nguyễn Văn A")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Schema(description = "Email", example = "admin-create@example.com")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, message = "Mật khẩu phải có ít nhất 8 ký tự")
    @Schema(description = "Mật khẩu", example = "Password123!")
    private String password;

    @Schema(description = "Role ID", example = "1")
    private Long roleId;
}
