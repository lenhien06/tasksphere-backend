package com.zone.tasksphere.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.dto.AIPredictionResponse;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.Worklog;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.repository.WorklogRepository;
import com.zone.tasksphere.service.AIPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIPredictionServiceImpl implements AIPredictionService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final WorklogRepository worklogRepository;

    @Value("${ai.prediction.python-api-url:http://127.0.0.1:8000}")
    private String pythonApiUrl;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    @Transactional(readOnly = true)
    public AIPredictionResponse getPerformancePrediction(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        List<Task> tasks = taskRepository.findByAssigneeIdAndDeletedAtIsNull(userId);
        List<Worklog> worklogs = worklogRepository.findByUserIdAndDeletedAtIsNullOrderByLogDateDesc(userId);

        long tasksCompleted = 0;
        long lateCount = 0;

        for (Task t : tasks) {
            if (TaskStatus.DONE.equals(t.getTaskStatus())) {
                tasksCompleted++;
                if (t.getCompletedAt() != null && t.getDueDate() != null) {
                    LocalDate completedDate = t.getCompletedAt().atZone(ZoneId.systemDefault()).toLocalDate();
                    if (completedDate.isAfter(t.getDueDate())) {
                        lateCount++;
                    }
                }
            }
        }

        double totalHoursLogged = worklogs.stream()
                .mapToLong(Worklog::getTimeSpentSeconds)
                .sum() / 3600.0;

        // Build JSON body manually using US locale to ensure dot as decimal separator
        String requestBody = String.format(java.util.Locale.US,
                "{\"employee_id\":%d,\"hours_worked\":%.4f,\"tasks_completed\":%d,\"late_count\":%d}",
                1, totalHoursLogged, tasksCompleted, lateCount
        );

        log.info("Calling Python AI API: {} | body: {}", pythonApiUrl + "/api/predict", requestBody);

        try {
            byte[] bodyBytes = requestBody.getBytes(StandardCharsets.UTF_8);

            // Use HttpURLConnection - most reliable Java HTTP client
            URL url = new URL(pythonApiUrl + "/api/predict");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Length", String.valueOf(bodyBytes.length));
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            // Write body
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
                os.flush();
            }

            int statusCode = conn.getResponseCode();
            log.info("Python AI API HTTP status: {}", statusCode);

            // Read response
            InputStream responseStream = (statusCode == 200)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            String responseBody = new String(responseStream.readAllBytes(), StandardCharsets.UTF_8);
            log.info("Python AI API response body: {}", responseBody);
            conn.disconnect();

            if (statusCode == 200) {
                JsonNode node = MAPPER.readTree(responseBody);
                double score = node.path("predicted_performance_score").asDouble(0.0);
                String trend = node.path("trend").asText("Unknown");

                return AIPredictionResponse.builder()
                        .employeeId(user.getId().toString())
                        .predictedPerformanceScore(score)
                        .trend(trend)
                        .build();
            } else {
                log.error("Python AI API error {}: {}", statusCode, responseBody);
                return AIPredictionResponse.builder()
                        .employeeId(user.getId().toString())
                        .predictedPerformanceScore(0.0)
                        .trend("Error")
                        .errorMessage("Python API returned HTTP " + statusCode + ": " + responseBody)
                        .build();
            }

        } catch (Exception e) {
            log.error("Error calling Python AI API: {}", e.getMessage(), e);
            return AIPredictionResponse.builder()
                    .employeeId(user.getId().toString())
                    .predictedPerformanceScore(0.0)
                    .trend("Error")
                    .errorMessage("Error calling Python API: " + e.getMessage())
                    .build();
        }
    }
}
