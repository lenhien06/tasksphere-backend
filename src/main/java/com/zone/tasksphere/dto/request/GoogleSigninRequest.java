package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Google Sign-In Request")
public class GoogleSigninRequest {

    @NotBlank(message = "Google ID token is required")
    @Schema(description = "Google ID token returned by Google Identity Services", example = "eyJhbGciOiJSUzI1NiIsImtpZCI6Ij...")
    private String idToken;

    @NotBlank(message = "Security verification is required")
    @Schema(description = "Cloudflare Turnstile token", example = "0.xxxxxxxxxxxxx")
    private String turnstileToken;
}
