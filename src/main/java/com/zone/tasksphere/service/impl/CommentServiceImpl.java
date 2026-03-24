package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.CreateCommentRequest;
import com.zone.tasksphere.dto.request.UpdateCommentRequest;
import com.zone.tasksphere.dto.response.CommentResponse;
import com.zone.tasksphere.dto.response.PageResponse;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.EntityType;
import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.*;
import com.zone.tasksphere.service.CommentService;
import com.zone.tasksphere.service.NotificationService;
import com.zone.tasksphere.service.ActivityLogService;
import com.zone.tasksphere.service.WebSocketService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private static final Safelist COMMENT_SAFELIST = Safelist.relaxed()
        .addAttributes("span", "data-mention-id", "data-mention-name", "class")
        .addAttributes("code", "class")
        .addAttributes("p", "class")
        .addAttributes("a", "href", "title", "target");

    private final CommentRepository commentRepository;
    private final CommentMentionRepository commentMentionRepository;
    private final AttachmentRepository attachmentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final WebSocketService webSocketService;
    private final MinioStorageService minioStorageService;

    /** FR-22 spec 96: Only 1 level of reply. depth 0=root, 1=reply (no nesting) */
    private static final int MAX_COMMENT_DEPTH = 1;

    @Override
    public CommentResponse addComment(UUID projectId, UUID taskId, CreateCommentRequest request, UUID currentUserId) {
        Task task = getTaskInProject(taskId, projectId);
        User author = getUser(currentUserId);
        validateMembership(projectId, currentUserId);

        Comment parentComment = null;
        int newDepth = 0;
        if (request.getParentId() != null) {
            parentComment = commentRepository.findById(request.getParentId())
                .orElseThrow(() -> new NotFoundException("Comment không tồn tại"));
            if (!parentComment.getTask().getId().equals(taskId)) {
                throw new BadRequestException("Comment không thuộc task này");
            }
            if (parentComment.getParentComment() != null) {
                throw new BadRequestException(
                    "Chỉ được reply trực tiếp vào comment gốc, không được reply lồng tiếp");
            }
            newDepth = 1;
        }

        // Sanitize HTML content (XSS protection)
        String sanitized = Jsoup.clean(request.getContent(), COMMENT_SAFELIST);

        Comment comment = Comment.builder()
            .task(task)
            .author(author)
            .content(sanitized)
            .parentComment(parentComment)
            .depth(newDepth)
            .isEdited(false)
            .build();

        comment = commentRepository.save(comment);

        // Parse @mention from data-mention-id HTML attributes (TipTap format)
        List<User> mentionedUsers = parseMentionsFromHtml(sanitized, projectId);
        saveMentions(comment, mentionedUsers);

        // Notify mentioned users
        notificationService.sendMentionNotification(mentionedUsers, task, comment, author);

        // Notify task assignee/reporter if they're different from author
        if (task.getAssignee() != null && !task.getAssignee().getId().equals(currentUserId)) {
            notificationService.sendTaskCommented(task, task.getAssignee(), author);
        }

        String plainText = sanitized == null ? "" : Jsoup.parse(sanitized).text();
        logActivity(projectId, currentUserId, EntityType.COMMENT, comment.getId(), ActionType.COMMENT_ADDED, null,
                toActivityJson("content", plainText));

        boolean isPM = projectMemberRepository.findByProjectIdAndUserId(projectId, currentUserId)
            .map(m -> m.getProjectRole() == com.zone.tasksphere.entity.enums.ProjectRole.PROJECT_MANAGER)
            .orElse(false);
        CommentResponse response = buildCommentTree(comment, currentUserId, isPM);
        webSocketService.sendToProject(projectId.toString(), "comment.created", response);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> getComments(UUID projectId, UUID taskId, Pageable pageable, UUID currentUserId) {
        getTaskInProject(taskId, projectId);
        validateMembership(projectId, currentUserId);

        boolean isPM = projectMemberRepository.findByProjectIdAndUserId(projectId, currentUserId)
            .map(m -> m.getProjectRole() == com.zone.tasksphere.entity.enums.ProjectRole.PROJECT_MANAGER)
            .orElse(false);

        // Only fetch root-level comments with pagination; replies are loaded recursively
        Page<Comment> page = commentRepository.findByTaskIdAndParentCommentIsNull(taskId, pageable);

        return PageResponse.fromPage(page.map(c -> buildCommentTree(c, currentUserId, isPM)));
    }

    @Override
    public CommentResponse updateComment(UUID commentId, UpdateCommentRequest request, UUID currentUserId) {
        Comment comment = getComment(commentId);

        if (!comment.getAuthor().getId().equals(currentUserId)) {
            throw new Forbidden("Chỉ tác giả mới được sửa bình luận");
        }

        // FR-22: Can only edit within 24 hours of creation
        if (comment.getCreatedAt() != null
                && comment.getCreatedAt().isBefore(Instant.now().minus(Duration.ofHours(24)))) {
            throw new Forbidden("Chỉ được sửa bình luận trong vòng 24 giờ sau khi tạo (FR-22)");
        }

        // Sanitize HTML content
        String sanitized = Jsoup.clean(request.getContent(), COMMENT_SAFELIST);

        comment.setContent(sanitized);
        comment.setEdited(true);
        comment.setEditedAt(Instant.now());

        // Re-parse mentions from updated content
        UUID projectId = comment.getTask().getProject().getId();
        Set<UUID> previousMentionUserIds = commentMentionRepository.findByCommentId(commentId).stream()
                .map(m -> m.getMentionedUser().getId())
                .collect(Collectors.toSet());

        List<User> mentionedUsers = parseMentionsFromHtml(sanitized, projectId);
        commentMentionRepository.deleteByCommentId(commentId);
        saveMentions(comment, mentionedUsers);

        comment = commentRepository.save(comment);

        List<User> newlyMentioned = mentionedUsers.stream()
                .filter(u -> !previousMentionUserIds.contains(u.getId()))
                .toList();
        if (!newlyMentioned.isEmpty()) {
            notificationService.sendMentionNotification(newlyMentioned, comment.getTask(), comment, comment.getAuthor());
        }

        boolean isPM = projectMemberRepository.findByProjectIdAndUserId(projectId, currentUserId)
            .map(m -> m.getProjectRole() == com.zone.tasksphere.entity.enums.ProjectRole.PROJECT_MANAGER)
            .orElse(false);
        return buildCommentTree(comment, currentUserId, isPM);
    }

    @Override
    public void deleteComment(UUID commentId, UUID currentUserId) {
        Comment comment = getComment(commentId);
        UUID projectId = comment.getTask().getProject().getId();

        boolean isAuthor = comment.getAuthor().getId().equals(currentUserId);
        boolean isPM = projectMemberRepository.findByProjectIdAndUserId(projectId, currentUserId)
            .map(m -> m.getProjectRole() == ProjectRole.PROJECT_MANAGER)
            .orElse(false);

        if (!isAuthor && !isPM) {
            throw new Forbidden("Chỉ tác giả hoặc PM mới được xóa bình luận");
        }

        comment.setDeletedAt(Instant.now());
        commentRepository.save(comment);
        String oldPreview = comment.getContent() == null ? null
            : (comment.getContent().length() <= 50 ? comment.getContent() : comment.getContent().substring(0, 50));
        logActivity(projectId, currentUserId, EntityType.COMMENT, comment.getId(), ActionType.COMMENT_DELETED, oldPreview, null);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private Task getTaskInProject(UUID taskId, UUID projectId) {
        return taskRepository.findByIdAndProjectId(taskId, projectId)
            .orElseThrow(() -> new NotFoundException("Task not found: " + taskId));
    }

    private Comment getComment(UUID commentId) {
        return commentRepository.findById(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    private void validateMembership(UUID projectId, UUID userId) {
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }
    }

    /**
     * Parse @mentions from TipTap/ProseMirror HTML:
     * <span data-mention-id="uuid" data-mention-name="Name">@Name</span>
     * Only includes users who are project members.
     */
    private List<User> parseMentionsFromHtml(String htmlContent, UUID projectId) {
        List<User> mentioned = new ArrayList<>();
        try {
            Document doc = Jsoup.parse(htmlContent);
            Elements mentionSpans = doc.select("[data-mention-id]");

            for (Element el : mentionSpans) {
                String userIdStr = el.attr("data-mention-id");
                try {
                    UUID userId = UUID.fromString(userIdStr);
                    userRepository.findById(userId).ifPresent(user -> {
                        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
                            mentioned.add(user);
                        }
                    });
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid UUID in data-mention-id: {}", userIdStr);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse mentions from HTML: {}", e.getMessage());
        }
        return mentioned;
    }

    private void saveMentions(Comment comment, List<User> users) {
        List<CommentMention> mentions = new ArrayList<>();
        for (User u : users) {
            CommentMention mention = new CommentMention();
            mention.setComment(comment);
            mention.setMentionedUser(u);
            mentions.add(mention);
        }
        commentMentionRepository.saveAll(mentions);
    }

    private void logActivity(UUID projectId, UUID actorId, EntityType entityType,
                              UUID entityId, ActionType action, String oldVal, String newVal) {
        try {
            HttpServletRequest httpRequest = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();
            activityLogService.logActivity(projectId, actorId, entityType, entityId,
                action, oldVal, newVal, httpRequest);
        } catch (Exception e) {
            log.warn("Failed to log activity: {}", e.getMessage());
        }
    }

    private String toActivityJson(String key, String value) {
        // Simple JSON builder — value is plain text (no HTML), safe to escape manually
        String escaped = value == null ? "" : value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
        return "{\"" + key + "\":\"" + escaped + "\"}";
    }

    private CommentResponse buildCommentTree(Comment comment, UUID currentUserId, boolean isPM) {
        // Root has replies; reply has empty replies[] (FR-22 spec 96: 1 level only)
        return buildCommentResponse(comment, currentUserId, isPM, comment.getParentComment() == null);
    }

    private CommentResponse buildCommentResponse(Comment comment, UUID currentUserId, boolean isPM,
                                                  boolean includeReplies) {
        List<CommentResponse> replies = includeReplies
            ? commentRepository.findByParentCommentIdOrderByCreatedAtAsc(comment.getId())
                .stream()
                .map(child -> buildCommentResponse(child, currentUserId, isPM, false))
                .toList()
            : java.util.Collections.emptyList();

        List<User> mentionedUsers = commentMentionRepository.findByCommentId(comment.getId())
            .stream()
            .map(com.zone.tasksphere.entity.CommentMention::getMentionedUser)
            .toList();

        List<CommentResponse.UserSummary> mentionSummaries = mentionedUsers.stream()
            .map(u -> CommentResponse.UserSummary.builder()
                .id(u.getId())
                .fullName(u.getFullName())
                .avatarUrl(u.getAvatarUrl())
                .build())
            .toList();

        List<com.zone.tasksphere.dto.response.AttachmentResponse> attachments = attachmentRepository
            .findByCommentIdOrderByCreatedAtDesc(comment.getId())
            .stream()
            .map(this::toAttachmentResponse)
            .toList();

        boolean canEdit = comment.getAuthor().getId().equals(currentUserId)
            && comment.getCreatedAt() != null
            && comment.getCreatedAt().isAfter(Instant.now().minus(Duration.ofHours(24)));
        boolean canDelete = comment.getAuthor().getId().equals(currentUserId) || isPM;

        User author = comment.getAuthor();
        return CommentResponse.builder()
            .id(comment.getId())
            .author(CommentResponse.UserSummary.builder()
                .id(author.getId())
                .fullName(author.getFullName())
                .avatarUrl(author.getAvatarUrl())
                .build())
            .content(comment.getContent())
            .parentId(comment.getParentComment() != null ? comment.getParentComment().getId() : null)
            .depth(comment.getDepth())
            .isEdited(comment.isEdited())
            .mentionedUsers(mentionSummaries)
            .attachments(attachments)
            .replies(replies)
            .canEdit(canEdit)
            .canDelete(canDelete)
            .createdAt(comment.getCreatedAt())
            .updatedAt(comment.getUpdatedAt())
            .build();
    }

    private com.zone.tasksphere.dto.response.AttachmentResponse toAttachmentResponse(Attachment a) {
        boolean previewable = minioStorageService.isPreviewable(a.getContentType());
        String previewUrl = previewable ? minioStorageService.generatePreviewUrl(a.getS3Key()) : null;
        return com.zone.tasksphere.dto.response.AttachmentResponse.builder()
            .id(a.getId())
            .fileName(a.getOriginalFilename())
            .fileSize(a.getFileSize())
            .mimeType(a.getContentType())
            .downloadUrl(minioStorageService.generateDownloadUrl(a.getS3Key()))
            .previewUrl(previewUrl)
            .previewable(previewable)
            .uploadedBy(com.zone.tasksphere.dto.response.AttachmentResponse.UserSummary.builder()
                .id(a.getUploadedBy().getId())
                .fullName(a.getUploadedBy().getFullName())
                .avatarUrl(a.getUploadedBy().getAvatarUrl())
                .build())
            .uploadedAt(a.getCreatedAt())
            .build();
    }
}
