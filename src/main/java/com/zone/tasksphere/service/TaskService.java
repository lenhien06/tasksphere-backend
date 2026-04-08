package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.*;
import com.zone.tasksphere.dto.response.*;
import com.zone.tasksphere.dto.response.CalendarViewResponse;
import com.zone.tasksphere.dto.response.SubTaskResponse;
import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.TaskType;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TaskService {

    // ── P3-BE-01: Create ────────────────────────────────────────────
    TaskDetailResponse createTask(UUID projectId, CreateTaskRequest request, UUID currentUserId);

    // ── P3-BE-02: Read ──────────────────────────────────────────────
    PageResponse<TaskResponse> getTasks(UUID projectId, TaskFilterParams params,
                                        Pageable pageable, UUID currentUserId);

    TaskDetailResponse getTaskById(UUID projectId, UUID taskId, UUID currentUserId);

    // ── P3-BE-03: Update ────────────────────────────────────────────
    TaskDetailResponse updateTask(UUID projectId, UUID taskId,
                                  UpdateTaskRequest request, UUID currentUserId);

    TaskStatusChangedResponse updateStatus(UUID projectId, UUID taskId,
                                           UpdateTaskStatusRequest request, UUID currentUserId);

    void updatePosition(UUID projectId, UUID taskId,
                        UpdateTaskPositionRequest request, UUID currentUserId);

    // ── P3-BE-04: Delete ────────────────────────────────────────────
    void deleteTask(UUID projectId, UUID taskId, UUID currentUserId);

    // ── Optimistic locking ──────────────────────────────────────────
    void validateETag(UUID taskId, String ifMatch);

    // ── P3-BE-05: Sub-task ──────────────────────────────────────────
    TaskDetailResponse createSubTask(UUID parentTaskId, CreateTaskRequest request, UUID currentUserId);

    /** Shortcut: tạo sub-task và trả SubTaskResponse (dùng khi FE không cần full TaskDetailResponse) */
    SubTaskResponse createSubTaskLight(UUID parentTaskId, CreateTaskRequest request, UUID currentUserId);

    List<SubTaskResponse> getSubTasks(UUID taskId, UUID currentUserId);

    /**
     * Promote sub-task thành task độc lập.
     *
     * @param projectId nếu khác null, bắt buộc khớp project của task (404 khi không khớp)
     */
    TaskDetailResponse promoteSubTask(UUID subtaskId, PromoteSubTaskRequest request,
                                     UUID currentUserId, UUID projectId);

    // ── P3-BE-10: Calendar View ──────────────────────────────────────
    CalendarViewResponse getCalendarView(UUID projectId, int year, int month,
                                         String q, TaskStatus status, String assigneeId,
                                         UUID sprintId, List<TaskPriority> priorities, UUID currentUserId);

    TimelineViewResponse getTimelineView(UUID projectId, UUID currentUserId);

    ShiftTaskScheduleResponse shiftTaskSchedule(UUID projectId, UUID taskId,
                                                ShiftTaskScheduleRequest request, UUID currentUserId);

    // ── Calendar: Update due date ─────────────────────────────────────
    TaskDetailResponse updateDueDate(UUID projectId, UUID taskId,
                                     UpdateTaskDueDateRequest request, UUID currentUserId);
}
