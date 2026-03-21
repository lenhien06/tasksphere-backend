package com.zone.tasksphere.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.List;

/**
 * Rich-text comment on a task, supporting threaded replies via parentComment.
 */
@Entity
@Table(name = "comments")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Comment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @NotBlank
    @Column(nullable = false,
            columnDefinition = "LONGTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    private String content;

    @Column(name = "is_edited", nullable = false)
    @Builder.Default
    private boolean isEdited = false;

    @Column(name = "edited_at")
    private Instant editedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private Comment parentComment;

    /**
     * Depth in the comment tree: 0 = root, 1 = reply (FR-22 spec 96: only 1 level of reply).
     */
    @Column(name = "depth", nullable = false)
    @Builder.Default
    private int depth = 0;

    @OneToMany(mappedBy = "comment", fetch = FetchType.LAZY)
    private List<CommentMention> mentions;
}
