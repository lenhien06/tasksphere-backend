package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Send OTP Request")
public class SendOtpRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    @Schema(description = "Email", example = "user@example.com")
    private String email;

    @NotBlank(message = "Vui lòng hoàn tất xác minh bảo mật")
    @Schema(description = "Cloudflare Turnstile token", example = "0.xxxxxxxxxxxxx")
    private String turnstileToken;
}
