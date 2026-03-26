package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/email")
@RequiredArgsConstructor
@Tag(name = "Internal - Email", description = "Test sending email via SendGrid SMTP")
public class EmailTestController {

    private final EmailService emailService;

    @Operation(summary = "Send test email (OTP format)")
    @PostMapping("/test")
    public ResponseEntity<ApiResponse<String>> sendTest(
            @RequestParam("to") String to,
            @RequestParam(value = "otp", defaultValue = "123456") String otp) {
        emailService.sendOtpEmail(to, otp);
        return ResponseEntity.ok(ApiResponse.success("Sent test OTP to " + to));
    }
}
