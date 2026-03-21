package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.CreateWorklogRequest;
import com.zone.tasksphere.dto.request.UpdateWorklogRequest;
import com.zone.tasksphere.dto.response.WorklogResponse;
import com.zone.tasksphere.dto.response.WorklogSummary;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.Worklog;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.repository.WorklogRepository;
import com.zone.tasksphere.service.WorklogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class WorklogServiceImpl implements WorklogService {

    private final WorklogRepository worklogRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Override
    public WorklogResponse logWork(UUID taskId, CreateWorklogRequest request, UUID currentUserId) {
        Task task = getTask(taskId);
        validateMember(task.getProject().getId(), currentUserId);
        User user = getUser(currentUserId);

        Worklog worklog = Worklog.builder()
            .task(task)
            .user(user)
            .timeSpentSeconds(request.getTimeSpent())
            .logDate(request.getLogDate())
            .description(request.getNote())
            .build();

        Worklog saved = worklogRepository.save(worklog);
        recalculateActualHours(task);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WorklogSummary getWorklogs(UUID taskId, UUID currentUserId) {
        Task task = getTask(taskId);
        validateMembership(task.getProject().getId(), currentUserId);

        List<Worklog> logs = worklogRepository
            .findByTaskIdAndDeletedAtIsNullOrderByLogDateDescCreatedAtDesc(taskId);

        long totalSeconds = worklogRepository.sumTimeSpentByTaskId(taskId);

        return WorklogSummary.builder()
            .totalSeconds((int) totalSeconds)
            .totalFormatted(formatDuration((int) totalSeconds))
            .logs(logs.stream().map(this::toResponse).toList())
            .build();
    }

    @Override
    public WorklogResponse updateWorklog(UUID worklogId, UpdateWorklogRequest request, UUID currentUserId) {
        Worklog worklog = getWorklog(worklogId);
        User currentUser = getUser(currentUserId);

        boolean isOwner = worklog.getUser().getId().equals(currentUserId);
        boolean isPM = isPM(worklog.getTask().getProject().getId(), currentUserId);
        boolean isAdmin = currentUser.getSystemRole() == SystemRole.ADMIN;

        if (!isOwner && !isPM && !isAdmin) {
            throw new Forbidden("WRK_003: Chỉ người tạo, PM hoặc Admin mới được sửa worklog");
        }

        worklog.setTimeSpentSeconds(request.getTimeSpent());
        worklog.setLogDate(request.getLogDate());
        worklog.setDescription(request.getNote());

        Worklog saved = worklogRepository.save(worklog);
        recalculateActualHours(worklog.getTask());
        return toResponse(saved);
    }

    @Override
    public void deleteWorklog(UUID worklogId, UUID currentUserId) {
        Worklog worklog = getWorklog(worklogId);
        User currentUser = getUser(currentUserId);

        boolean isOwner = worklog.getUser().getId().equals(currentUserId);
        boolean isPM = isPM(worklog.getTask().getProject().getId(), currentUserId);
        boolean isAdmin = currentUser.getSystemRole() == SystemRole.ADMIN;

        if (!isOwner && !isPM && !isAdmin) {
            throw new Forbidden("WRK_003: Chỉ người tạo, PM hoặc Admin mới được xóa worklog");
        }

        worklog.setDeletedAt(Instant.now());
        worklogRepository.save(worklog);
        recalculateActualHours(worklog.getTask());
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Task getTask(UUID taskId) {
        return taskRepository.findById(taskId)
            .orElseThrow(() -> new NotFoundException("Task not found: " + taskId));
    }

    private Worklog getWorklog(UUID worklogId) {
        return worklogRepository.findById(worklogId)
            .filter(w -> w.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Worklog not found: " + worklogId));
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    private void validateMembership(UUID projectId, UUID userId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }
    }

    private void validateMember(UUID projectId, UUID userId) {
        var member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(() -> new Forbidden("Bạn không phải thành viên dự án này"));
        if (member.getProjectRole() == ProjectRole.VIEWER) {
            throw new Forbidden("VIEWER không có quyền log thời gian");
        }
    }

    private boolean isPM(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            .map(m -> m.getProjectRole() == ProjectRole.PROJECT_MANAGER)
            .orElse(false);
    }

    private void recalculateActualHours(Task task) {
        long totalSeconds = worklogRepository.sumTimeSpentByTaskId(task.getId());
        BigDecimal hours = BigDecimal.valueOf(totalSeconds)
            .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
        task.setActualHours(hours);
        taskRepository.save(task);
    }

    static String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        if (hours > 0 && minutes > 0) return hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h";
        return minutes + "m";
    }

    private WorklogResponse toResponse(Worklog w) {
        User u = w.getUser();
        return WorklogResponse.builder()
            .id(w.getId())
            .user(WorklogResponse.UserSummary.builder()
                .id(u.getId())
                .fullName(u.getFullName())
                .avatarUrl(u.getAvatarUrl())
                .build())
            .timeSpent(w.getTimeSpentSeconds())
            .timeSpentFormatted(formatDuration(w.getTimeSpentSeconds()))
            .logDate(w.getLogDate())
            .note(w.getDescription())
            .createdAt(w.getCreatedAt())
            .build();
    }
}
