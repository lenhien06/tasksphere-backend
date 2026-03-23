package com.zone.tasksphere.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.dto.request.SetRecurrenceRequest;
import com.zone.tasksphere.dto.response.RecurrenceResponse;
import com.zone.tasksphere.dto.response.TaskSummaryResponse;
import com.zone.tasksphere.entity.RecurringTaskConfig;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.RecurrenceStatus;
import com.zone.tasksphere.entity.enums.RecurringFrequency;
import com.zone.tasksphere.exception.BusinessRuleException;
import com.zone.tasksphere.exception.ConflictException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.exception.StructuredApiException;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.RecurringTaskConfigRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.service.RecurringTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class RecurringTaskServiceImpl implements RecurringTaskService {

    /** Khi chỉ có endDate (không gửi maxOccurrences), BE dùng giới hạn nội bộ để tránh vòng lặp vô hạn. */
    private static final int MAX_OCCURRENCES_WHEN_END_DATE_ONLY = 10_000;

    private final TaskRepository taskRepository;
    private final RecurringTaskConfigRepository recurringConfigRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ObjectMapper objectMapper;

    // ════════════════════════════════════════
    // P6-BE-02-01: Set Recurrence
    // ════════════════════════════════════════

    @Override
    public RecurrenceResponse setRecurrence(UUID taskId, SetRecurrenceRequest request, UUID currentUserId) {
        Task task = findTaskOrThrow(taskId);
        checkWritePermission(task.getProject().getId(), currentUserId);

        if (task.getParentRecurringTaskId() != null) {
            throw new BusinessRuleException("TSK_011: Task này là instance của một recurring task, không thể thiết lập lịch lặp.");
        }
        if (recurringConfigRepository.existsByTaskId(taskId)) {
            throw new ConflictException("Task này đã có cấu hình lặp. Hãy dùng PUT để cập nhật.");
        }

        if (request.getFirstRunAt() == null) {
            throw new BusinessRuleException("firstRunAt là bắt buộc khi tạo cấu hình lặp.");
        }
        validateRequest(request);

        Instant nextRunAt = request.getFirstRunAt().toInstant(ZoneOffset.UTC);
        String frequencyConfig = buildFrequencyConfig(request);
        int maxOcc = resolveMaxOccurrencesForCreate(request);

        RecurringTaskConfig config = RecurringTaskConfig.builder()
                .task(task)
                .frequency(request.getFrequency())
                .startDate(request.getFirstRunAt().toLocalDate())
                .endDate(request.getEndDate())
                .maxOccurrences(maxOcc)
                .occurrenceCount(0)
                .nextRunAt(nextRunAt)
                .status(RecurrenceStatus.ACTIVE)
                .frequencyConfig(frequencyConfig)
                .build();

        task.setRecurring(true);
        taskRepository.save(task);

        RecurringTaskConfig saved = recurringConfigRepository.save(config);
        log.info("[RecurringTask] Created recurrence config {} for task {}", saved.getId(), taskId);
        return toResponse(saved);
    }

    // ════════════════════════════════════════
    // P6-BE-02-02: Get Recurrence
    // ════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public RecurrenceResponse getRecurrence(UUID taskId, UUID currentUserId) {
        Task task = findTaskOrThrow(taskId);
        checkReadPermission(task.getProject().getId(), currentUserId);

        RecurringTaskConfig config = recurringConfigRepository.findByTaskId(taskId).orElse(null);
        return config != null ? toResponse(config) : null;
    }

    // ════════════════════════════════════════
    // P6-BE-02-03: Update Recurrence
    // ════════════════════════════════════════

    @Override
    public RecurrenceResponse updateRecurrence(UUID taskId, SetRecurrenceRequest request, UUID currentUserId) {
        Task task = findTaskOrThrow(taskId);
        checkWritePermission(task.getProject().getId(), currentUserId);

        RecurringTaskConfig config = recurringConfigRepository.findByTaskId(taskId)
                .orElseThrow(() -> new NotFoundException("Task này chưa có cấu hình lặp."));

        if (config.getStatus() == RecurrenceStatus.CANCELLED) {
            throw new BusinessRuleException("Cấu hình lặp đã bị hủy, không thể cập nhật.");
        }

        validateRequest(request);

        String frequencyConfig = buildFrequencyConfig(request);
        int maxOcc = resolveMaxOccurrencesForUpdate(request, config);

        config.setFrequency(request.getFrequency());
        config.setEndDate(request.getEndDate());
        config.setMaxOccurrences(maxOcc);
        config.setFrequencyConfig(frequencyConfig);

        RecurringTaskConfig saved = recurringConfigRepository.save(config);
        log.info("[RecurringTask] Updated recurrence config {} for task {}", saved.getId(), taskId);
        return toResponse(saved);
    }

    // ════════════════════════════════════════
    // P6-BE-02-04: Delete Recurrence
    // ════════════════════════════════════════

    @Override
    public Map<String, Object> deleteRecurrence(UUID taskId, UUID currentUserId) {
        Task task = findTaskOrThrow(taskId);
        checkWritePermission(task.getProject().getId(), currentUserId);

        RecurringTaskConfig config = recurringConfigRepository.findByTaskId(taskId)
                .orElseThrow(() -> new NotFoundException("Task này chưa có cấu hình lặp."));

        // Cancel the config
        config.setStatus(RecurrenceStatus.CANCELLED);
        recurringConfigRepository.save(config);

        // Soft-delete future TODO instances not yet assigned to a sprint
        String templateId = taskId.toString();
        List<Task> futureInstances = taskRepository.findFutureInstancesByTemplateId(templateId);
        Instant now = Instant.now();
        futureInstances.forEach(t -> t.setDeletedAt(now));
        taskRepository.saveAll(futureInstances);

        // Mark template as no longer recurring
        task.setRecurring(false);
        taskRepository.save(task);

        log.info("[RecurringTask] Cancelled recurrence for task {}. Soft-deleted {} future instances.",
                taskId, futureInstances.size());

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Cấu hình lặp đã được hủy thành công.");
        result.put("cancelledInstanceCount", futureInstances.size());
        return result;
    }

    // ════════════════════════════════════════
    // P6-BE-02-05: Get Instances
    // ════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<TaskSummaryResponse> getInstances(UUID taskId, UUID currentUserId) {
        Task task = findTaskOrThrow(taskId);
        checkReadPermission(task.getProject().getId(), currentUserId);

        List<Task> instances = taskRepository.findInstancesByTemplateId(taskId.toString());
        return instances.stream()
                .map(t -> TaskSummaryResponse.builder()
                        .id(t.getId())
                        .taskCode(t.getTaskCode())
                        .title(t.getTitle())
                        .type(t.getType())
                        .priority(t.getPriority())
                        .taskStatus(t.getTaskStatus())
                        .dueDate(t.getDueDate())
                        .createdAt(t.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Task findTaskOrThrow(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy task với ID: " + taskId));
    }

    private void checkWritePermission(UUID projectId, UUID userId) {
        projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .filter(m -> m.getProjectRole() == ProjectRole.PROJECT_MANAGER || m.getProjectRole() == ProjectRole.MEMBER)
                .orElseThrow(() -> new Forbidden("Bạn không có quyền thực hiện thao tác này (chỉ PROJECT_MANAGER hoặc MEMBER)."));
    }

    private void checkReadPermission(UUID projectId, UUID userId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new Forbidden("Bạn không phải thành viên của dự án này.");
        }
    }

    /**
     * FE/contract: phải có ít nhất một điều kiện dừng — {@code endDate} hoặc {@code maxOccurrences}.
     */
    private void validateEndCondition(SetRecurrenceRequest request) {
        if (request.getEndDate() == null && request.getMaxOccurrences() == null) {
            throw new StructuredApiException(HttpStatus.BAD_REQUEST, "RECURRING_NO_END_CONDITION",
                    "Cần ít nhất một trong hai: endDate hoặc maxOccurrences để giới hạn lịch lặp.");
        }
    }

    private int resolveMaxOccurrencesForCreate(SetRecurrenceRequest request) {
        if (request.getMaxOccurrences() != null) {
            return request.getMaxOccurrences();
        }
        return MAX_OCCURRENCES_WHEN_END_DATE_ONLY;
    }

    private int resolveMaxOccurrencesForUpdate(SetRecurrenceRequest request, RecurringTaskConfig config) {
        if (request.getMaxOccurrences() != null) {
            return request.getMaxOccurrences();
        }
        return config.getMaxOccurrences();
    }

    private void validateRequest(SetRecurrenceRequest request) {
        validateEndCondition(request);
        if (request.getMaxOccurrences() != null && request.getMaxOccurrences() > 100) {
            throw new BusinessRuleException("TSK_010: maxOccurrences tối đa 100");
        }
        if (request.getFirstRunAt() != null && !request.getFirstRunAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new BusinessRuleException("firstRunAt phải là thời điểm trong tương lai.");
        }
        if (request.getFrequency() == RecurringFrequency.WEEKLY) {
            if (request.getDaysOfWeek() == null || request.getDaysOfWeek().isEmpty()) {
                throw new BusinessRuleException("WEEKLY yêu cầu daysOfWeek không được rỗng.");
            }
            for (int day : request.getDaysOfWeek()) {
                if (day < 1 || day > 7) {
                    throw new BusinessRuleException("daysOfWeek chỉ nhận giá trị từ 1 (Thứ 2) đến 7 (Chủ nhật).");
                }
            }
        }
        if (request.getFrequency() == RecurringFrequency.MONTHLY) {
            if (request.getDayOfMonth() == null || request.getDayOfMonth() < 1 || request.getDayOfMonth() > 31) {
                throw new BusinessRuleException("MONTHLY yêu cầu dayOfMonth từ 1 đến 31.");
            }
        }
        if (request.getFrequency() == RecurringFrequency.CUSTOM) {
            if (request.getCronExpression() == null || request.getCronExpression().isBlank()) {
                throw new BusinessRuleException("CUSTOM yêu cầu cronExpression không được rỗng.");
            }
        }
    }

    private String buildFrequencyConfig(SetRecurrenceRequest request) {
        try {
            Map<String, Object> config = new HashMap<>();
            switch (request.getFrequency()) {
                case WEEKLY -> config.put("daysOfWeek", request.getDaysOfWeek());
                case MONTHLY -> config.put("dayOfMonth", request.getDayOfMonth());
                case CUSTOM -> config.put("cronExpression", request.getCronExpression());
                default -> { return null; }
            }
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            log.warn("[RecurringTask] Failed to serialize frequency config: {}", e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFrequencyConfig(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[RecurringTask] Failed to parse frequency config JSON: {}", e.getMessage());
            return null;
        }
    }

    private RecurrenceResponse toResponse(RecurringTaskConfig config) {
        int remaining = config.getMaxOccurrences() - config.getOccurrenceCount();
        LocalDateTime nextRunAtLdt = config.getNextRunAt() != null
                ? LocalDateTime.ofInstant(config.getNextRunAt(), ZoneOffset.UTC)
                : null;

        return RecurrenceResponse.builder()
                .id(config.getId())
                .taskId(config.getTask().getId())
                .frequency(config.getFrequency())
                .frequencyConfig(parseFrequencyConfig(config.getFrequencyConfig()))
                .endDate(config.getEndDate())
                .maxOccurrences(config.getMaxOccurrences())
                .occurrenceCount(config.getOccurrenceCount())
                .nextRunAt(nextRunAtLdt)
                .status(config.getStatus())
                .remainingOccurrences(Math.max(0, remaining))
                .build();
    }
}
