package com.zone.tasksphere.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SlackSendResponse {
    private boolean success;
    private String detail;
}
