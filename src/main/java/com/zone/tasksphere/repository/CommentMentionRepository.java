package com.zone.tasksphere.repository;

import com.zone.tasksphere.entity.CommentMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentMentionRepository extends JpaRepository<CommentMention, UUID> {

    List<CommentMention> findByCommentId(UUID commentId);

    void deleteByCommentId(UUID commentId);
}
