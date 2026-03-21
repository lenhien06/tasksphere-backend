package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.SavedFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SavedFilterRepository extends JpaRepository<SavedFilter, UUID> {

    /** Lấy danh sách filter của user trong project (auto-filter soft-deleted) */
    List<SavedFilter> findByProjectIdAndCreatedByIdOrderByCreatedAtDesc(UUID projectId, UUID userId);

    /** Đếm filter của user trong project (để giới hạn tối đa 10) */
    long countByProjectIdAndCreatedById(UUID projectId, UUID userId);
}
