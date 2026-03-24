package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.ActivityLogResponse;
import com.zone.tasksphere.dto.response.PageResponse;
import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.UUID;

public interface ActivityLogService {
    
    /**
     * Ghi log thủ công thông qua service
     */
    void logActivity(UUID projectId, UUID actorId, EntityType type, UUID entityId, 
                     ActionType action, String oldVal, String newVal, HttpServletRequest request);

    /**
     * Truy vấn lịch sử hoạt động của dự án có phân trang và lọc
     */
    PageResponse<ActivityLogResponse> getProjectActivities(
            UUID projectId, UUID actorId, EntityType type, ActionType action, 
            Instant from, Instant to, Pageable pageable);

    /**
     * Truy vấn activity theo phạm vi task (bao gồm TASK/COMMENT/ATTACHMENT của task đó)
     */
    PageResponse<ActivityLogResponse> getTaskActivities(UUID projectId, UUID taskId, Pageable pageable);
}
