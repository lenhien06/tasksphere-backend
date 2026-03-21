package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.SetRecurrenceRequest;
import com.zone.tasksphere.dto.response.RecurrenceResponse;
import com.zone.tasksphere.dto.response.TaskSummaryResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface RecurringTaskService {

    /**
     * Thiết lập lịch lặp cho task. Task phải tồn tại, chưa là instance, chưa có config lặp.
     */
    RecurrenceResponse setRecurrence(UUID taskId, SetRecurrenceRequest request, UUID currentUserId);

    /**
     * Lấy thông tin cấu hình lặp của task.
     */
    RecurrenceResponse getRecurrence(UUID taskId, UUID currentUserId);

    /**
     * Cập nhật cấu hình lặp hiện tại của task.
     */
    RecurrenceResponse updateRecurrence(UUID taskId, SetRecurrenceRequest request, UUID currentUserId);

    /**
     * Hủy cấu hình lặp. Các instance TODO chưa gán sprint sẽ bị soft-delete.
     */
    Map<String, Object> deleteRecurrence(UUID taskId, UUID currentUserId);

    /**
     * Trả về danh sách các task instance được sinh ra từ template task này.
     */
    List<TaskSummaryResponse> getInstances(UUID taskId, UUID currentUserId);
}
