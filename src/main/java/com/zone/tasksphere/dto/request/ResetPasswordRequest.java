package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Reset Password Request")
public class ResetPasswordRequest {
    @NotBlank(message = "Email không được để trống")
    @Schema(description = "Email", example = "user@example.com")
    private String email;

    @NotBlank(message = "Vui lòng nhập mã OTP")
    @Schema(description = "Otp", example = "123456")
    private String otp;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, message = "Mật khẩu phải có ít nhất 6 ký tự")
    @Schema(description = "New password", example = "Password123!")
    private String newPassword;

    @NotBlank(message = "Xác nhận mật khẩu không được để trống")
    @Schema(description = "Confirm password", example = "Password123!")
    private String confirmPassword;
}
