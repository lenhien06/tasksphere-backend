package com.zone.tasksphere.component;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectStatusColumn;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.ProjectStatusColumnRepository;
import com.zone.tasksphere.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Tầng 2: Migration data cũ — chạy 1 lần khi app khởi động.
 * - Tìm project chưa có column → seed 4 cột mặc định.
 * - Tìm task chưa có statusColumn → gán vào cột đầu tiên của project.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)   // Chạy sau DataSeeder (Order=1)
public class ColumnMigrationRunner implements ApplicationRunner {

    private final ProjectRepository projectRepository;
    private final ProjectStatusColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final DefaultColumnSeeder defaultColumnSeeder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateProjectsWithoutColumns();
        migrateTasksWithoutColumn();
    }

    // ── Migrate projects chưa có column ────────────────────────────

    private void migrateProjectsWithoutColumns() {
        List<Project> orphanProjects = projectRepository.findProjectsWithoutColumns();

        if (orphanProjects.isEmpty()) {
            log.info("[ColumnMigration] All projects have columns. Nothing to migrate.");
            return;
        }

        log.warn("[ColumnMigration] Found {} project(s) without columns. Seeding...",
            orphanProjects.size());

        for (Project project : orphanProjects) {
            try {
                defaultColumnSeeder.seedForProject(project);
                log.info("[ColumnMigration] Seeded columns for project: {} ({})",
                    project.getName(), project.getId());
            } catch (Exception e) {
                log.error("[ColumnMigration] Failed to seed columns for project {}: {}",
                    project.getId(), e.getMessage());
            }
        }

        log.info("[ColumnMigration] Done. {} project(s) migrated.", orphanProjects.size());
    }

    // ── Migrate tasks chưa có statusColumn ─────────────────────────

    private void migrateTasksWithoutColumn() {
        List<Task> orphanTasks = taskRepository.findTasksWithoutStatusColumn();

        if (orphanTasks.isEmpty()) {
            log.info("[ColumnMigration] All tasks have statusColumn. Nothing to fix.");
            return;
        }

        log.warn("[ColumnMigration] Found {} task(s) without statusColumn. Fixing...",
            orphanTasks.size());

        int fixed = 0;
        for (Task task : orphanTasks) {
            try {
                ProjectStatusColumn firstCol = columnRepository
                    .findFirstByProjectOrderBySortOrderAsc(task.getProject())
                    .orElseGet(() -> {
                        // Edge case: project vẫn không có column sau migration → seed ngay
                        log.warn("[ColumnMigration] Project {} has no columns even after migration! Seeding on-demand.",
                            task.getProject().getId());
                        List<ProjectStatusColumn> seeded =
                            defaultColumnSeeder.seedForProject(task.getProject());
                        return seeded.get(0);
                    });

                task.setStatusColumn(firstCol);
                taskRepository.save(task);
                fixed++;
            } catch (Exception e) {
                log.error("[ColumnMigration] Failed to fix task {}: {}", task.getId(), e.getMessage());
            }
        }

        log.info("[ColumnMigration] Fixed {}/{} orphan tasks.", fixed, orphanTasks.size());
    }
}
