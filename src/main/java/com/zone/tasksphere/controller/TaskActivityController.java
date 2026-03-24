package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.ActivityLogResponse;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.PageResponse;
import com.zone.tasksphere.dto.response.TaskActivityItemResponse;
import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import com.zone.tasksphere.service.ActivityLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks")
@RequiredArgsConstructor
@Tag(name = "20. Activity Logs", description = "Task activity endpoint cho FE tab Hoạt động.")
@SecurityRequirement(name = "bearerAuth")
public class TaskActivityController {

    private final ActivityLogService activityLogService;

    @Operation(summary = "Lấy activity theo task (FE-friendly path)")
    @GetMapping("/{taskId}/activity")
    public ResponseEntity<ApiResponse<PageResponse<TaskActivityItemResponse>>> getTaskActivities(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ActivityLogResponse> activities = activityLogService.getTaskActivities(projectId, taskId, pageable);
        PageResponse<TaskActivityItemResponse> payload = PageResponse.<TaskActivityItemResponse>builder()
                .content(activities.getContent().stream().map(this::toTaskActivityItem).toList())
                .totalElements(activities.getTotalElements())
                .totalPages(activities.getTotalPages())
                .size(activities.getSize())
                .number(activities.getNumber())
                .first(activities.isFirst())
                .last(activities.isLast())
                .empty(activities.isEmpty())
                .build();
        return ResponseEntity.ok(ApiResponse.success(payload));
    }

    private TaskActivityItemResponse toTaskActivityItem(ActivityLogResponse log) {
        return TaskActivityItemResponse.builder()
            .id(log.getId())
            .action(toFeAction(log))
            .entityType(log.getEntityType() != null ? log.getEntityType().name() : null)
            .entityId(log.getEntityId())
            .actor(TaskActivityItemResponse.Actor.builder()
                .id(log.getActorId())
                .fullName(log.getActorName())
                .avatarUrl(log.getActorAvatar())
                .build())
            .oldValue(log.getOldValues())
            .newValue(log.getNewValues())
            .createdAt(log.getCreatedAt())
            .build();
    }

    private String toFeAction(ActivityLogResponse log) {
        if (log.getAction() == null) return null;
        return switch (log.getAction()) {
            case TASK_CREATED -> "TASK_CREATED";
            case STATUS_CHANGED -> "STATUS_CHANGED";
            case ASSIGNEE_CHANGED, ASSIGNED -> "ASSIGNEE_CHANGED";
            case PRIORITY_CHANGED -> "PRIORITY_CHANGED";
            case UPDATED -> "UPDATED";
            case COMMENT_ADDED -> "COMMENT_ADDED";
            case COMMENT_DELETED -> "COMMENT_DELETED";
            case ATTACHMENT_UPLOADED -> "ATTACHMENT_UPLOADED";
            case ATTACHMENT_DELETED -> "ATTACHMENT_DELETED";
            case SUBTASK_CREATED -> "SUBTASK_CREATED";
            case SUBTASK_DELETED -> "SUBTASK_DELETED";
            case SPRINT_CHANGED -> "SPRINT_CHANGED";
            case CREATED -> log.getEntityType() == EntityType.TASK ? "TASK_CREATED" : "UPDATED";
            default -> log.getAction().name();
        };
    }
}
