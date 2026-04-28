package com.zone.tasksphere.dto.request;

import lombok.Data;

@Data
public class SlackSendRequest {
    private String webhookUrl;
    private String message;
    private String developerName;
}
