package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.AssignVersionRequest;
import com.zone.tasksphere.dto.request.CreateVersionRequest;
import com.zone.tasksphere.dto.request.UpdateVersionRequest;
import com.zone.tasksphere.dto.response.DeleteVersionResponse;
import com.zone.tasksphere.dto.response.TaskResponse;
import com.zone.tasksphere.dto.response.VersionResponse;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.*;
import com.zone.tasksphere.exception.*;
import com.zone.tasksphere.mapper.TaskMapper;
import com.zone.tasksphere.repository.*;
import com.zone.tasksphere.service.ActivityLogService;
import com.zone.tasksphere.service.VersionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class VersionServiceImpl implements VersionService {

    private final ProjectVersionRepository versionRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final ActivityLogService activityLogService;
    private final TaskMapper taskMapper;

    @Override
    public VersionResponse createVersion(UUID projectId, CreateVersionRequest request, UUID currentUserId) {
        requirePM(projectId, currentUserId);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Dự án không tồn tại"));

        if (versionRepository.existsByProject_IdAndNameAndDeletedAtIsNull(projectId, request.getName())) {
            throw new ConflictException("Tên version đã tồn tại trong dự án");
        }

        ProjectVersion version = ProjectVersion.builder()
                .project(project)
                .name(request.getName())
                .description(request.getDescription())
                .releaseDate(request.getReleaseDate())
                .status(VersionStatus.PLANNING)
                .build();

        version = versionRepository.save(version);

        logActivity(projectId, currentUserId, EntityType.PROJECT, version.getId(),
                ActionType.CREATED, null, version.getName());

        return toResponse(version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VersionResponse> getVersionsByProject(UUID projectId, UUID currentUserId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUserId)) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }

        return versionRepository.findByProject_IdAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Override
    public VersionResponse updateVersion(UUID versionId, UpdateVersionRequest request, UUID currentUserId) {
        ProjectVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Version không tồn tại"));

        requirePM(version.getProject().getId(), currentUserId);

        UUID projectId = version.getProject().getId();

        if (request.getName() != null && !request.getName().equals(version.getName())) {
            if (versionRepository.existsByProject_IdAndNameAndIdNotAndDeletedAtIsNull(
                    projectId, request.getName(), versionId)) {
                throw new ConflictException("Tên version đã tồn tại trong dự án");
            }
            version.setName(request.getName());
        }

        if (request.getDescription() != null) {
            version.setDescription(request.getDescription());
        }

        if (request.getStatus() != null) {
            // Validate: RELEASED không thể về PLANNING/IN_PROGRESS
            if (version.getStatus() == VersionStatus.RELEASED
                    && request.getStatus() != VersionStatus.RELEASED) {
                throw new BusinessRuleException("Không thể hạ cấp trạng thái của version đã release");
            }
            version.setStatus(request.getStatus());
        }

        if (request.getReleaseDate() != null) {
            version.setReleaseDate(request.getReleaseDate());
        }

        version = versionRepository.save(version);

        logActivity(projectId, currentUserId, EntityType.PROJECT, version.getId(),
                ActionType.UPDATED, null, version.getName());

        return toResponse(version);
    }

    @Override
    public DeleteVersionResponse deleteVersion(UUID versionId, UUID currentUserId) {
        ProjectVersion version = versionRepository.findById(versionId)
                .orElseThrow(() -> new NotFoundException("Version không tồn tại"));

        requirePM(version.getProject().getId(), currentUserId);

        if (version.getStatus() == VersionStatus.RELEASED) {
            throw new BusinessRuleException("Không thể xóa version đã release");
        }

        UUID projectId = version.getProject().getId();

        // SET version_id = NULL cho tất cả task trong version
        long tasksMoved = clearVersionFromTasks(versionId, projectId);

        version.setDeletedAt(Instant.now());
        versionRepository.save(version);

        logActivity(projectId, currentUserId, EntityType.PROJECT, version.getId(),
                ActionType.DELETED, version.getName(), null);

        return DeleteVersionResponse.builder()
                .message("Đã xóa version " + version.getName())
                .tasksMoved(tasksMoved)
                .build();
    }

    @Override
    public TaskResponse assignTaskVersion(UUID taskId, AssignVersionRequest request, UUID currentUserId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task không tồn tại"));

        requirePM(task.getProject().getId(), currentUserId);

        if (request.getVersionId() == null) {
            task.setProjectVersion(null);
        } else {
            ProjectVersion version = versionRepository.findByIdAndProject_IdAndDeletedAtIsNull(
                    request.getVersionId(), task.getProject().getId())
                    .orElseThrow(() -> new NotFoundException("Version không tồn tại hoặc không thuộc dự án này"));

            // TSK_007: Không thể gán task vào version đã release
            if (version.getStatus() == VersionStatus.RELEASED) {
                throw new BusinessRuleException("Không thể gán task vào version đã release");
            }

            task.setProjectVersion(version);
        }

        task = taskRepository.save(task);
        return taskMapper.toResponse(task);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private VersionResponse toResponse(ProjectVersion version) {
        long taskCount = versionRepository.countTasksByVersionId(version.getId());
        long doneCount = versionRepository.countDoneTasksByVersionId(version.getId());
        double completionRate = taskCount > 0
                ? Math.round(doneCount * 1000.0 / taskCount) / 10.0
                : 0.0;

        return VersionResponse.builder()
                .id(version.getId())
                .name(version.getName())
                .description(version.getDescription())
                .status(version.getStatus())
                .releaseDate(version.getReleaseDate())
                .taskCount(taskCount)
                .doneCount(doneCount)
                .completionRate(completionRate)
                .createdAt(version.getCreatedAt())
                .build();
    }

    private void requirePM(UUID projectId, UUID userId) {
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new Forbidden("Bạn không phải thành viên dự án này"));
        if (member.getProjectRole() != ProjectRole.PROJECT_MANAGER) {
            throw new Forbidden("Chỉ Project Manager mới có quyền thực hiện thao tác này");
        }
    }

    /**
     * SET version_id = NULL cho task trong version đã xóa.
     * Dùng JPQL update trực tiếp để tránh load N task vào memory.
     */
    private long clearVersionFromTasks(UUID versionId, UUID projectId) {
        // Đếm trước
        long count = versionRepository.countTasksByVersionId(versionId);
        // Dùng native query thông qua taskRepository để update
        // Ta sẽ dùng batch query chuyên biệt
        taskRepository.clearVersionFromTasks(versionId, projectId);
        return count;
    }

    private void logActivity(UUID projectId, UUID actorId, EntityType entityType,
                              UUID entityId, ActionType action, String oldVal, String newVal) {
        try {
            HttpServletRequest httpRequest = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getRequest();
            activityLogService.logActivity(projectId, actorId, entityType, entityId,
                    action, oldVal, newVal, httpRequest);
        } catch (Exception e) {
            log.warn("Failed to log activity for version {}: {}", entityId, e.getMessage());
        }
    }
}
