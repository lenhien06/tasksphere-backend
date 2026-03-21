package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectStatusColumn;
import com.zone.tasksphere.entity.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectStatusColumnRepository extends JpaRepository<ProjectStatusColumn, UUID> {

    List<ProjectStatusColumn> findByProjectOrderBySortOrderAsc(Project project);

    Optional<ProjectStatusColumn> findFirstByProjectOrderBySortOrderAsc(Project project);

    boolean existsByProjectAndName(Project project, String name);

    /** Tầng 1: Dùng để guard trong DefaultColumnSeeder */
    boolean existsByProject(Project project);

    /** Tầng 4: Dùng trong endpoint GET /columns — lấy by UUID để không cần load Project */
    List<ProjectStatusColumn> findByProjectIdOrderBySortOrderAsc(UUID projectId);

    // ── P3-BE-04: Custom Columns CRUD ─────────────────────────────────

    /** Kiểm tra tên cột trùng trong project (case-insensitive), auto-filter soft-deleted */
    boolean existsByProjectIdAndNameIgnoreCase(UUID projectId, String name);

    /** Kiểm tra tên cột trùng nhưng loại trừ cột hiện tại (dùng khi update) */
    @Query("SELECT COUNT(c) > 0 FROM ProjectStatusColumn c " +
           "WHERE c.project.id = :projectId AND LOWER(c.name) = LOWER(:name) AND c.id != :excludeId")
    boolean existsByProjectIdAndNameIgnoreCaseExcludingId(
            @Param("projectId") UUID projectId,
            @Param("name") String name,
            @Param("excludeId") UUID excludeId);

    /** Lấy sortOrder lớn nhất trong project (dùng khi tạo cột mới) */
    @Query("SELECT MAX(c.sortOrder) FROM ProjectStatusColumn c WHERE c.project.id = :projectId")
    Optional<Integer> findMaxSortOrderByProjectId(@Param("projectId") UUID projectId);

    /** Tìm cột mặc định theo mappedStatus (dùng để tìm cột ToDo khi xóa cột) */
    Optional<ProjectStatusColumn> findFirstByProjectIdAndMappedStatusAndIsDefaultTrue(
            UUID projectId, TaskStatus mappedStatus);

    /** FIX: BR-29 - Đếm số cột có cùng mappedStatus trong project (để bảo vệ cột START/DONE cuối cùng) */
    long countByProjectIdAndMappedStatus(UUID projectId, TaskStatus mappedStatus);
}
