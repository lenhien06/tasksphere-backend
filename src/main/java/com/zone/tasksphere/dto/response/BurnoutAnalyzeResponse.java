package com.zone.tasksphere.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BurnoutAnalyzeResponse {
    private List<BurnoutDataPoint> data;
    private String developerName;
    private int startIndex;
    private int endIndex;
    private int length;
    private double startLeadTime;
    private double endLeadTime;
    private String startDate;
    private String endDate;
    private int startDay;
    private int endDay;
}
