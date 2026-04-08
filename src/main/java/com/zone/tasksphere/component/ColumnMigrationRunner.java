package com.zone.tasksphere.component;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectStatusColumn;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.enums.TaskStatus;
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
import java.util.Set;
import java.util.UUID;

/**
 * Tầng 2: Migration data cũ — chạy 1 lần khi app khởi động.
 * - Tìm project chưa có column → seed 5 cột mặc định.
 * - Tìm task chưa có statusColumn → gán vào cột đầu tiên của project.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2) // Chạy sau DataSeeder (Order=1)
public class ColumnMigrationRunner implements ApplicationRunner {

    private final ProjectRepository projectRepository;
    private final ProjectStatusColumnRepository columnRepository;
    private final TaskRepository taskRepository;
    private final DefaultColumnSeeder defaultColumnSeeder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        migrateProjectsWithoutColumns();
        migrateLegacyQaColumns();
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
                            log.warn(
                                    "[ColumnMigration] Project {} has no columns even after migration! Seeding on-demand.",
                                    task.getProject().getId());
                            List<ProjectStatusColumn> seeded = defaultColumnSeeder.seedForProject(task.getProject());
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

    private void migrateLegacyQaColumns() {
        List<Project> projects = projectRepository.findAll();
        int migratedProjects = 0;

        for (Project project : projects) {
            List<ProjectStatusColumn> columns = columnRepository.findByProjectOrderBySortOrderAsc(project);
            if (columns.isEmpty()) {
                continue;
            }

            boolean changed = false;
            ProjectStatusColumn reviewColumn = null;
            ProjectStatusColumn testingColumn = null;
            ProjectStatusColumn doneColumn = null;
            List<ProjectStatusColumn> duplicateReviewColumns = new java.util.ArrayList<>();

            for (ProjectStatusColumn column : columns) {
                String normalizedName = column.getName() == null ? "" : column.getName().trim().toLowerCase();
                if (column.getMappedStatus() == TaskStatus.DONE) {
                    doneColumn = column;
                }
                if (column.getMappedStatus() == TaskStatus.TESTING || normalizedName.contains("testing")) {
                    testingColumn = column;
                    if (column.getMappedStatus() != TaskStatus.TESTING) {
                        column.setMappedStatus(TaskStatus.TESTING);
                        changed = true;
                    }
                }
                boolean isReviewColumn = column.getMappedStatus() == TaskStatus.IN_REVIEW
                        || column.getMappedStatus() == TaskStatus.READY_FOR_TEST
                        || normalizedName.contains("ready for test")
                        || normalizedName.contains("in review");
                if (isReviewColumn) {
                    if (reviewColumn == null) {
                        reviewColumn = column;
                        if (!"In Review".equals(column.getName())) {
                            column.setName("In Review");
                            changed = true;
                        }
                        if (column.getMappedStatus() != TaskStatus.IN_REVIEW) {
                            column.setMappedStatus(TaskStatus.IN_REVIEW);
                            changed = true;
                        }
                    } else if (!java.util.Objects.equals(reviewColumn.getId(), column.getId())) {
                        duplicateReviewColumns.add(column);
                    }
                }
            }

            if (reviewColumn == null) {
                reviewColumn = ProjectStatusColumn.builder()
                        .project(project)
                        .name("In Review")
                        .colorHex("#FAAD14")
                        .isDefault(false)
                        .mappedStatus(TaskStatus.IN_REVIEW)
                        .build();
                reviewColumn = columnRepository.save(reviewColumn);
                columns.add(reviewColumn);
                changed = true;
            }

            if (testingColumn == null) {
                testingColumn = ProjectStatusColumn.builder()
                        .project(project)
                        .name("Testing")
                        .colorHex("#722ED1")
                        .isDefault(false)
                        .mappedStatus(TaskStatus.TESTING)
                        .build();
                columns.add(testingColumn);
                changed = true;
            }

            if (!duplicateReviewColumns.isEmpty()) {
                Set<UUID> duplicateIds = duplicateReviewColumns.stream()
                        .map(ProjectStatusColumn::getId)
                        .collect(java.util.stream.Collectors.toSet());
                List<Task> tasksToReassign = new java.util.ArrayList<>();
                for (Task task : taskRepository.findTimelineTasksByProjectId(project.getId())) {
                    if (task.getStatusColumn() != null && duplicateIds.contains(task.getStatusColumn().getId())) {
                        task.setStatusColumn(reviewColumn);
                        if (task.getTaskStatus() != TaskStatus.DONE && task.getTaskStatus() != TaskStatus.CANCELLED) {
                            task.setTaskStatus(TaskStatus.IN_REVIEW);
                        }
                        tasksToReassign.add(task);
                    }
                }
                if (!tasksToReassign.isEmpty()) {
                    taskRepository.saveAll(tasksToReassign);
                }
                columnRepository.deleteAll(duplicateReviewColumns);
                changed = true;
            }

            columns.sort(java.util.Comparator.comparingInt(ProjectStatusColumn::getSortOrder));
            List<ProjectStatusColumn> reordered = new java.util.ArrayList<>();
            Set<UUID> duplicateIds = duplicateReviewColumns.stream()
                    .map(ProjectStatusColumn::getId)
                    .collect(java.util.stream.Collectors.toSet());
            for (ProjectStatusColumn column : columns) {
                if (!java.util.Objects.equals(column.getId(), reviewColumn.getId())
                        && !java.util.Objects.equals(column.getId(), testingColumn.getId())
                        && !java.util.Objects.equals(column.getId(), doneColumn == null ? null : doneColumn.getId())
                        && !duplicateIds.contains(column.getId())) {
                    reordered.add(column);
                }
            }

            int doneIndex = doneColumn == null ? reordered.size() : reordered.indexOf(doneColumn);
            if (doneIndex < 0) {
                doneIndex = reordered.size();
            }
            reordered.add(Math.min(doneIndex, reordered.size()), reviewColumn);
            reordered.add(Math.min(doneIndex + 1, reordered.size()), testingColumn);
            if (doneColumn != null) {
                reordered.add(Math.min(doneIndex + 2, reordered.size()), doneColumn);
            }

            for (int i = 0; i < reordered.size(); i++) {
                reordered.get(i).setSortOrder(i + 1);
            }

            if (changed) {
                columnRepository.saveAll(reordered);
                migrateQaTaskStatuses(project);
                migratedProjects++;
            }
        }

        if (migratedProjects > 0) {
            log.info("[ColumnMigration] Migrated QA workflow columns for {} project(s).", migratedProjects);
        }
    }

    private void migrateQaTaskStatuses(Project project) {
        List<Task> tasks = taskRepository.findTimelineTasksByProjectId(project.getId());
        List<Task> toSave = new java.util.ArrayList<>();
        for (Task task : tasks) {
            if (task.getStatusColumn() == null || task.getTaskStatus() == TaskStatus.DONE
                    || task.getTaskStatus() == TaskStatus.CANCELLED) {
                continue;
            }
            TaskStatus mappedStatus = task.getStatusColumn().getMappedStatus();
            if (mappedStatus != null && task.getTaskStatus() != mappedStatus) {
                task.setTaskStatus(mappedStatus);
                toSave.add(task);
            }
        }

        if (!toSave.isEmpty()) {
            taskRepository.saveAll(toSave);
        }
    }
}
