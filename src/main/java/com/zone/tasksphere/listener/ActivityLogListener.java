package com.zone.tasksphere.listener;

import com.zone.tasksphere.entity.ActivityLog;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.event.ActivityLogEvent;
import com.zone.tasksphere.repository.ActivityLogRepository;
import com.zone.tasksphere.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActivityLogListener {

    private final ActivityLogRepository activityLogRepository;
    private final UserRepository userRepository;

    /**
     * Lắng nghe ActivityLogEvent để lưu vết lịch sử.
     * Hoạt động @Async để không làm chậm luồng xử lý CRUD của người dùng.
     */
    @Async
    @EventListener
    @Transactional
    public void handleActivityLogEvent(ActivityLogEvent event) {
        try {
            User actor = null;
            if (event.getActorId() != null) {
                actor = userRepository.findById(event.getActorId()).orElse(null);
            }

            ActivityLog activityLog = ActivityLog.builder()
                    .projectId(event.getProjectId())
                    .actor(actor)
                    .entityType(event.getEntityType())
                    .entityId(event.getEntityId())
                    .action(event.getAction())
                    .oldValues(event.getOldValues())
                    .newValues(event.getNewValues())
                    .ipAddress(event.getIpAddress())
                    .userAgent(event.getUserAgent())
                    .build();

            activityLogRepository.save(activityLog);
            
            log.info("Activity Log saved successfully for entity {} (ID: {})", 
                     event.getEntityType(), event.getEntityId());
            
            // TODO: TRIGGER NOTIFICATION HERE
            // Sau khi log được lưu, chúng ta có thể gọi NotificationService tại đây 
            // để bắn thông báo (Real-time hoặc Email) dựa trên event.
            
        } catch (Exception e) {
            log.error("Failed to save activity log for event: {}", event.getEntityType(), e);
        }
    }
}
