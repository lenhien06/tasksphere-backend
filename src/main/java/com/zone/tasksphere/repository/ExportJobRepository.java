package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.ExportJob;
import com.zone.tasksphere.entity.enums.ExportJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {

    List<ExportJob> findByStatusAndExpiresAtBefore(ExportJobStatus status, Instant now);
}
