package com.zone.tasksphere.dto.request;

import lombok.Data;

@Data
public class BurnoutAiRequest {
    private String developerName;
    private int startDay;
    private int endDay;
    private double startLeadTime;
    private double endLeadTime;
    private String startDate;
    private String endDate;
    private String coffeeTime;
}
