package com.zone.tasksphere.component;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectStatusColumn;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.repository.ProjectStatusColumnRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeder tái sử dụng để tạo 4 cột Kanban mặc định cho một Project.
 * Được gọi bởi:
 *   - ColumnMigrationRunner (startup — migrate data cũ)
 *   - TaskServiceImpl (safety guard — edge case)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultColumnSeeder {

    private final ProjectStatusColumnRepository columnRepository;

    /** 4 cột mặc định — khớp với logic đang có trong ProjectService.createProject() */
    private static final List<DefaultColumnDef> DEFAULT_COLUMNS = List.of(
        new DefaultColumnDef("To Do",       "#D9D9D9", 1, true,  TaskStatus.TODO),
        new DefaultColumnDef("In Progress", "#1677FF", 2, false, TaskStatus.IN_PROGRESS),
        new DefaultColumnDef("In Review",   "#FAAD14", 3, false, TaskStatus.IN_REVIEW),
        new DefaultColumnDef("Done",        "#52C41A", 4, false, TaskStatus.DONE)
    );

    /**
     * Seed 4 cột mặc định cho project.
     * Guard: nếu project đã có ít nhất 1 cột → trả về danh sách hiện có, không seed thêm.
     */
    @Transactional
    public List<ProjectStatusColumn> seedForProject(Project project) {
        if (columnRepository.existsByProject(project)) {
            log.debug("[DefaultColumnSeeder] Project {} đã có columns, bỏ qua.", project.getId());
            return columnRepository.findByProjectOrderBySortOrderAsc(project);
        }

        log.info("[DefaultColumnSeeder] Seeding default columns for project: {} ({})",
            project.getName(), project.getId());

        List<ProjectStatusColumn> columns = DEFAULT_COLUMNS.stream()
            .map(def -> ProjectStatusColumn.builder()
                .project(project)
                .name(def.name())
                .colorHex(def.color())
                .sortOrder(def.sortOrder())
                .isDefault(def.isDefault())
                .mappedStatus(def.mappedStatus())
                .build())
            .collect(java.util.stream.Collectors.toList());

        return columnRepository.saveAll(columns);
    }

    record DefaultColumnDef(
        String name,
        String color,
        int sortOrder,
        boolean isDefault,
        TaskStatus mappedStatus
    ) {}
}
