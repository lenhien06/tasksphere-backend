package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.AIPredictionRequest;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate;

    @Value("${ai.prediction.python-api-url:http://127.0.0.1:8000}")
    private String pythonApiUrl;

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

        AIPredictionRequest request = AIPredictionRequest.builder()
                .employeeId(1)
                .hoursWorked(totalHoursLogged)
                .tasksCompleted(tasksCompleted)
                .lateCount(lateCount)
                .build();

        log.info("Calling Python API at {} with data: {}", pythonApiUrl, request);

        try {
            // Let RestTemplate + MappingJackson2HttpMessageConverter serialize the object
            // @JsonProperty annotations on AIPredictionRequest handle snake_case field names
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<AIPredictionRequest> entity = new HttpEntity<>(request, headers);

            log.info("Sending request body: employeeId={}, hoursWorked={}, tasksCompleted={}, lateCount={}",
                    request.getEmployeeId(), request.getHoursWorked(), request.getTasksCompleted(), request.getLateCount());

            AIPredictionResponse response = restTemplate.postForObject(
                    pythonApiUrl + "/api/predict", entity, AIPredictionResponse.class);
            if (response != null) {
                response.setEmployeeId(user.getId().toString());
                return response;
            }
        } catch (Exception e) {
            log.error("Error calling Python API: ", e);
            return AIPredictionResponse.builder()
                    .employeeId(user.getId().toString())
                    .predictedPerformanceScore(0.0)
                    .trend("Error")
                    .errorMessage("Error calling Python API: " + e.getMessage()
                            + " (Cause: " + (e.getCause() != null ? e.getCause().getMessage() : "none") + ")")
                    .build();
        }

        return AIPredictionResponse.builder()
                .employeeId(user.getId().toString())
                .predictedPerformanceScore(0.0)
                .trend("Error")
                .errorMessage("Python API returned null response")
                .build();
    }
}
