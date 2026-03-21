package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.CreateChecklistItemRequest;
import com.zone.tasksphere.dto.request.ReorderChecklistRequest;
import com.zone.tasksphere.dto.request.UpdateChecklistItemRequest;
import com.zone.tasksphere.dto.response.ChecklistItemResponse;
import com.zone.tasksphere.dto.response.ChecklistSummary;
import com.zone.tasksphere.entity.ChecklistItem;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.ChecklistItemRepository;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.service.ChecklistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ChecklistServiceImpl implements ChecklistService {

    private final ChecklistItemRepository checklistItemRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Override
    public ChecklistItemResponse addItem(UUID taskId, CreateChecklistItemRequest request, UUID currentUserId) {
        Task task = getTask(taskId);
        validateMember(task.getProject().getId(), currentUserId);

        int nextOrder = checklistItemRepository.findMaxSortOrderByTaskId(taskId) + 1;

        ChecklistItem item = ChecklistItem.builder()
            .task(task)
            .title(request.getTitle())
            .isCompleted(false)
            .sortOrder(nextOrder)
            .build();

        return toResponse(checklistItemRepository.save(item));
    }

    @Override
    @Transactional(readOnly = true)
    public ChecklistSummary getChecklist(UUID taskId, UUID currentUserId) {
        Task task = getTask(taskId);
        validateMember(task.getProject().getId(), currentUserId);

        List<ChecklistItem> items = checklistItemRepository
            .findByTaskIdAndDeletedAtIsNullOrderBySortOrderAsc(taskId);

        int total = items.size();
        int completed = (int) items.stream().filter(ChecklistItem::isCompleted).count();

        return ChecklistSummary.builder()
            .total(total)
            .completed(completed)
            .items(items.stream().map(this::toResponse).toList())
            .build();
    }

    @Override
    public ChecklistItemResponse updateItem(UUID itemId, UpdateChecklistItemRequest request, UUID currentUserId) {
        ChecklistItem item = getItem(itemId);
        validateMember(item.getTask().getProject().getId(), currentUserId);

        if (request.getTitle() != null) {
            item.setTitle(request.getTitle());
        }

        if (request.getIsDone() != null) {
            item.setCompleted(request.getIsDone());
            if (request.getIsDone()) {
                item.setCompletedBy(getUser(currentUserId));
                item.setCompletedAt(Instant.now());
            } else {
                item.setCompletedBy(null);
                item.setCompletedAt(null);
            }
        }

        return toResponse(checklistItemRepository.save(item));
    }

    @Override
    public void deleteItem(UUID itemId, UUID currentUserId) {
        ChecklistItem item = getItem(itemId);
        validateMember(item.getTask().getProject().getId(), currentUserId);
        item.setDeletedAt(Instant.now());
        checklistItemRepository.save(item);
    }

    @Override
    public void reorder(UUID taskId, ReorderChecklistRequest request, UUID currentUserId) {
        Task task = getTask(taskId);
        validateMember(task.getProject().getId(), currentUserId);

        List<UUID> orderedIds = request.getOrderedIds();
        List<ChecklistItem> items = checklistItemRepository
            .findByIdInAndTaskIdAndDeletedAtIsNull(orderedIds, taskId);

        if (items.size() != orderedIds.size()) {
            throw new BadRequestException("Một số checklist item không thuộc task này hoặc đã bị xóa");
        }

        // Build a map for quick lookup
        var itemMap = items.stream()
            .collect(java.util.stream.Collectors.toMap(ChecklistItem::getId, i -> i));

        for (int i = 0; i < orderedIds.size(); i++) {
            ChecklistItem item = itemMap.get(orderedIds.get(i));
            if (item != null) {
                item.setSortOrder(i);
            }
        }

        checklistItemRepository.saveAll(items);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Task getTask(UUID taskId) {
        return taskRepository.findById(taskId)
            .orElseThrow(() -> new NotFoundException("Task not found: " + taskId));
    }

    private ChecklistItem getItem(UUID itemId) {
        return checklistItemRepository.findByIdAndDeletedAtIsNull(itemId)
            .orElseThrow(() -> new NotFoundException("Checklist item not found: " + itemId));
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    private void validateMember(UUID projectId, UUID userId) {
        var member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
            .orElseThrow(() -> new Forbidden("Bạn không phải thành viên dự án này"));
        if (member.getProjectRole() == ProjectRole.VIEWER) {
            throw new Forbidden("VIEWER không có quyền thực hiện thao tác này");
        }
    }

    private ChecklistItemResponse toResponse(ChecklistItem item) {
        ChecklistItemResponse.UserSummary completedBy = null;
        if (item.getCompletedBy() != null) {
            User u = item.getCompletedBy();
            completedBy = ChecklistItemResponse.UserSummary.builder()
                .id(u.getId())
                .fullName(u.getFullName())
                .avatarUrl(u.getAvatarUrl())
                .build();
        }

        return ChecklistItemResponse.builder()
            .id(item.getId())
            .title(item.getTitle())
            .isDone(item.isCompleted())
            .sortOrder(item.getSortOrder())
            .completedBy(completedBy)
            .completedAt(item.getCompletedAt())
            .createdAt(item.getCreatedAt())
            .build();
    }
}
