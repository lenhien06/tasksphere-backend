package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    List<Attachment> findByTaskIdAndCommentIsNullOrderByCreatedAtDesc(UUID taskId);
    List<Attachment> findByCommentIdOrderByCreatedAtDesc(UUID commentId);

    long countByTaskIdAndCommentIsNull(UUID taskId);
    long countByCommentId(UUID commentId);
}
