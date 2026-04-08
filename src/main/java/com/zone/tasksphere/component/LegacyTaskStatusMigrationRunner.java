package com.zone.tasksphere.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@Order(0)
public class LegacyTaskStatusMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updatedTaskStatuses = jdbcTemplate.update(
                "UPDATE tasks SET task_status = 'IN_REVIEW' WHERE task_status = 'READY_FOR_TEST'");

        int updatedMappedStatuses = jdbcTemplate.update(
                "UPDATE project_status_columns SET mapped_status = 'IN_REVIEW' WHERE mapped_status = 'READY_FOR_TEST'");

        int renamedColumns = jdbcTemplate.update(
                "UPDATE project_status_columns SET name = 'In Review' WHERE LOWER(TRIM(name)) = 'ready for test'");

        int updatedSavedFilters = jdbcTemplate.update(
                "UPDATE saved_filters SET filter_criteria = REPLACE(filter_criteria, 'READY_FOR_TEST', 'IN_REVIEW') "
                        + "WHERE filter_criteria LIKE '%READY_FOR_TEST%'");

        if (updatedTaskStatuses > 0 || updatedMappedStatuses > 0 || renamedColumns > 0 || updatedSavedFilters > 0) {
            log.info(
                    "[LegacyTaskStatusMigration] Migrated READY_FOR_TEST data -> IN_REVIEW (tasks={}, columns={}, names={}, filters={})",
                    updatedTaskStatuses,
                    updatedMappedStatuses,
                    renamedColumns,
                    updatedSavedFilters);
        } else {
            log.info("[LegacyTaskStatusMigration] No READY_FOR_TEST legacy data found.");
        }
    }
}
