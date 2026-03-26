package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.AddDependencyRequest;
import com.zone.tasksphere.dto.response.DependencyResponse;
import com.zone.tasksphere.dto.response.TaskDependenciesResponse;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.TaskDependency;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.DependencyType;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.exception.StructuredApiException;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.TaskDependencyRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.service.TaskDependencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class TaskDependencyServiceImpl implements TaskDependencyService {

    private final TaskDependencyRepository dependencyRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository memberRepository;

    // ════════════════════════════════════════
    // POST /api/v1/projects/{projectId}/tasks/{taskId}/links (and /dependencies backward compat)
    // ════════════════════════════════════════
    @Override
    public DependencyResponse addDependency(UUID projectId, UUID taskId,
                                            AddDependencyRequest request, UUID currentUserId) {
        Task sourceTask = getTask(taskId, projectId);
        UUID resolvedProjectId = sourceTask.getProject().getId();

        validateMemberOrPM(resolvedProjectId, currentUserId);

        UUID targetTaskId = request.resolveTargetTaskId();
        if (targetTaskId == null) {
            throw dependencyError(HttpStatus.BAD_REQUEST, "DEPENDENCY_TARGET_REQUIRED",
                    "targetTaskId là bắt buộc");
        }

        // Self-dependency guard
        if (taskId.equals(targetTaskId)) {
            throw dependencyError(HttpStatus.BAD_REQUEST, "DEPENDENCY_SELF_REFERENCE",
                    "Task không thể liên kết với chính nó");
        }

        Task targetTask = taskRepository.findByIdAndProjectId(targetTaskId, resolvedProjectId)
                .orElseThrow(() -> dependencyError(HttpStatus.NOT_FOUND, "DEPENDENCY_TARGET_NOT_FOUND",
                        "Task không tồn tại hoặc không thuộc dự án này: " + targetTaskId));

        DependencyType linkType = request.getLinkType() != null ? request.getLinkType() : DependencyType.BLOCKS;

        // Check duplicate: source → target already exists?
        if (dependencyRepository.existsByBlockingTaskIdAndBlockedTaskId(taskId, targetTaskId)) {
            throw dependencyError(HttpStatus.CONFLICT, "DEPENDENCY_ALREADY_EXISTS",
                    "Liên kết dependency này đã tồn tại");
        }

        // Circular dependency check for blocking types only
        if (linkType == DependencyType.BLOCKS || linkType == DependencyType.BLOCKED_BY) {
            UUID effectiveBlocker = (linkType == DependencyType.BLOCKS) ? taskId : targetTaskId;
            UUID effectiveBlocked = (linkType == DependencyType.BLOCKS) ? targetTaskId : taskId;
            if (hasCircularDependency(effectiveBlocked, effectiveBlocker)) {
                throw dependencyError(HttpStatus.UNPROCESSABLE_ENTITY, "DEPENDENCY_CYCLE_DETECTED",
                        "Không thể tạo vòng lặp phụ thuộc");
            }
        }

        User creator = getUser(currentUserId);

        // Store link from source's perspective: source --[linkType]--> target
        TaskDependency link = TaskDependency.builder()
                .blockingTask(sourceTask)   // "source" in new model
                .blockedTask(targetTask)    // "target" in new model
                .linkType(linkType)
                .createdBy(creator)
                .build();
        link = dependencyRepository.save(link);

        // Auto-create inverse record
        DependencyType inverseLinkType = getInverseLinkType(linkType);
        if (!dependencyRepository.existsByBlockingTaskIdAndBlockedTaskId(targetTaskId, taskId)) {
            TaskDependency inverse = TaskDependency.builder()
                    .blockingTask(targetTask)   // target is source of inverse
                    .blockedTask(sourceTask)    // source is target of inverse
                    .linkType(inverseLinkType)
                    .createdBy(creator)
                    .build();
            dependencyRepository.save(inverse);
        }

        log.info("Link added: task {} --[{}]--> task {}", taskId, linkType, targetTaskId);
        return toDependencyResponse(link, sourceTask, targetTask);
    }

    // ════════════════════════════════════════
    // GET /api/v1/projects/{projectId}/tasks/{taskId}/links (and /dependencies)
    // ════════════════════════════════════════
    @Override
    @Transactional(readOnly = true)
    public TaskDependenciesResponse getDependencies(UUID projectId, UUID taskId, UUID currentUserId) {
        Task task = getTask(taskId, projectId);
        UUID resolvedProjectId = task.getProject().getId();

        validateProjectMembership(resolvedProjectId, currentUserId);

        // All links from this task's perspective (blockingTask = this task)
        List<TaskDependency> allLinks = dependencyRepository.findLinksBySourceTaskId(taskId);

        // Separate by linkType for backward-compat response structure
        List<TaskDependenciesResponse.DependencyItem> blockedBy = allLinks.stream()
                .filter(d -> d.getLinkType() == DependencyType.BLOCKED_BY)
                .map(d -> TaskDependenciesResponse.DependencyItem.builder()
                        .depId(d.getId())
                        .linkType(d.getLinkType().name())
                        .task(toTaskRef(d.getBlockedTask()))
                        .build())
                .toList();

        List<TaskDependenciesResponse.DependencyItem> blocking = allLinks.stream()
                .filter(d -> d.getLinkType() == DependencyType.BLOCKS)
                .map(d -> TaskDependenciesResponse.DependencyItem.builder()
                        .depId(d.getId())
                        .linkType(d.getLinkType().name())
                        .task(toTaskRef(d.getBlockedTask()))
                        .build())
                .toList();

        List<TaskDependenciesResponse.DependencyItem> others = allLinks.stream()
                .filter(d -> d.getLinkType() != DependencyType.BLOCKS
                          && d.getLinkType() != DependencyType.BLOCKED_BY)
                .map(d -> TaskDependenciesResponse.DependencyItem.builder()
                        .depId(d.getId())
                        .linkType(d.getLinkType().name())
                        .task(toTaskRef(d.getBlockedTask()))
                        .build())
                .toList();

        // canTransitionToDone: no BLOCKED_BY link whose target is not yet DONE/CANCELLED
        boolean canDone = allLinks.stream()
                .filter(d -> d.getLinkType() == DependencyType.BLOCKED_BY)
                .map(TaskDependency::getBlockedTask)
                .allMatch(target -> target.getTaskStatus() == TaskStatus.DONE
                        || target.getTaskStatus() == TaskStatus.CANCELLED);

        return TaskDependenciesResponse.builder()
                .blockedBy(blockedBy)
                .blocking(blocking)
                .others(others)
                .canTransitionToDone(canDone)
                .build();
    }

    // ════════════════════════════════════════
    // DELETE /api/v1/projects/{projectId}/tasks/{taskId}/links/{linkId}
    // ════════════════════════════════════════
    @Override
    public void removeDependency(UUID projectId, UUID taskId, UUID depId, UUID currentUserId) {
        Task task = getTask(taskId, projectId);
        UUID resolvedProjectId = task.getProject().getId();

        // Find by depId; the link's blockingTask must be taskId (source perspective)
        TaskDependency dependency = dependencyRepository.findById(depId)
                .filter(d -> d.getBlockingTask().getId().equals(taskId)
                          || d.getBlockedTask().getId().equals(taskId))
                .orElseThrow(() -> dependencyError(HttpStatus.NOT_FOUND, "DEPENDENCY_NOT_FOUND",
                        "Link không tồn tại hoặc không thuộc task này"));

        // Quyền: PM hoặc MEMBER
        validateMemberOrPM(resolvedProjectId, currentUserId);

        // Delete the inverse link first
        DependencyType inverseLinkType = getInverseLinkType(dependency.getLinkType());
        UUID inverseSourceId = dependency.getBlockedTask().getId();
        UUID inverseTargetId = dependency.getBlockingTask().getId();
        dependencyRepository.findByBlockingTaskIdAndBlockedTaskIdAndLinkType(
                inverseSourceId, inverseTargetId, inverseLinkType)
            .ifPresent(dependencyRepository::delete);

        dependencyRepository.delete(dependency);
        log.info("Link {} removed from task {}", depId, taskId);
    }

    // ════════════════════════════════════════
    // BFS CIRCULAR DEPENDENCY DETECTION
    // ════════════════════════════════════════

    /**
     * Kiểm tra circular: nếu thêm (blocked=taskId, blocker=newBlockerId) sẽ tạo vòng lặp không.
     * BFS từ newBlockerId theo chiều "ai block ai" — nếu gặp taskId → circular.
     */
    private boolean hasCircularDependency(UUID taskId, UUID newBlockerId) {
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();
        queue.add(newBlockerId);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (current.equals(taskId)) return true;
            if (visited.contains(current)) continue;
            visited.add(current);

            dependencyRepository.findDependsOnIdsByTaskId(current).forEach(queue::add);
        }
        return false;
    }

    /** Trả về linkType ngược lại để tạo inverse record */
    private DependencyType getInverseLinkType(DependencyType type) {
        return switch (type) {
            case BLOCKS         -> DependencyType.BLOCKED_BY;
            case BLOCKED_BY     -> DependencyType.BLOCKS;
            case RELATES_TO     -> DependencyType.RELATES_TO;
            case DUPLICATES     -> DependencyType.IS_DUPLICATED_BY;
            case IS_DUPLICATED_BY -> DependencyType.DUPLICATES;
        };
    }

    // ════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════

    private Task getTask(UUID taskId, UUID expectedProjectId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> dependencyError(HttpStatus.NOT_FOUND, "DEPENDENCY_TASK_NOT_FOUND",
                        "Task không tồn tại: " + taskId));
        if (expectedProjectId != null && !expectedProjectId.equals(task.getProject().getId())) {
            throw dependencyError(HttpStatus.NOT_FOUND, "DEPENDENCY_TASK_NOT_IN_PROJECT",
                    "Task không thuộc project trên path",
                    Map.of("taskId", taskId, "projectId", expectedProjectId));
        }
        return task;
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> dependencyError(HttpStatus.NOT_FOUND, "DEPENDENCY_USER_NOT_FOUND",
                        "User không tồn tại: " + userId));
    }

    private void validateProjectMembership(UUID projectId, UUID userId) {
        if (!memberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw dependencyError(HttpStatus.FORBIDDEN, "DEPENDENCY_PROJECT_ACCESS_DENIED",
                    "Bạn không phải thành viên dự án này");
        }
    }

    private void validateMemberOrPM(UUID projectId, UUID userId) {
        memberRepository.findByProjectIdAndUserId(projectId, userId)
                .filter(m -> m.getProjectRole() == ProjectRole.PROJECT_MANAGER
                        || m.getProjectRole() == ProjectRole.MEMBER)
                .orElseThrow(() -> dependencyError(HttpStatus.FORBIDDEN, "DEPENDENCY_WRITE_FORBIDDEN",
                        "Chỉ PM hoặc MEMBER mới được thực hiện hành động này"));
    }

    private DependencyResponse.TaskRef toTaskRef(Task task) {
        return DependencyResponse.TaskRef.builder()
                .id(task.getId())
                .taskCode(task.getTaskCode())
                .title(task.getTitle())
                .taskStatus(task.getTaskStatus())
                .priority(task.getPriority())
                .type(task.getType())
                .build();
    }

    private DependencyResponse toDependencyResponse(TaskDependency dep, Task sourceTask, Task targetTask) {
        return DependencyResponse.builder()
                .id(dep.getId())
                .task(toTaskRef(sourceTask))
                .dependsOnTask(toTaskRef(targetTask))
                .linkType(dep.getLinkType())
                .createdAt(dep.getCreatedAt())
                .build();
    }

    private StructuredApiException dependencyError(HttpStatus status, String code, String message) {
        return dependencyError(status, code, message, null);
    }

    private StructuredApiException dependencyError(HttpStatus status, String code,
                                                   String message, Map<String, Object> meta) {
        return new StructuredApiException(status, code, message, meta);
    }
}
