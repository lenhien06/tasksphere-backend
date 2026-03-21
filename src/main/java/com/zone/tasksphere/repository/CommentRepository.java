package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.Comment;
import com.zone.tasksphere.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /** Root comments only (paginated) */
    Page<Comment> findByTaskIdAndParentCommentIsNull(UUID taskId, Pageable pageable);

    /** Direct children of a comment, sorted oldest first */
    List<Comment> findByParentCommentIdOrderByCreatedAtAsc(UUID parentCommentId);

    /** Comments at depth >= 2 (reply of reply) — for migration to flatten */
    List<Comment> findByDepthGreaterThanEqual(int depth);

    long countByTaskId(UUID taskId);

    /** @deprecated use findByTaskIdAndParentCommentIsNull with Pageable */
    @Deprecated
    List<Comment> findByTaskAndParentCommentIsNullOrderByCreatedAtAsc(Task task);
}
