package com.zone.tasksphere.mapper;

import com.zone.tasksphere.dto.request.UpdateTaskRequest;
import com.zone.tasksphere.dto.response.CustomFieldValueResponse;
import com.zone.tasksphere.dto.response.TaskDetailResponse;
import com.zone.tasksphere.dto.response.TaskResponse;
import com.zone.tasksphere.entity.CustomFieldValue;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.CustomFieldType;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.repository.AttachmentRepository;
import com.zone.tasksphere.repository.CustomFieldValueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskMapper {

    private final CustomFieldValueRepository customFieldValueRepository;
    private final AttachmentRepository attachmentRepository;

    // ── Light response (Kanban / List view) ──────────────────────────

    public TaskResponse toResponse(Task task) {
        User assignee = task.getAssignee();
        User reporter = task.getReporter();

        int subtaskCount = task.getChildTasks() != null ? task.getChildTasks().size() : 0;
        int subtaskDone  = task.getChildTasks() != null
            ? (int) task.getChildTasks().stream()
                .filter(t -> t.getTaskStatus() == TaskStatus.DONE)
                .count()
            : 0;
        int taskOnlyAttachmentCount = (int) attachmentRepository.countByTaskIdAndCommentIsNull(task.getId());

        return TaskResponse.builder()
            .id(task.getId())
            .taskCode(task.getTaskCode())
            .title(task.getTitle())
            .type(task.getType())
            .priority(task.getPriority())
            .taskStatus(task.getTaskStatus())
            .sprintId(task.getSprint() != null ? task.getSprint().getId() : null)
            .sprintName(task.getSprint() != null ? task.getSprint().getName() : null)
            .columnId(task.getStatusColumn() != null ? task.getStatusColumn().getId() : null)
            .columnName(task.getStatusColumn() != null ? task.getStatusColumn().getName() : null)
            .taskPosition(task.getTaskPosition())
            .storyPoints(task.getStoryPoints())
            .startDate(task.getStartDate())
            .dueDate(task.getDueDate())
            .overdue(isOverdue(task))
            .subtaskCount(subtaskCount)
            .subtaskDone(subtaskDone)
            .commentsCount(task.getComments() != null ? task.getComments().size() : 0)
            .attachmentsCount(taskOnlyAttachmentCount)
            .createdAt(task.getCreatedAt())
            .updatedAt(task.getUpdatedAt())
            .assignee(toUserSummary(assignee))
            .reporter(toUserSummary(reporter))
            .build();
    }

    // ── Detail response ──────────────────────────────────────────────

    public TaskDetailResponse toDetailResponse(Task task) {
        List<TaskDetailResponse.SubTaskSummary> subtasks = task.getChildTasks() != null
            ? task.getChildTasks().stream().map(this::toSubTaskSummary).toList()
            : Collections.emptyList();

        int subtaskDone = (int) subtasks.stream()
            .filter(s -> s.getTaskStatus() == TaskStatus.DONE)
            .count();
        int taskOnlyAttachmentCount = (int) attachmentRepository.countByTaskIdAndCommentIsNull(task.getId());

        List<CustomFieldValueResponse> customFieldValues = toCustomFieldValueResponses(task.getId());

        return TaskDetailResponse.builder()
            .id(task.getId())
            .taskCode(task.getTaskCode())
            .version(task.getVersion())
            .title(task.getTitle())
            .description(task.getDescription())
            .type(task.getType())
            .taskStatus(task.getTaskStatus())
            .priority(task.getPriority())
            .taskPosition(task.getTaskPosition())
            .statusColumn(toColumnSummary(task))
            .storyPoints(task.getStoryPoints())
            .estimatedHours(task.getEstimatedHours())
            .actualHours(task.getActualHours())
            .startDate(task.getStartDate())
            .dueDate(task.getDueDate())
            .overdue(isOverdue(task))
            .isRecurring(task.isRecurring())
            .depth(task.getDepth())
            .subtaskCount(subtasks.size())
            .subtaskDoneCount(subtaskDone)
            .subtaskProgress(subtasks.isEmpty() ? null : (int) Math.round((double) subtaskDone / subtasks.size() * 100))
            .parentTask(toTaskSummary(task.getParentTask()))
            .subtasks(subtasks)
            .commentCount(task.getComments() != null ? task.getComments().size() : 0)
            .attachmentCount(taskOnlyAttachmentCount)
            .assignee(toDetailUserSummary(task.getAssignee()))
            .reporter(toDetailUserSummary(task.getReporter()))
            .sprint(toSprintSummary(task))
            .projectVersion(toVersionSummary(task))
            .customFieldValues(customFieldValues)
            .createdAt(task.getCreatedAt())
            .updatedAt(task.getUpdatedAt())
            .build();
    }

    private List<CustomFieldValueResponse> toCustomFieldValueResponses(java.util.UUID taskId) {
        try {
            List<CustomFieldValue> values = customFieldValueRepository.findByTaskId(taskId);
            return values.stream().map(this::toCustomFieldValueResponse).toList();
        } catch (Exception e) {
            log.warn("Could not load custom field values for task {}: {}", taskId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private CustomFieldValueResponse toCustomFieldValueResponse(CustomFieldValue cfv) {
        CustomFieldType type = cfv.getCustomField().getFieldType();
        String strValue = getStringValue(cfv, type);
        return CustomFieldValueResponse.builder()
                .fieldId(cfv.getCustomField().getId())
                .fieldName(cfv.getCustomField().getName())
                .fieldType(type)
                .value(strValue)
                .typedValue(getTypedValue(type, strValue))
                .build();
    }

    private String getStringValue(CustomFieldValue cfv, CustomFieldType type) {
        return switch (type) {
            case TEXT, SELECT, MULTI_SELECT, URL -> cfv.getTextValue();
            case NUMBER -> cfv.getNumberValue() != null ? cfv.getNumberValue().toPlainString() : null;
            case DATE -> cfv.getDateValue() != null ? cfv.getDateValue().toString() : null;
            case BOOLEAN -> cfv.getBooleanValue() != null ? cfv.getBooleanValue().toString() : null;
        };
    }

    private Object getTypedValue(CustomFieldType type, String value) {
        if (value == null) return null;
        return switch (type) {
            case NUMBER -> Double.parseDouble(value);
            case DATE -> LocalDate.parse(value);
            case BOOLEAN -> Boolean.parseBoolean(value);
            default -> value;
        };
    }

    /** Áp dụng các field từ UpdateTaskRequest vào entity (chỉ override khi không null) */
    public void updateEntityFromRequest(Task task, UpdateTaskRequest request) {
        if (request.getTitle() != null) task.setTitle(request.getTitle());
        if (request.getDescription() != null) task.setDescription(request.getDescription());
        if (request.getType() != null) task.setType(request.getType());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getStartDate() != null) task.setStartDate(request.getStartDate());
        if (request.getDueDate() != null) task.setDueDate(request.getDueDate());
        if (request.getStoryPoints() != null) task.setStoryPoints(request.getStoryPoints());
        if (request.getEstimatedHours() != null) task.setEstimatedHours(request.getEstimatedHours());
    }

    // ── Private helpers ──────────────────────────────────────────────

    private boolean isOverdue(Task task) {
        return task.getDueDate() != null
            && task.getDueDate().isBefore(LocalDate.now())
            && task.getTaskStatus() != TaskStatus.DONE
            && task.getTaskStatus() != TaskStatus.CANCELLED;
    }

    private TaskResponse.UserSummary toUserSummary(User user) {
        if (user == null) return null;
        return TaskResponse.UserSummary.builder()
            .id(user.getId())
            .fullName(user.getFullName())
            .avatarUrl(user.getAvatarUrl())
            .build();
    }

    private TaskDetailResponse.UserSummary toDetailUserSummary(User user) {
        if (user == null) return null;
        return TaskDetailResponse.UserSummary.builder()
            .id(user.getId())
            .fullName(user.getFullName())
            .avatarUrl(user.getAvatarUrl())
            .build();
    }

    private TaskDetailResponse.ColumnSummary toColumnSummary(Task task) {
        if (task.getStatusColumn() == null) return null;
        return TaskDetailResponse.ColumnSummary.builder()
            .id(task.getStatusColumn().getId())
            .name(task.getStatusColumn().getName())
            .colorHex(task.getStatusColumn().getColorHex())
            .sortOrder(task.getStatusColumn().getSortOrder())
            .build();
    }

    private TaskDetailResponse.SprintSummary toSprintSummary(Task task) {
        if (task.getSprint() == null) return null;
        return TaskDetailResponse.SprintSummary.builder()
            .id(task.getSprint().getId())
            .name(task.getSprint().getName())
            .status(task.getSprint().getStatus() != null ? task.getSprint().getStatus().name() : null)
            .startDate(task.getSprint().getStartDate())
            .endDate(task.getSprint().getEndDate())
            .build();
    }

    private TaskDetailResponse.TaskSummary toTaskSummary(Task task) {
        if (task == null) return null;
        return TaskDetailResponse.TaskSummary.builder()
            .id(task.getId())
            .taskCode(task.getTaskCode())
            .title(task.getTitle())
            .build();
    }

    private TaskDetailResponse.SubTaskSummary toSubTaskSummary(Task task) {
        return TaskDetailResponse.SubTaskSummary.builder()
            .id(task.getId())
            .taskCode(task.getTaskCode())
            .title(task.getTitle())
            .taskStatus(task.getTaskStatus())
            .priority(task.getPriority())
            .assignee(toDetailUserSummary(task.getAssignee()))
            .build();
    }

    private TaskDetailResponse.VersionSummary toVersionSummary(Task task) {
        if (task.getProjectVersion() == null) return null;
        return TaskDetailResponse.VersionSummary.builder()
            .id(task.getProjectVersion().getId())
            .name(task.getProjectVersion().getName())
            .build();
    }
}
