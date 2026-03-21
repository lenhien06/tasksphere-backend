package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "Signup Request")
public class SignupRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự")
    @Schema(description = "Full name", example = "John Doe")
    private String fullName;

    @NotBlank(message = "Mật khẩu không được để trống")
    // FIX: BR-07 - min 8 ký tự + uppercase + lowercase + digit + special char
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,100}$",
        message = "Mật khẩu phải từ 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt"
    )
    @Schema(description = "Password", example = "Password123!")
    private String password;

    @NotBlank(message = "Xác nhận mật khẩu không được để trống")
    @Schema(description = "Confirm password", example = "Password123!")
    private String confirmPassword;

    @NotBlank(message = "Vui lòng nhập mã OTP")
    @Schema(description = "Otp", example = "123456")
    private String otp;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Schema(description = "Email", example = "user@example.com")
    private String email;

    @Schema(description = "Invite token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String inviteToken;

    @AssertTrue(message = "{ERROR_ACCEPT_TERMS}")
    @Schema(description = "Accept terms", example = "true")
    private boolean acceptTerms;
}
