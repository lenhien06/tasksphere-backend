package com.zone.tasksphere.component;

import com.zone.tasksphere.service.WorkspaceHealthMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkspaceHealthMetricsScheduler {

    private final WorkspaceHealthMetricsService workspaceHealthMetricsService;

    @Scheduled(cron = "0 */15 * * * *")
    public void refreshWorkspaceHealthMetricsCache() {
        log.debug("[WorkspaceHealth] Refreshing cached workspace health metrics");
        workspaceHealthMetricsService.refreshAllWorkspaceHealthMetrics();
    }
}
