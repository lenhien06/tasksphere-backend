package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.ExportJobStatusResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.ExportService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "13. Export", description = "Xuất dữ liệu task ra Excel/PDF. Sync (≤1000 rows) hoặc Async Job.")
@SecurityRequirement(name = "bearerAuth")
public class ExportController {

    private final ExportService exportService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    @Operation(
        summary = "Export task list (GET — chuẩn spec FR-48)",
        description = """
            Xuất task list ra **Excel (.xlsx)** hoặc **PDF**.

            | Số rows | Luồng |
            |---------|-------|
            | ≤ 1 000 | **Sync** — HTTP 200 + file bytes |
            | > 1 000 | **Async** — HTTP 202 + `jobId` để polling |

            **format:** `xlsx` hoặc `pdf`
            **scope:** `all` (toàn project) hoặc `sprint` (cần kèm `sprintId`)

            **Phân quyền:** PROJECT_MANAGER, MEMBER được export. VIEWER → 403.

            **File tự động xóa sau 24 giờ.**
            """,
        parameters = {
            @Parameter(name = "format",   description = "xlsx | pdf",    required = true),
            @Parameter(name = "scope",    description = "all | sprint",  required = true),
            @Parameter(name = "sprintId", description = "UUID sprint — bắt buộc khi scope=sprint")
        }
    )
    @GetMapping("/projects/{projectId}/export")
    public ResponseEntity<?> getExport(
            @PathVariable UUID projectId,
            @RequestParam String format,
            @RequestParam(defaultValue = "all") String scope,
            @RequestParam(required = false) String sprintId) {

        // Validate scope=sprint requires sprintId
        if ("sprint".equalsIgnoreCase(scope) && (sprintId == null || sprintId.isBlank())) {
            throw new BadRequestException("sprintId là bắt buộc khi scope=sprint");
        }

        // Map spec param values → internal enum names
        String internalFormat = switch (format.toLowerCase()) {
            case "xlsx" -> "EXCEL";
            case "pdf"  -> "PDF";
            default     -> format.toUpperCase();    // pass-through (EXCEL/PDF still accepted)
        };
        String internalScope = switch (scope.toLowerCase()) {
            case "all"    -> "ALL";
            case "sprint" -> "SPRINT";
            default       -> scope.toUpperCase();
        };

        return exportService.createExportJob(
                projectId, internalFormat, internalScope, sprintId, getCurrentUserId());
    }

    @Operation(
        summary = "Tạo export (POST — legacy)",
        description = """
            **≤ 1000 rows:** Sync — trả file trực tiếp (HTTP 200 + file bytes).
            **> 1000 rows:** Async — tạo background job, trả HTTP 202 + jobId.

            **Polling:** GET /export/jobs/{jobId}/status để theo dõi tiến trình.
            **Notify:** WebSocket `export.completed` khi job hoàn thành.

            **Excel (.xlsx):** Dùng Apache POI — bao gồm Custom Fields.
            **PDF:** Dùng iText — layout tóm tắt (không bao gồm Custom Fields).

            **File tự động xóa sau 24 giờ.**
            """,
        parameters = {
            @Parameter(name = "format",   description = "EXCEL | PDF", required = true),
            @Parameter(name = "scope",    description = "ALL | FILTERED | SPRINT (default: ALL)"),
            @Parameter(name = "sprintId", description = "UUID sprint (required nếu scope=SPRINT)")
        }
    )
    @PostMapping("/projects/{projectId}/export")
    public ResponseEntity<?> createExport(
            @PathVariable UUID projectId,
            @RequestParam String format,
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) String sprintId) {
        return exportService.createExportJob(projectId, format, scope, sprintId, getCurrentUserId());
    }

    @Operation(
        summary = "Kiểm tra trạng thái export job",
        description = """
            Poll status của async export job.

            **Status:** PENDING → PROCESSING → DONE | FAILED | EXPIRED

            Khi DONE: downloadUrl có presigned URL TTL 24h.
            Khi EXPIRED (sau 24h): HTTP 410 Gone.
            """
    )
    @GetMapping("/export/jobs/{jobId}/status")
    public ResponseEntity<ApiResponse<ExportJobStatusResponse>> getJobStatus(@PathVariable UUID jobId) {
        ExportJobStatusResponse response = exportService.getJobStatus(jobId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Download file export",
        description = """
            **HTTP 302 Redirect** đến presigned download URL.

            **HTTP 409:** Job chưa DONE.
            **HTTP 410 Gone:** File đã hết hạn (sau 24h).
            """
    )
    @GetMapping("/export/jobs/{jobId}/download")
    public ResponseEntity<?> downloadExport(@PathVariable UUID jobId) {
        return exportService.getDownloadResponse(jobId, getCurrentUserId());
    }
}
