package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.ProjectView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectViewRepository extends JpaRepository<ProjectView, UUID> {
    Optional<ProjectView> findByProjectIdAndUserId(UUID projectId, UUID userId);
}
