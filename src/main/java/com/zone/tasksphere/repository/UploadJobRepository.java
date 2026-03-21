package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.UploadJob;
import com.zone.tasksphere.entity.enums.UploadJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UploadJobRepository extends JpaRepository<UploadJob, UUID> {
    List<UploadJob> findByStatus(UploadJobStatus status);
}
