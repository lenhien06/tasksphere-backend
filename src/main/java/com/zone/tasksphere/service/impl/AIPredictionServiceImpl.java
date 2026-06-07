package com.zone.tasksphere.service.impl;

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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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

    // Shared HttpClient instance - thread-safe, reusable
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

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

        // Build JSON body manually to ensure correct format - no serialization magic
        String requestBody = String.format(
                "{\"employee_id\":%d,\"hours_worked\":%.4f,\"tasks_completed\":%d,\"late_count\":%d}",
                1, totalHoursLogged, tasksCompleted, lateCount
        );

        log.info("Calling Python AI API: {} | body: {}", pythonApiUrl + "/api/predict", requestBody);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pythonApiUrl + "/api/predict"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.info("Python AI API response: status={}, body={}", response.statusCode(), response.body());

            if (response.statusCode() == 200) {
                // Parse as JsonNode to handle type differences (e.g. Python int vs Java String)
                com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(response.body());
                double score = node.path("predicted_performance_score").asDouble(0.0);
                String trend = node.path("trend").asText("Unknown");

                return AIPredictionResponse.builder()
                        .employeeId(user.getId().toString())
                        .predictedPerformanceScore(score)
                        .trend(trend)
                        .build();
            } else {
                return AIPredictionResponse.builder()
                        .employeeId(user.getId().toString())
                        .predictedPerformanceScore(0.0)
                        .trend("Error")
                        .errorMessage("Python API returned HTTP " + response.statusCode() + ": " + response.body())
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
