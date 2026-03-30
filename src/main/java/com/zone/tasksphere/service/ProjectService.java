package com.zone.tasksphere.service;

import com.zone.tasksphere.component.DefaultColumnSeeder;
import com.zone.tasksphere.dto.request.ProjectRequest;
import com.zone.tasksphere.dto.request.ProjectUpdateRequest;
import com.zone.tasksphere.dto.response.ColumnResponse;
import com.zone.tasksphere.dto.response.ProjectMemberResponse;
import com.zone.tasksphere.dto.response.ProjectResponse;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectMember;
import com.zone.tasksphere.entity.ProjectStatusColumn;
import com.zone.tasksphere.entity.ProjectView;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.*;
import com.zone.tasksphere.event.ActivityLogEvent;
import com.zone.tasksphere.exception.ConflictException;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.ProjectStatusColumnRepository;
import com.zone.tasksphere.repository.ProjectViewRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.specification.ProjectSpecification;
import com.zone.tasksphere.utils.AuthUtils;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectStatusColumnRepository columnRepository;
    private final ProjectViewRepository projectViewRepository;
    private final TaskRepository taskRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final DefaultColumnSeeder defaultColumnSeeder;
    private final com.zone.tasksphere.service.impl.MinioStorageService minioStorageService;
    private final EntityManager entityManager;

    public Page<ProjectResponse> getProjects(String search, ProjectStatus status,
                                             ProjectVisibility visibility, UUID userId,
                                             boolean isAdmin, Pageable pageable) {

        Specification<Project> spec = ProjectSpecification.filterProjects(search, status, visibility, userId, isAdmin);
        Page<Project> projectPage = projectRepository.findAll(spec, pageable);

        List<UUID> projectIds = projectPage.getContent().stream()
                .map(Project::getId)
                .collect(Collectors.toList());

        if (projectIds.isEmpty()) {
            return projectPage.map(this::toSummaryResponse);
        }

        // Fetch member counts in one query
        Map<UUID, Long> memberCountMap = projectMemberRepository.getProjectMemberCounts(projectIds).stream()
                .collect(Collectors.toMap(
                        ProjectMemberRepository.ProjectMemberCountProjection::getProjectId,
                        ProjectMemberRepository.ProjectMemberCountProjection::getMemberCount
                ));

        // Fetch task stats in one query
        Map<UUID, TaskRepository.ProjectTaskStatsProjection> taskStatsMap = taskRepository.getProjectTaskStats(projectIds).stream()
                .collect(Collectors.toMap(
                        TaskRepository.ProjectTaskStatsProjection::getProjectId,
                        s -> s
                ));

        // Fetch roles for the current user in one query (skip for guest)
        Map<UUID, String> roleMap = Map.of();
        if (userId != null) {
            roleMap = projectMemberRepository.findByUserIdAndProjectIdIn(userId, projectIds).stream()
                    .collect(Collectors.toMap(
                            pm -> pm.getProject().getId(),
                            pm -> pm.getProjectRole().name()
                    ));
        }
        final Map<UUID, String> roleMapFinal = roleMap;

        return projectPage.map(project -> {
            ProjectResponse response = toSummaryResponse(project);
            response.setOwner(userId != null
                    && project.getOwner() != null
                    && project.getOwner().getId().equals(userId));

            // Populate computed fields
            response.setMemberCount(memberCountMap.getOrDefault(project.getId(), 0L));

            TaskRepository.ProjectTaskStatsProjection stats = taskStatsMap.get(project.getId());
            if (stats != null) {
                long total = stats.getTotal() != null ? stats.getTotal() : 0L;
                long done = stats.getDone() != null ? stats.getDone() : 0L;
                long overdue = stats.getOverdue() != null ? stats.getOverdue() : 0L;

                response.setTaskStats(ProjectResponse.TaskStats.builder()
                        .total(total)
                        .done(done)
                        .overdue(overdue)
                        .build());

                if (total > 0) {
                    double progress = (double) done / total * 100;
                    response.setProgress(Math.round(progress * 10.0) / 10.0); // Round to 1 decimal place
                } else {
                    response.setProgress(0.0);
                }
            } else {
                response.setTaskStats(ProjectResponse.TaskStats.builder()
                        .total(0L)
                        .done(0L)
                        .overdue(0L)
                        .build());
                response.setProgress(0.0);
            }

            // Set myRole
            if (userId == null) {
                response.setMyRole("GUEST");
            } else if (project.getOwner().getId().equals(userId)) {
                response.setMyRole("PROJECT_MANAGER");
            } else {
                String role = roleMapFinal.get(project.getId());
                if (role != null) {
                    response.setMyRole(role);
                } else if (isAdmin) {
                    response.setMyRole("SYSTEM_ADMIN");
                } else {
                    response.setMyRole("VIEWER");
                }
            }

            return response;
        });
    }

    private ProjectResponse toSummaryResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .projectKey(project.getProjectKey())
                .description(project.getDescription())
                .status(project.getStatus())
                .visibility(project.getVisibility())
                .ownerId(project.getOwner().getId())
                .ownerName(project.getOwner().getFullName())
                .owner(false)
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    @Transactional
    public ProjectResponse createProject(ProjectRequest request, UUID ownerId) {
        String normalizedKey = request.getProjectKey() != null ? request.getProjectKey().trim() : null;
        if (normalizedKey == null || !normalizedKey.matches("^[A-Z0-9]{2,10}$")) {
            throw new com.zone.tasksphere.exception.BadRequestException(
                    "Project Key không hợp lệ. Chỉ cho phép 2-10 ký tự IN HOA và số.");
        }

        if (request.getStartDate() != null && request.getEndDate() != null
                && !request.getEndDate().isAfter(request.getStartDate())) {
            throw new com.zone.tasksphere.exception.BadRequestException("endDate phải sau startDate");
        }

        if (projectRepository.existsByProjectKey(normalizedKey)) {
            throw new ConflictException("Project Key đã được sử dụng");
        }

        if (projectRepository.existsByName(request.getName())) {
            throw new ConflictException("Tên dự án đã tồn tại");
        }

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found: " + ownerId));

        Project project = Project.builder()
                .name(request.getName())
                .projectKey(normalizedKey)
                .description(request.getDescription())
                .visibility(request.getVisibility() != null ? request.getVisibility() : ProjectVisibility.PRIVATE)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(ProjectStatus.ACTIVE)
                .taskCounter(0)
                .owner(owner)
                .build();

        List<ProjectStatusColumn> defaultColumns = new ArrayList<>();
        defaultColumns.add(ProjectStatusColumn.builder()
                .project(project).name("To Do").colorHex("#D9D9D9").sortOrder(1).isDefault(true).mappedStatus(TaskStatus.TODO).build());
        defaultColumns.add(ProjectStatusColumn.builder()
                .project(project).name("In Progress").colorHex("#1677FF").sortOrder(2).isDefault(false).mappedStatus(TaskStatus.IN_PROGRESS).build());
        defaultColumns.add(ProjectStatusColumn.builder()
                .project(project).name("In Review").colorHex("#FAAD14").sortOrder(3).isDefault(false).mappedStatus(TaskStatus.IN_REVIEW).build());
        defaultColumns.add(ProjectStatusColumn.builder()
                .project(project).name("Done").colorHex("#52C41A").sortOrder(4).isDefault(false).mappedStatus(TaskStatus.DONE).build());

        project.setStatusColumns(defaultColumns);
        Project savedProject = projectRepository.save(project);

        ProjectMember projectMember = ProjectMember.builder()
                .project(savedProject).user(owner).projectRole(ProjectRole.PROJECT_MANAGER).joinedAt(Instant.now()).build();
        ProjectMember savedMember = projectMemberRepository.save(projectMember);

        eventPublisher.publishEvent(ActivityLogEvent.builder()
                .projectId(savedProject.getId()).actorId(ownerId).entityType(EntityType.PROJECT)
                .entityId(savedProject.getId()).action(ActionType.CREATED).build());

        // Inverse `project.members` không được JPA hydrate sau save riêng → FE thấy members=[].
        ProjectResponse response = toResponse(savedProject, ownerId, false);
        response.setMembers(List.of(toProjectMemberResponse(savedMember, owner)));
        return response;
    }

    private static ProjectMemberResponse toProjectMemberResponse(ProjectMember m, User u) {
        return ProjectMemberResponse.builder()
                .id(m.getId())
                .projectRole(m.getProjectRole())
                .joinedAt(m.getJoinedAt())
                .user(ProjectMemberResponse.UserInfo.builder()
                        .id(u.getId())
                        .fullName(u.getFullName())
                        .email(u.getEmail())
                        .avatarUrl(u.getAvatarUrl())
                        .build())
                .build();
    }

    public ProjectResponse getProjectById(UUID id, UUID userId, boolean isAdmin) {
        Project project = projectRepository.findById(id).orElse(null);
        if (project == null && isAdmin) {
            project = projectRepository.findByIdWithDeleted(id)
                    .orElseThrow(() -> new NotFoundException("Project not found: " + id));
        } else if (project == null) {
            throw new NotFoundException("Project not found: " + id);
        }
        checkVisibility(project, userId, isAdmin);
        recordProjectView(project, userId, isAdmin);
        return toResponse(project, userId, isAdmin);
    }

    public ProjectResponse getProjectByKey(String key, UUID userId, boolean isAdmin) {
        Project project = projectRepository.findByProjectKey(key).orElse(null);
        if (project == null && isAdmin) {
            project = projectRepository.findByKeyWithDeleted(key)
                    .orElseThrow(() -> new NotFoundException("Project not found with key: " + key));
        } else if (project == null) {
            throw new NotFoundException("Project not found with key: " + key);
        }
        checkVisibility(project, userId, isAdmin);
        recordProjectView(project, userId, isAdmin);
        return toResponse(project, userId, isAdmin);
    }

    @Transactional
    protected void recordProjectView(Project project, UUID userId, boolean isAdmin) {
        if (userId == null || isAdmin || project.getDeletedAt() != null) {
            return;
        }

        if (project.getVisibility() != ProjectVisibility.PUBLIC) {
            return;
        }

        boolean isOwner = project.getOwner() != null && project.getOwner().getId().equals(userId);
        boolean isMember = projectMemberRepository.existsByProjectIdAndUserId(project.getId(), userId);
        if (isOwner || isMember) {
            return;
        }

        Instant now = Instant.now();
        ProjectView projectView = projectViewRepository.findByProjectIdAndUserId(project.getId(), userId)
                .orElseGet(() -> ProjectView.builder()
                        .project(project)
                        .user(userRepository.getReferenceById(userId))
                        .lastViewedAt(now)
                        .build());

        projectView.setLastViewedAt(now);
        projectViewRepository.save(projectView);
    }

    private void checkVisibility(Project project, UUID userId, boolean isAdmin) {
        if (isAdmin) return;
        if (project.getDeletedAt() != null) {
            throw new NotFoundException("Project not found or archived");
        }
        ProjectVisibility visibility = project.getVisibility();
        if (visibility == ProjectVisibility.PUBLIC) return;
        if (userId == null) {
            throw new com.zone.tasksphere.exception.SignInRequiredException("Sign in required to view this project");
        }
        boolean isMember = projectMemberRepository.existsByProjectIdAndUserId(project.getId(), userId);
        if (!isMember && !project.getOwner().getId().equals(userId)) {
            throw new com.zone.tasksphere.exception.Forbidden("You do not have permission to view this project");
        }
    }

    @Transactional
    public ProjectResponse updateProject(UUID id, ProjectUpdateRequest request, UUID actorId, boolean isAdmin) {
        Project project = projectRepository.findById(id).orElseThrow(() -> new NotFoundException("Project not found: " + id));
        ProjectRole actorRole = getProjectRole(project, actorId, isAdmin);
        if (actorRole != ProjectRole.PROJECT_MANAGER && !isAdmin) {
            throw new com.zone.tasksphere.exception.Forbidden("Only Project Manager or Admin can update project settings");
        }
        if (!project.getName().equals(request.getName()) && projectRepository.existsByName(request.getName())) {
            throw new ConflictException("Project name already exists: " + request.getName());
        }
        String oldVal = String.format("name=%s, visibility=%s, description=%s", project.getName(), project.getVisibility(), project.getDescription());
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setVisibility(request.getVisibility());
        project.setStartDate(request.getStartDate());
        project.setEndDate(request.getEndDate());
        Project savedProject = projectRepository.saveAndFlush(project);
        String newVal = String.format("name=%s, visibility=%s, description=%s", savedProject.getName(), savedProject.getVisibility(), savedProject.getDescription());
        eventPublisher.publishEvent(ActivityLogEvent.builder()
                .projectId(savedProject.getId()).actorId(actorId).entityType(EntityType.PROJECT)
                .entityId(savedProject.getId()).action(ActionType.UPDATED).oldValues(oldVal).newValues(newVal).build());
        return toResponse(savedProject, actorId, isAdmin);
    }

    @Transactional
    public void archiveProject(UUID id, String confirmName, UUID actorId, boolean isAdmin) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));

        // BR-11: Chỉ Owner hoặc Admin mới được archive
        boolean isOwner = project.getOwner() != null && project.getOwner().getId().equals(actorId);
        if (!isOwner && !isAdmin) {
            throw new com.zone.tasksphere.exception.Forbidden("Chỉ Owner hoặc Admin mới được archive dự án");
        }

        // FR-13: confirmName phải khớp chính xác tên dự án
        if (confirmName == null || confirmName.trim().isEmpty()) {
            throw new com.zone.tasksphere.exception.BadRequestException("Tên xác nhận không được để trống");
        }

        String trimmedConfirmName = confirmName.trim();
        if (!project.getName().equals(trimmedConfirmName)) {
            throw new com.zone.tasksphere.exception.BadRequestException(
                    "Tên xác nhận không khớp với tên dự án"
            );
        }

        // Nếu đã archive trước đó thì không thao tác lại
        if (project.getDeletedAt() != null || project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new com.zone.tasksphere.exception.BadRequestException(
                    "Dự án đã được archive trước đó"
            );
        }

        // FR-13 + BR-11: Soft delete + status ARCHIVED
        project.setDeletedAt(Instant.now());
        project.setStatus(ProjectStatus.ARCHIVED);
        projectRepository.save(project);

        // Ghi lại hoạt động
        eventPublisher.publishEvent(ActivityLogEvent.builder()
                .projectId(project.getId())
                .actorId(actorId)
                .entityType(EntityType.PROJECT)
                .entityId(project.getId())
                .action(ActionType.DELETED)
                .build());

        // Thông báo cho tất cả thành viên + email async
        User actor = userRepository.findById(actorId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Admin";
        String projectName = project.getName();
        String notifTitle = "Dự án đã được archive";
        String notifBody = "Dự án \"" + projectName + "\" đã được archive bởi " + actorName;

        List<ProjectMember> members = projectMemberRepository.findByProjectId(project.getId());
        members.forEach(member -> {
            if (member.getUser() != null) {
                emailService.sendProjectArchivedEmail(member.getUser().getEmail(), projectName, actorName);
                notificationService.createNotification(
                        member.getUser(),
                        NotificationType.SYSTEM_ANNOUNCEMENT,
                        notifTitle,
                        notifBody,
                        "PROJECT",
                        project.getId()
                );
            }
        });
    }

    @Transactional
    public void deleteProjectPermanently(UUID id, String confirmName, UUID actorId, boolean isAdmin) {
        Project project = projectRepository.findByIdWithDeleted(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));

        validatePermanentDeletePermission(project, confirmName, actorId, isAdmin);

        List<String> storageKeysToDelete = collectProjectStorageKeys(project.getId());

        // Delete task-dependent tables first
        executeDelete("""
                DELETE FROM comment_mentions
                WHERE comment_id IN (
                    SELECT c.id FROM comments c
                    JOIN tasks t ON t.id = c.task_id
                    WHERE t.project_id = :projectId
                )
                """, project.getId());
        executeDelete("""
                DELETE FROM webhook_delivery_logs
                WHERE webhook_id IN (
                    SELECT w.id FROM webhooks w WHERE w.project_id = :projectId
                )
                """, project.getId());
        executeDelete("""
                DELETE FROM sprint_task_snapshots
                WHERE sprint_id IN (
                    SELECT s.id FROM sprints s WHERE s.project_id = :projectId
                )
                   OR task_id IN (
                    SELECT t.id FROM tasks t WHERE t.project_id = :projectId
                )
                """, project.getId());
        executeDelete("""
                DELETE FROM recurring_task_configs
                WHERE task_id IN (
                    SELECT t.id FROM tasks t WHERE t.project_id = :projectId
                )
                """, project.getId());
        executeDelete("""
                DELETE FROM task_dependencies
                WHERE blocking_task_id IN (
                    SELECT t.id FROM tasks t WHERE t.project_id = :projectId
                )
                   OR blocked_task_id IN (
                    SELECT t.id FROM tasks t WHERE t.project_id = :projectId
                )
                """, project.getId());
        executeDelete("""
                DELETE FROM worklogs
                WHERE task_id IN (
                    SELECT t.id FROM tasks t WHERE t.project_id = :projectId
                )
                """, project.getId());
        executeDelete("""
                DELETE FROM custom_field_values
                WHERE task_id IN (
                    SELECT t.id FROM tasks t WHERE t.project_id = :projectId
                )
                   OR custom_field_id IN (
                    SELECT cf.id FROM custom_fields cf WHERE cf.project_id = :projectId
                )
                """, project.getId());
        executeDelete("""
                DELETE FROM checklist_items
                WHERE task_id IN (
                    SELECT t.id FROM tasks t WHERE t.project_id = :projectId
                )
                """, project.getId());
        executeDelete("""
                DELETE FROM attachments
                WHERE task_id IN (
                    SELECT t.id FROM tasks t WHERE t.project_id = :projectId
                )
                """, project.getId());
        executeDelete("""
                DELETE FROM upload_jobs
                WHERE task_id IN (
                    SELECT t.id FROM tasks t WHERE t.project_id = :projectId
                )
                """, project.getId());
        deleteProjectComments(project.getId());

        // Delete project-scoped tables
        executeDelete("DELETE FROM notifications WHERE project_id = :projectId", project.getId());
        executeDelete("DELETE FROM activity_logs WHERE project_id = :projectId", project.getId());
        executeDelete("DELETE FROM export_jobs WHERE project_id = :projectId", project.getId());
        executeDelete("DELETE FROM saved_filters WHERE project_id = :projectId", project.getId());
        executeDelete("DELETE FROM project_invites WHERE project_id = :projectId", project.getId());
        executeDelete("DELETE FROM project_views WHERE project_id = :projectId", project.getId());
        executeDelete("DELETE FROM project_members WHERE project_id = :projectId", project.getId());
        deleteProjectTasks(project.getId());
        executeDelete("DELETE FROM sprints WHERE project_id = :projectId", project.getId());
        executeDelete("DELETE FROM project_versions WHERE project_id = :projectId", project.getId());
        executeDelete("DELETE FROM custom_fields WHERE project_id = :projectId", project.getId());
        executeDelete("DELETE FROM project_status_columns WHERE project_id = :projectId", project.getId());
        executeDelete("DELETE FROM webhooks WHERE project_id = :projectId", project.getId());

        int deletedProjects = executeDelete("DELETE FROM projects WHERE id = :projectId", project.getId());
        if (deletedProjects == 0) {
            throw new NotFoundException("Project not found: " + project.getId());
        }

        scheduleStorageCleanup(storageKeysToDelete);
    }

    private void validatePermanentDeletePermission(Project project, String confirmName, UUID actorId, boolean isAdmin) {
        boolean isOwner = project.getOwner() != null && project.getOwner().getId().equals(actorId);
        if (!isOwner && !isAdmin) {
            throw new com.zone.tasksphere.exception.Forbidden("Chỉ Owner hoặc Admin mới được xóa vĩnh viễn dự án");
        }

        if (confirmName == null || confirmName.trim().isEmpty()) {
            throw new com.zone.tasksphere.exception.BadRequestException("Tên xác nhận không được để trống");
        }

        String trimmedConfirmName = confirmName.trim();
        boolean matchesName = project.getName().equalsIgnoreCase(trimmedConfirmName);
        boolean matchesKey = project.getProjectKey().equalsIgnoreCase(trimmedConfirmName);
        if (!matchesName && !matchesKey) {
            throw new com.zone.tasksphere.exception.BadRequestException(
                    "Tên hoặc project key xác nhận không khớp với dự án"
            );
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> collectProjectStorageKeys(UUID projectId) {
        List<String> attachmentKeys = entityManager.createNativeQuery("""
                SELECT a.s3_key
                FROM attachments a
                JOIN tasks t ON t.id = a.task_id
                WHERE t.project_id = :projectId
                  AND a.s3_key IS NOT NULL
                """)
                .setParameter("projectId", projectId)
                .getResultList();

        List<String> uploadJobKeys = entityManager.createNativeQuery("""
                SELECT uj.temp_storage_key
                FROM upload_jobs uj
                JOIN tasks t ON t.id = uj.task_id
                WHERE t.project_id = :projectId
                  AND uj.temp_storage_key IS NOT NULL
                """)
                .setParameter("projectId", projectId)
                .getResultList();

        List<String> exportKeys = entityManager.createNativeQuery("""
                SELECT ej.storage_key
                FROM export_jobs ej
                WHERE ej.project_id = :projectId
                  AND ej.storage_key IS NOT NULL
                """)
                .setParameter("projectId", projectId)
                .getResultList();

        List<String> allKeys = new ArrayList<>();
        allKeys.addAll(attachmentKeys);
        allKeys.addAll(uploadJobKeys);
        allKeys.addAll(exportKeys);
        return allKeys.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }

    private int executeDelete(String sql, UUID projectId) {
        return entityManager.createNativeQuery(sql)
                .setParameter("projectId", projectId)
                .executeUpdate();
    }

    private void deleteProjectComments(UUID projectId) {
        Integer maxDepth = findMaxCommentDepth(projectId);
        if (maxDepth == null) {
            return;
        }

        for (int depth = maxDepth; depth >= 0; depth--) {
            entityManager.createNativeQuery("""
                    DELETE FROM comments
                    WHERE depth = :depth
                      AND task_id IN (
                        SELECT t.id FROM tasks t WHERE t.project_id = :projectId
                    )
                    """)
                    .setParameter("depth", depth)
                    .setParameter("projectId", projectId)
                    .executeUpdate();
        }
    }

    private void deleteProjectTasks(UUID projectId) {
        Integer maxDepth = findMaxTaskDepth(projectId);
        if (maxDepth == null) {
            return;
        }

        for (int depth = maxDepth; depth >= 0; depth--) {
            entityManager.createNativeQuery("""
                    DELETE FROM tasks
                    WHERE project_id = :projectId
                      AND depth = :depth
                    """)
                    .setParameter("projectId", projectId)
                    .setParameter("depth", depth)
                    .executeUpdate();
        }
    }

    private Integer findMaxCommentDepth(UUID projectId) {
        Object result = entityManager.createNativeQuery("""
                SELECT MAX(c.depth)
                FROM comments c
                JOIN tasks t ON t.id = c.task_id
                WHERE t.project_id = :projectId
                """)
                .setParameter("projectId", projectId)
                .getSingleResult();
        return toInteger(result);
    }

    private Integer findMaxTaskDepth(UUID projectId) {
        Object result = entityManager.createNativeQuery("""
                SELECT MAX(t.depth)
                FROM tasks t
                WHERE t.project_id = :projectId
                """)
                .setParameter("projectId", projectId)
                .getSingleResult();
        return toInteger(result);
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }

    private void scheduleStorageCleanup(List<String> storageKeys) {
        if (storageKeys.isEmpty()) {
            return;
        }

        Runnable cleanup = () -> storageKeys.forEach(minioStorageService::deleteFile);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
            return;
        }

        cleanup.run();
    }

    @Transactional
    public ProjectResponse restoreProject(UUID projectId) {
        com.zone.tasksphere.dto.response.UserDetail currentUser = AuthUtils.getUserDetail();
        if (currentUser == null) {
            throw new com.zone.tasksphere.exception.SignInRequiredException("Sign in required");
        }

        UUID actorId = currentUser.getId();
        boolean isAdmin = SystemRole.ADMIN.equals(currentUser.getSystemRole());

        log.info("[restoreProject] Querying project by id (include deleted). projectId={}, actorId={}, isAdmin={}",
                projectId, actorId, isAdmin);
        Project project = projectRepository.findByIdWithDeleted(projectId)
                .orElseThrow(() -> new NotFoundException("Dự án không tồn tại"));
        log.info("[restoreProject] Project found. id={}, status={}, deletedAt={}, ownerId={}",
                project.getId(), project.getStatus(), project.getDeletedAt(),
                project.getOwner() != null ? project.getOwner().getId() : null);

        if (project.getDeletedAt() == null) {
            throw new com.zone.tasksphere.exception.BadRequestException("Dự án chưa bị archive");
        }

        boolean isOwner = project.getOwner() != null && project.getOwner().getId().equals(actorId);
        if (!isOwner && !isAdmin) {
            throw new com.zone.tasksphere.exception.Forbidden("Chỉ Owner hoặc Admin được khôi phục dự án");
        }

        project.setDeletedAt(null);
        project.setStatus(ProjectStatus.ACTIVE);
        Project restoredProject = projectRepository.save(project);

        String notifTitle = "Dự án đã được khôi phục";
        String notifBody = "Dự án \"" + restoredProject.getName() + "\" đã được khôi phục";
        List<ProjectMember> members = projectMemberRepository.findByProjectId(restoredProject.getId());
        members.forEach(member -> {
            if (member.getUser() != null) {
                notificationService.createNotification(
                        member.getUser(),
                        NotificationType.PROJECT_RESTORED,
                        notifTitle,
                        notifBody,
                        "PROJECT",
                        restoredProject.getId()
                );
            }
        });

        eventPublisher.publishEvent(ActivityLogEvent.builder()
                .projectId(restoredProject.getId())
                .actorId(actorId)
                .entityType(EntityType.PROJECT)
                .entityId(restoredProject.getId())
                .action(ActionType.PROJECT_RESTORED)
                .oldValues(ProjectStatus.ARCHIVED.name())
                .newValues(ProjectStatus.ACTIVE.name())
                .build());

        return toResponse(restoredProject, actorId, isAdmin);
    }

    /**
     * Tầng 4: Lấy danh sách columns của project.
     * Nếu chưa có column (edge case) → tự seed 4 cột mặc định rồi trả về.
     * FE phải gọi endpoint này TRƯỚC khi render Kanban Board.
     */
    @Transactional
    public List<ColumnResponse> getProjectColumns(UUID projectId, UUID userId, boolean isAdmin) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        checkVisibility(project, userId, isAdmin);

        List<ProjectStatusColumn> columns =
            columnRepository.findByProjectIdOrderBySortOrderAsc(projectId);

        // Safety: seed nếu project không có column
        if (columns.isEmpty()) {
            log.warn("[ProjectService] Project {} has no columns on GET /columns. Auto-seeding.",
                projectId);
            columns = defaultColumnSeeder.seedForProject(project);
        }

        return columns.stream().map(this::toColumnResponse).toList();
    }

    private ColumnResponse toColumnResponse(ProjectStatusColumn col) {
        return ColumnResponse.builder()
            .id(col.getId())
            .name(col.getName())
            .colorHex(col.getColorHex())
            .sortOrder(col.getSortOrder())
            .isDefault(col.isDefault())
            .mappedStatus(col.getMappedStatus())
            .taskCount(0)   // FE tự tính từ danh sách task nếu cần
            .build();
    }

    private ProjectRole getProjectRole(Project project, UUID userId, boolean isAdmin) {
        if (project.getOwner().getId().equals(userId)) return ProjectRole.PROJECT_MANAGER;
        return projectMemberRepository.findByProjectIdAndUserId(project.getId(), userId).map(ProjectMember::getProjectRole).orElse(null);
    }

    private ProjectResponse toResponse(Project project, UUID userId, boolean isAdmin) {
        List<ProjectMemberResponse> members = new ArrayList<>();
        if (project.getMembers() != null) {
            members = project.getMembers().stream()
                    .map(m -> ProjectMemberResponse.builder()
                            .id(m.getId()).projectRole(m.getProjectRole()).joinedAt(m.getJoinedAt())
                            .user(ProjectMemberResponse.UserInfo.builder()
                                    .id(m.getUser().getId()).fullName(m.getUser().getFullName())
                                    .email(m.getUser().getEmail()).avatarUrl(m.getUser().getAvatarUrl()).build())
                            .build()).collect(Collectors.toList());
        }
        ProjectResponse response = ProjectResponse.builder()
                .id(project.getId()).name(project.getName()).projectKey(project.getProjectKey())
                .description(project.getDescription()).status(project.getStatus()).visibility(project.getVisibility())
                .ownerId(project.getOwner().getId()).ownerName(project.getOwner().getFullName())
                .owner(userId != null && project.getOwner().getId().equals(userId))
                .startDate(project.getStartDate()).endDate(project.getEndDate()).members(members)
                .createdAt(project.getCreatedAt()).updatedAt(project.getUpdatedAt()).build();

        if (userId == null) {
            response.setMyRole("GUEST");
        } else if (isAdmin) {
            response.setMyRole("SYSTEM_ADMIN");
        } else if (project.getOwner().getId().equals(userId)) {
            response.setMyRole("PROJECT_MANAGER");
        } else {
            ProjectRole role = projectMemberRepository.findByProjectIdAndUserId(project.getId(), userId).map(ProjectMember::getProjectRole).orElse(null);
            if (role != null) {
                response.setMyRole(role.name());
            } else {
                response.setMyRole("GUEST");
            }
        }
        return response;
    }
}
