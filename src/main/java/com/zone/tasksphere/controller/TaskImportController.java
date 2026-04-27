package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.TaskImportResultResponse;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.security.CustomUserDetail;
import com.zone.tasksphere.service.TaskImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks")
@RequiredArgsConstructor
@Tag(name = "4. Task Management", description = "Import tasks từ file Excel.")
@SecurityRequirement(name = "bearerAuth")
public class TaskImportController {

    private final TaskImportService taskImportService;

    @Operation(
        summary = "Tải file template import task",
        description = """
            Trả về file .xlsx mẫu để người dùng điền dữ liệu và import.

            File template bao gồm:
            - Dropdown cho cột Type (TASK, BUG, FEATURE, STORY, EPIC)
            - Dropdown cho cột Priority (LOW, MEDIUM, HIGH, CRITICAL)
            - Validation cho StoryPoints (1–100) và EstimatedHours (>= 0)
            - 3 dòng ví dụ minh hoạ
            - Tooltip hướng dẫn trên mỗi cột header
            """
    )
    @GetMapping("/import-template")
    public ResponseEntity<byte[]> getImportTemplate(@PathVariable UUID projectId) throws IOException {
        byte[] bytes = taskImportService.generateTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        ));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"task-import-template.xlsx\"");
        headers.setContentLength(bytes.length);
        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    @Operation(
        summary = "Import tasks từ file Excel",
        description = """
            Upload file .xlsx chứa danh sách task cần import.

            **Luồng xử lý:**
            1. Validate toàn bộ file trước khi import
            2. Nếu có lỗi → trả 422 với danh sách lỗi chi tiết theo từng dòng
            3. Nếu hợp lệ → tạo tất cả task, trả 200 với kết quả

            **Quyền:** PM hoặc MEMBER (VIEWER không được phép)

            **Giới hạn:** Chỉ nhận file .xlsx, tối đa 5MB
            """
    )
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TaskImportResultResponse>> importTasks(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file) throws IOException {

        TaskImportResultResponse result = taskImportService.importTasks(projectId, file, getCurrentUserId());

        if (result.getErrors().isEmpty()) {
            String msg = "Import thành công " + result.getCreatedCount() + " task";
            return ResponseEntity.ok(ApiResponse.success(result, msg));
        }
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiResponse.success(result));
    }

    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new CustomAuthenticationException("Chưa đăng nhập hoặc phiên làm việc hết hạn");
        }
        CustomUserDetail userDetail = (CustomUserDetail) auth.getPrincipal();
        return userDetail.getUserDetail().getId();
    }
}
