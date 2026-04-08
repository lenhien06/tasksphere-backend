package com.zone.tasksphere.component;

import com.zone.tasksphere.service.SprintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SprintAutoCloseJob {

    private final SprintService sprintService;

    @Scheduled(cron = "0 10 0 * * *")
    public void autoCloseExpiredSprints() {
        log.debug("[SprintAutoCloseJob] Checking overdue active sprints");
        sprintService.autoCloseExpiredSprints();
    }
}
