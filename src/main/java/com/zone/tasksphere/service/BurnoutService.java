package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.BurnoutAiRequest;
import com.zone.tasksphere.dto.request.BurnoutAnalyzeRequest;
import com.zone.tasksphere.dto.response.BurnoutAiResponse;
import com.zone.tasksphere.dto.response.BurnoutAnalyzeResponse;
import com.zone.tasksphere.dto.response.BurnoutDataPoint;

import java.util.List;

public interface BurnoutService {
    List<BurnoutDataPoint> generateDemoData(String developerName);
    BurnoutAnalyzeResponse analyze(BurnoutAnalyzeRequest request);
    BurnoutAiResponse generateAiMessage(BurnoutAiRequest request);
}
