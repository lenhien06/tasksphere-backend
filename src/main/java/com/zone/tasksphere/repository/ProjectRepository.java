package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.enums.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    @Query("SELECT COUNT(p) FROM Project p WHERE p.deletedAt IS NULL")
    long countAllNotDeleted();

    @Query("SELECT COUNT(p) FROM Project p WHERE p.status = com.zone.tasksphere.entity.enums.ProjectStatus.ACTIVE AND p.deletedAt IS NULL")
    long countActiveProjects();

    boolean existsByProjectKey(String projectKey);

    boolean existsByName(String name);

    Optional<Project> findByProjectKey(String projectKey);

    @Query("SELECT p FROM Project p WHERE p.id = :id")
    Optional<Project> findByIdWithDeleted(@Param("id") UUID id);

    @Query("SELECT p FROM Project p WHERE p.projectKey = :key")
    Optional<Project> findByKeyWithDeleted(@Param("key") String key);

    @Query("""
        SELECT DISTINCT p FROM Project p
        LEFT JOIN p.members pm
        WHERE p.deletedAt IS NULL
          AND (p.owner.id = :userId OR pm.user.id = :userId)
        ORDER BY p.updatedAt DESC
    """)
    List<Project> findOwnedOrMemberProjects(@Param("userId") UUID userId);

    @Query("""
        SELECT DISTINCT p FROM Project p
        LEFT JOIN p.members pm
        WHERE p.deletedAt IS NULL
          AND p.status = :status
          AND (p.owner.id = :userId OR pm.user.id = :userId)
        ORDER BY p.updatedAt DESC
    """)
    List<Project> findOwnedOrMemberProjectsByStatus(@Param("userId") UUID userId,
                                                    @Param("status") ProjectStatus status);

    /**
     * Pessimistic write lock — dùng cho atomic task code generation.
     * SELECT ... FOR UPDATE đảm bảo chỉ 1 thread tăng taskCounter tại một thời điểm.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :id AND p.deletedAt IS NULL")
    Optional<Project> findByIdWithLock(@Param("id") UUID id);

    /** Tầng 2: Tìm project chưa có column nào — dùng cho migration runner */
    @Query("""
        SELECT p FROM Project p
        WHERE NOT EXISTS (
            SELECT sc FROM ProjectStatusColumn sc
            WHERE sc.project = p
              AND sc.deletedAt IS NULL
        )
    """)
    List<Project> findProjectsWithoutColumns();
}
