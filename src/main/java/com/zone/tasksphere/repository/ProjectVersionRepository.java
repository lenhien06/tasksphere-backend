package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.ProjectVersion;
import com.zone.tasksphere.entity.enums.VersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectVersionRepository extends JpaRepository<ProjectVersion, UUID> {

    List<ProjectVersion> findByProject_IdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID projectId);

    Optional<ProjectVersion> findByIdAndProject_IdAndDeletedAtIsNull(UUID id, UUID projectId);

    /** Kiểm tra tên version unique trong project */
    boolean existsByProject_IdAndNameAndDeletedAtIsNull(UUID projectId, String name);

    /** Tìm version theo project + name (kể cả deleted) */
    Optional<ProjectVersion> findByProject_IdAndName(UUID projectId, String name);

    /** Kiểm tra tên unique khi update (exclude chính nó) */
    boolean existsByProject_IdAndNameAndIdNotAndDeletedAtIsNull(UUID projectId, String name, UUID excludeId);

    /** Tổng số task trong version */
    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.projectVersion.id = :versionId AND t.deletedAt IS NULL
    """)
    long countTasksByVersionId(@Param("versionId") UUID versionId);

    /** Số task DONE trong version */
    @Query("""
        SELECT COUNT(t) FROM Task t
        WHERE t.projectVersion.id = :versionId
          AND t.taskStatus = com.zone.tasksphere.entity.enums.TaskStatus.DONE
          AND t.deletedAt IS NULL
    """)
    long countDoneTasksByVersionId(@Param("versionId") UUID versionId);
}
