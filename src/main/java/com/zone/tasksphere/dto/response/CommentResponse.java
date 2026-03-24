package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Comment Response")
public class CommentResponse {

    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Author")
    private UserSummary author;
    @Schema(description = "Content (HTML or Markdown)", example = "string")
    private String content;
    @Schema(description = "Parent comment id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID parentId;
    @Schema(description = "Depth in thread: 0=root, 1=reply (1 level only per FR-22)", example = "0")
    private int depth;
    @Schema(description = "Is edited", example = "true")
    private boolean isEdited;
    @Schema(description = "Mentioned users", example = "[]")
    private List<UserSummary> mentionedUsers;
    @Schema(description = "Attachments of this comment", example = "[]")
    private List<AttachmentResponse> attachments;
    @Schema(description = "Replies (1 level only — no nested replies)", example = "[]")
    private List<CommentResponse> replies;
    @Schema(description = "Current user can edit this comment", example = "true")
    private boolean canEdit;
    @Schema(description = "Current user can delete this comment", example = "false")
    private boolean canDelete;
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;
    @Schema(description = "Updated at", example = "2023-12-31T23:59:59Z")
    private Instant updatedAt;

    @Data
    @Builder
@Schema(description = "User Summary")
public static class UserSummary {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Full name", example = "John Doe")
        private String fullName;
        @Schema(description = "Avatar url", example = "https://example.com/image.png")
        private String avatarUrl;
    }
}
