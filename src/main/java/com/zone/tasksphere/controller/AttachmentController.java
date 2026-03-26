package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.AttachmentResponse;
import com.zone.tasksphere.dto.response.PreviewUrlResponse;
import com.zone.tasksphere.dto.response.UploadJobResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.AttachmentService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "7. Attachments", description = "Upload/Download file đính kèm với xử lý nền sau khi upload.")
@SecurityRequirement(name = "bearerAuth")
public class AttachmentController {

    private final AttachmentService attachmentService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    @Operation(
        summary = "Upload file đính kèm (Async)",
        description = """
            **BR-26:** Validate file trước khi xử lý:
            - Max 25MB (HTTP 413 nếu vượt)
            - Whitelist MIME types (HTTP 415 nếu sai)
            - Magic bytes validation (Apache Tika) — phát hiện file giả mạo

            **Async Processing:**
            1. Nhận file → trả về ngay **202 Accepted** + jobId
            2. Background: xử lý file và hoàn tất upload
            3. Tạo Attachment record
            4. Notify qua WebSocket: `attachment.uploaded` hoặc `attachment.scan_failed`

            **Polling:** GET /attachments/jobs/{jobId} để kiểm tra tiến trình.
            """,
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "File đang xử lý — trả về jobId"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "File vượt quá 25MB"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "415", description = "Định dạng file không được phép")
        }
    )
    @PostMapping("/projects/{projectId}/tasks/{taskId}/attachments")
    public ResponseEntity<ApiResponse<UploadJobResponse>> uploadAttachment(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @RequestParam("file") MultipartFile file) {
        UploadJobResponse response = attachmentService.initiateAsyncUpload(projectId, taskId, file, getCurrentUserId());
        return ResponseEntity.accepted().body(ApiResponse.success(response));
    }

    @Operation(summary = "Upload attachment cho comment", description = "Upload trực tiếp file gắn vào 1 comment cụ thể.")
    @PostMapping("/projects/{projectId}/tasks/{taskId}/comments/{commentId}/attachments")
    public ResponseEntity<ApiResponse<AttachmentResponse>> uploadCommentAttachment(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @PathVariable UUID commentId,
            @RequestParam("file") MultipartFile file) {
        AttachmentResponse response = attachmentService.uploadCommentAttachment(
                projectId, taskId, commentId, file, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Kiểm tra trạng thái upload job",
        description = """
            Polling để kiểm tra tiến trình xử lý upload.

            **Status flow:** PENDING → SCANNING → DONE | FAILED

            Khi DONE → response bao gồm AttachmentResponse đầy đủ.
            Khi FAILED → errorMessage mô tả lý do thất bại.
            """
    )
    @GetMapping("/attachments/jobs/{jobId}")
    public ResponseEntity<ApiResponse<UploadJobResponse>> getJobStatus(@PathVariable UUID jobId) {
        UploadJobResponse response = attachmentService.getJobStatus(jobId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Lấy danh sách file đính kèm",
        description = """
            Trả về danh sách file với presigned download URL (TTL 1 giờ).
            URL được gen mới mỗi lần gọi — không cache trên BE.

            **previewable:** true nếu image/* hoặc application/pdf.
            **previewUrl:** Presigned URL TTL 15 phút (ngắn hơn download).
            """
    )
    @GetMapping("/projects/{projectId}/tasks/{taskId}/attachments")
    public ResponseEntity<ApiResponse<List<AttachmentResponse>>> getAttachments(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId) {
        List<AttachmentResponse> response = attachmentService.getAttachments(projectId, taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy danh sách attachment của comment")
    @GetMapping("/projects/{projectId}/tasks/{taskId}/comments/{commentId}/attachments")
    public ResponseEntity<ApiResponse<List<AttachmentResponse>>> getCommentAttachments(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @PathVariable UUID commentId) {
        List<AttachmentResponse> response = attachmentService.getCommentAttachments(
                projectId, taskId, commentId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Lấy presigned URL preview",
        description = """
            Gen URL preview với TTL 15 phút.
            Chỉ hoạt động với file image/* và application/pdf.

            **HTTP 422:** Nếu file không hỗ trợ preview.
            """
    )
    @GetMapping("/attachments/{attachmentId}/preview-url")
    public ResponseEntity<ApiResponse<PreviewUrlResponse>> getPreviewUrl(@PathVariable UUID attachmentId) {
        PreviewUrlResponse previewUrl = attachmentService.getPreviewUrlOnly(attachmentId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(previewUrl));
    }

    @Operation(
        summary = "Xóa file đính kèm",
        description = """
            **Quyền:** Người upload HOẶC PM.

            **2 bước:**
            1. Soft delete record trong DB
            2. Xóa file thật trên MinIO/S3 (async)
            """
    )
    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(@PathVariable UUID attachmentId) {
        attachmentService.deleteAttachment(attachmentId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }
}
