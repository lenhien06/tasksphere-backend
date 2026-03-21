package com.zone.tasksphere.config;

import com.zone.tasksphere.entity.Comment;
import com.zone.tasksphere.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * FR-22 spec 96: Flatten reply depth — only 1 level allowed.
 * Migrates existing comments: replies at depth 2+ get parentId reassigned to root.
 */
@Component
@Profile("!test")
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class CommentThreadMigration implements CommandLineRunner {

    private final CommentRepository commentRepository;

    @Override
    @Transactional
    public void run(String... args) {
        List<Comment> nested = commentRepository.findByDepthGreaterThanEqual(2);
        if (nested.isEmpty()) return;

        log.info("[CommentThreadMigration] Flattening {} reply(ies) at depth 2+ to root", nested.size());
        for (Comment c : nested) {
            Comment root = findRoot(c);
            if (root == null || root.getId().equals(c.getParentComment().getId())) continue;
            c.setParentComment(root);
            c.setDepth(1);
            commentRepository.save(c);
        }
        log.info("[CommentThreadMigration] Done.");
    }

    private Comment findRoot(Comment comment) {
        Comment curr = comment;
        while (curr.getParentComment() != null) {
            curr = curr.getParentComment();
        }
        return curr;
    }
}
