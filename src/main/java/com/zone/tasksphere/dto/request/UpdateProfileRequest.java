package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request update user profile")
public class UpdateProfileRequest {
    @Schema(description = "Full name", example = "Nguyễn Văn A")
    private String fullName;

    @Schema(description = "Avatar URL", example = "https://example.com/avatar.png")
    private String avatarUrl;

    @Schema(description = "Timezone", example = "Asia/Ho_Chi_Minh")
    private String timezone;

    @Schema(description = "Weekdays only", example = "true")
    private boolean weekdaysOnly;

    @Schema(description = "Email daily digest", example = "true")
    private boolean emailDailyDigest;
}
