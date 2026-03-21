package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Map;

@Data
@Schema(description = "Request update notification preferences")
public class NotifPrefsRequest {
    @Schema(description = "Enable email daily digest", example = "true")
    private boolean emailDailyDigest;

    @Schema(description = "Receive notifications on weekdays only", example = "true")
    private boolean weekdaysOnly;

    @Schema(description = "Timezone", example = "Asia/Ho_Chi_Minh")
    private String timezone;

    @Schema(description = "Preferences for each notification type")
    private Map<String, Boolean> typePreferences;
}
