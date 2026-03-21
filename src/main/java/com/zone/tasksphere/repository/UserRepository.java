package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE " +
           "(:keyword IS NULL OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:roleId IS NULL OR u.role.id = :roleId) AND " +
           "(:status IS NULL OR u.status = :status)")
    Page<User> findAllWithFilter(
            @Param("keyword") String keyword,
            @Param("roleId") Long roleId,
            @Param("status") UserStatus status,
            Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deletedAt IS NULL")
    long countAllActiveNotDeleted();

    long countByStatus(UserStatus status);

    @Query("""
        SELECT u FROM User u
        JOIN ProjectMember pm ON pm.user = u
        WHERE pm.project.id = :projectId
          AND pm.deletedAt IS NULL
          AND u.deletedAt IS NULL
          AND (LOWER(u.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
        ORDER BY u.fullName ASC
        LIMIT 10
    """)
    List<User> searchProjectMembers(
        @Param("projectId") UUID projectId,
        @Param("q") String q);

    // ── P6-BE-04: Daily Digest ────────────────────────────────────────────────

    @Query("""
        SELECT u FROM User u
        WHERE u.emailDailyDigest = true
          AND u.status = com.zone.tasksphere.entity.enums.UserStatus.ACTIVE
          AND u.deletedAt IS NULL
    """)
    List<User> findDigestEligibleUsers();
}
