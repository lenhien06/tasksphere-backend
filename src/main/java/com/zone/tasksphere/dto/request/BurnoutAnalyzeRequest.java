package com.zone.tasksphere.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class BurnoutAnalyzeRequest {
    private String developerName;
    private List<Double> leadTimes;
    private List<String> dates;
}
