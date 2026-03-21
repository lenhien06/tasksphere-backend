package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.CreateColumnRequest;
import com.zone.tasksphere.dto.request.ReorderColumnsRequest;
import com.zone.tasksphere.dto.request.UpdateColumnRequest;
import com.zone.tasksphere.dto.response.ColumnResponse;
import com.zone.tasksphere.dto.response.DeleteColumnResponse;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectStatusColumn;
import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.exception.BusinessRuleException;
import com.zone.tasksphere.exception.ConflictException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.ProjectStatusColumnRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.service.ActivityLogService;
import com.zone.tasksphere.service.ColumnService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class ColumnServiceImpl implements ColumnService {

    private final ProjectStatusColumnRepository columnRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final TaskRepository taskRepository;
    private final ActivityLogService activityLogService;

    // ════════════════════════════════════════
    // POST /api/v1/projects/{projectId}/columns
    // ════════════════════════════════════════
    @Override
    public ColumnResponse createColumn(UUID projectId, CreateColumnRequest request, UUID currentUserId) {
        validatePM(projectId, currentUserId);
        Project project = getProject(projectId);

        // Check tên trùng (case-insensitive)
        if (columnRepository.existsByProjectIdAndNameIgnoreCase(projectId, request.getName())) {
            throw new ConflictException("Tên cột đã tồn tại trong dự án");
        }

        // SortOrder = max + 1
        int sortOrder = columnRepository.findMaxSortOrderByProjectId(projectId)
                .map(max -> max + 1)
                .orElse(1);

        ProjectStatusColumn column = ProjectStatusColumn.builder()
                .project(project)
                .name(request.getName())
                .colorHex(request.getColorHex())
                .mappedStatus(request.getMappedStatus())
                .sortOrder(sortOrder)
                .isDefault(false)
                .build();

        column = columnRepository.save(column);

        logActivity(projectId, currentUserId, column.getId(), ActionType.CREATED, null, column.getName());
        log.info("Column '{}' created in project {}", column.getName(), projectId);

        return toResponse(column, 0);
    }

    // ════════════════════════════════════════
    // PUT /api/v1/columns/{columnId}
    // ════════════════════════════════════════
    @Override
    public ColumnResponse updateColumn(UUID columnId, UpdateColumnRequest request, UUID currentUserId) {
        ProjectStatusColumn column = getColumn(columnId);
        UUID projectId = column.getProject().getId();
        validatePM(projectId, currentUserId);

        // Check tên trùng (loại trừ cột hiện tại)
        if (request.getName() != null && !request.getName().isBlank()
                && !request.getName().equalsIgnoreCase(column.getName())) {
            if (columnRepository.existsByProjectIdAndNameIgnoreCaseExcludingId(
                    projectId, request.getName(), columnId)) {
                throw new ConflictException("Tên cột đã tồn tại trong dự án");
            }
            column.setName(request.getName());
        }

        if (request.getColorHex() != null) {
            column.setColorHex(request.getColorHex());
        }

        column = columnRepository.save(column);
        logActivity(projectId, currentUserId, column.getId(), ActionType.UPDATED, null, column.getName());

        int taskCount = (int) taskRepository.countByStatusColumnId(columnId);
        return toResponse(column, taskCount);
    }

    // ════════════════════════════════════════
    // DELETE /api/v1/columns/{columnId}
    // ════════════════════════════════════════
    @Override
    public DeleteColumnResponse deleteColumn(UUID columnId, UUID currentUserId) {
        ProjectStatusColumn column = getColumn(columnId);
        UUID projectId = column.getProject().getId();
        validatePM(projectId, currentUserId);

        // Không cho xóa cột mặc định
        if (column.isDefault()) {
            throw new BusinessRuleException("Không thể xóa cột mặc định");
        }

        // FIX: BR-29 - Phải có ít nhất 1 cột START (TODO) và 1 cột DONE
        if (column.getMappedStatus() == TaskStatus.TODO || column.getMappedStatus() == TaskStatus.DONE) {
            long countSameStatus = columnRepository.countByProjectIdAndMappedStatus(
                    projectId, column.getMappedStatus());
            if (countSameStatus <= 1) {
                throw new BusinessRuleException(
                    "BR-29: Không thể xóa cột cuối cùng có trạng thái " + column.getMappedStatus()
                    + ". Dự án phải có ít nhất 1 cột START (Todo) và 1 cột DONE.");
            }
        }

        // Đếm số task trong cột
        int movedTaskCount = (int) taskRepository.countByStatusColumnId(columnId);

        // Tìm cột ToDo mặc định để chuyển task sang
        ProjectStatusColumn todoColumn = columnRepository
                .findFirstByProjectIdAndMappedStatusAndIsDefaultTrue(projectId, TaskStatus.TODO)
                .orElseThrow(() -> new BusinessRuleException("Không tìm thấy cột To Do mặc định để chuyển task"));

        // Chuyển tất cả task sang cột ToDo
        if (movedTaskCount > 0) {
            taskRepository.moveTasksToColumn(columnId, todoColumn, TaskStatus.TODO);
        }

        // Soft delete column
        column.setDeletedAt(Instant.now());
        columnRepository.save(column);

        logActivity(projectId, currentUserId, column.getId(), ActionType.DELETED, column.getName(), null);
        log.info("Column '{}' deleted in project {}, {} tasks moved to '{}'",
                column.getName(), projectId, movedTaskCount, todoColumn.getName());

        return DeleteColumnResponse.builder()
                .message("Đã xóa cột " + column.getName())
                .movedTaskCount(movedTaskCount)
                .movedToColumn(todoColumn.getName())
                .build();
    }

    // ════════════════════════════════════════
    // PATCH /api/v1/projects/{projectId}/columns/reorder
    // ════════════════════════════════════════
    @Override
    public List<ColumnResponse> reorderColumns(UUID projectId, ReorderColumnsRequest request, UUID currentUserId) {
        validatePM(projectId, currentUserId);

        List<ProjectStatusColumn> activeColumns =
                columnRepository.findByProjectIdOrderBySortOrderAsc(projectId);

        Set<UUID> activeIds = activeColumns.stream()
                .map(ProjectStatusColumn::getId)
                .collect(Collectors.toSet());

        // Validate số lượng
        if (request.getOrderedIds().size() != activeColumns.size()) {
            throw new BusinessRuleException("Danh sách cột không đầy đủ");
        }

        // Validate tất cả id thuộc project
        for (UUID id : request.getOrderedIds()) {
            if (!activeIds.contains(id)) {
                throw new BusinessRuleException("Cột không tồn tại hoặc không thuộc dự án: " + id);
            }
        }

        // Map column by ID để cập nhật sortOrder
        var columnById = activeColumns.stream()
                .collect(Collectors.toMap(ProjectStatusColumn::getId, c -> c));

        List<ProjectStatusColumn> toSave = new ArrayList<>();
        for (int i = 0; i < request.getOrderedIds().size(); i++) {
            ProjectStatusColumn col = columnById.get(request.getOrderedIds().get(i));
            col.setSortOrder(i + 1);
            toSave.add(col);
        }

        columnRepository.saveAll(toSave);

        return toSave.stream()
                .map(col -> toResponse(col, (int) taskRepository.countByStatusColumnId(col.getId())))
                .toList();
    }

    // ════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════

    private void validatePM(UUID projectId, UUID userId) {
        memberRepository.findByProjectIdAndUserId(projectId, userId)
                .filter(m -> m.getProjectRole() == ProjectRole.PROJECT_MANAGER)
                .orElseThrow(() -> new Forbidden("Chỉ Project Manager mới được thực hiện hành động này"));
    }

    private Project getProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project không tồn tại: " + projectId));
    }

    private ProjectStatusColumn getColumn(UUID columnId) {
        return columnRepository.findById(columnId)
                .orElseThrow(() -> new NotFoundException("Column không tồn tại: " + columnId));
    }

    private ColumnResponse toResponse(ProjectStatusColumn column, int taskCount) {
        return ColumnResponse.builder()
                .id(column.getId())
                .name(column.getName())
                .colorHex(column.getColorHex())
                .sortOrder(column.getSortOrder())
                .isDefault(column.isDefault())
                .mappedStatus(column.getMappedStatus())
                .taskCount(taskCount)
                .build();
    }

    private void logActivity(UUID projectId, UUID actorId, UUID entityId,
                              ActionType action, String oldVal, String newVal) {
        try {
            HttpServletRequest req = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getRequest();
            activityLogService.logActivity(projectId, actorId, EntityType.PROJECT, entityId,
                    action, oldVal, newVal, req);
        } catch (Exception e) {
            log.warn("Failed to log column activity: {}", e.getMessage());
        }
    }
}
