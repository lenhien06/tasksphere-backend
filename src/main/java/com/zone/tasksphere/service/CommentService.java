package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.CreateCommentRequest;
import com.zone.tasksphere.dto.request.UpdateCommentRequest;
import com.zone.tasksphere.dto.response.CommentResponse;
import com.zone.tasksphere.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CommentService {

    CommentResponse addComment(UUID projectId, UUID taskId, CreateCommentRequest request, UUID currentUserId);

    PageResponse<CommentResponse> getComments(UUID projectId, UUID taskId, Pageable pageable, UUID currentUserId);

    CommentResponse updateComment(UUID commentId, UpdateCommentRequest request, UUID currentUserId);

    void deleteComment(UUID commentId, UUID currentUserId);
}
