package com.zone.tasksphere.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BurnoutDataPoint {
    private int day;
    private String date;
    private double avgLeadTimeHours;
}
