package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.response.ActivityLogResponse;
import com.zone.tasksphere.dto.response.PageResponse;
import com.zone.tasksphere.entity.ActivityLog;
import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import com.zone.tasksphere.event.ActivityLogEvent;
import com.zone.tasksphere.repository.ActivityLogRepository;
import com.zone.tasksphere.service.ActivityLogService;
import com.zone.tasksphere.specification.ActivityLogSpecification;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ActivityLogServiceImpl implements ActivityLogService {

    private final ActivityLogRepository activityLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void logActivity(UUID projectId, UUID actorId, EntityType type, UUID entityId, 
                             ActionType action, String oldVal, String newVal, HttpServletRequest request) {
        
        // Trích xuất IP và User Agent từ request
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = request.getRemoteAddr();
        }
        String userAgent = request.getHeader("User-Agent");

        // Bắn event để xử lý bất đồng bộ
        ActivityLogEvent event = ActivityLogEvent.builder()
                .projectId(projectId)
                .actorId(actorId)
                .entityType(type)
                .entityId(entityId)
                .action(action)
                .oldValues(oldVal)
                .newValues(newVal)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();
        
        eventPublisher.publishEvent(event);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> getProjectActivities(
            UUID projectId, UUID actorId, EntityType type, ActionType action,
            Instant from, Instant to, Pageable pageable) {

        Specification<ActivityLog> spec = ActivityLogSpecification.hasProjectId(projectId)
                .and(ActivityLogSpecification.hasActorId(actorId))
                .and(ActivityLogSpecification.hasEntityType(type))
                .and(ActivityLogSpecification.hasAction(action))
                .and(ActivityLogSpecification.isBetween(from, to));

        Page<ActivityLog> logPage = activityLogRepository.findAll(spec, pageable);

        return PageResponse.fromPage(logPage.map(this::mapToResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ActivityLogResponse> getTaskActivities(UUID projectId, UUID taskId, Pageable pageable) {
        // Native query already has ORDER BY created_at DESC; avoid appending pageable sort
        Pageable safePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<ActivityLog> logPage = activityLogRepository.findTaskActivities(projectId.toString(), taskId.toString(), safePageable);
        return PageResponse.fromPage(logPage.map(this::mapToResponse));
    }

    private ActivityLogResponse mapToResponse(ActivityLog log) {
        return ActivityLogResponse.builder()
                .id(log.getId())
                .projectId(log.getProjectId())
                .actorId(log.getActor() != null ? log.getActor().getId() : null)
                .actorName(log.getActor() != null ? log.getActor().getFullName() : "System")
                .actorAvatar(log.getActor() != null ? log.getActor().getAvatarUrl() : null)
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .action(log.getAction())
                .oldValues(log.getOldValues())
                .newValues(log.getNewValues())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
