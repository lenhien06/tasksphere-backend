package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import lombok.Data;

@Data
@Schema(description = "Login Request")
public class LoginRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Schema(description = "Email", example = "user@example.com")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Schema(description = "Password", example = "Password123!")
    private String password;
}
