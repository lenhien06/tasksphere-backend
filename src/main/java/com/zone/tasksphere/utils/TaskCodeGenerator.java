package com.zone.tasksphere.utils;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Atomic task code generator.
 *
 * Chiến lược: dùng Project.taskCounter + pessimistic lock (SELECT FOR UPDATE).
 * - Propagation.REQUIRES_NEW → luôn mở transaction MỚI, tách khỏi transaction gọi.
 *   Điều này đảm bảo counter được commit ngay lập tức, tránh race condition.
 * - SELECT ... FOR UPDATE → chỉ 1 thread/request được tăng counter tại một thời điểm.
 *
 * Format output: PROJECT_KEY-NNN  (e.g. NTTMTA-001, NTTMTA-002)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskCodeGenerator {

    private final ProjectRepository projectRepository;

    /**
     * Sinh task code atomic bằng cách tăng taskCounter của project.
     * Dùng REQUIRES_NEW để transaction này commit độc lập.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateTaskCode(Project project) {
        return generateTaskCode(project.getId());
    }

    /**
     * Overload nhận UUID — tiện dùng khi chưa có Project entity trong tay.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String generateTaskCode(UUID projectId) {
        // SELECT ... FOR UPDATE: lock row → chỉ 1 thread vào được tại mỗi thời điểm
        Project locked = projectRepository.findByIdWithLock(projectId)
            .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        int next = locked.getTaskCounter() + 1;
        locked.setTaskCounter(next);
        projectRepository.save(locked); // commit ngay sau khi REQUIRES_NEW tx kết thúc

        String code = String.format("%s-%03d", locked.getProjectKey(), next);
        log.debug("[TaskCodeGenerator] Generated code: {} (counter={})", code, next);
        return code;
    }
}
