package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.CreateColumnRequest;
import com.zone.tasksphere.dto.request.ReorderColumnsRequest;
import com.zone.tasksphere.dto.request.UpdateColumnRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.ColumnResponse;
import com.zone.tasksphere.dto.response.DeleteColumnResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.ColumnService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "3. Project Management", description = "Quản lý dự án — tạo, sửa, archive, quản lý thành viên.")
@SecurityRequirement(name = "bearerAuth")
public class ColumnController {

    private final ColumnService columnService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    @Operation(summary = "Thêm cột Kanban mới cho dự án")
    @PostMapping("/projects/{projectId}/columns")
    public ResponseEntity<ApiResponse<ColumnResponse>> createColumn(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateColumnRequest request) {
        ColumnResponse response = columnService.createColumn(projectId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Cập nhật thông tin cột")
    @PutMapping("/columns/{columnId}")
    public ResponseEntity<ApiResponse<ColumnResponse>> updateColumn(
            @PathVariable UUID columnId,
            @Valid @RequestBody UpdateColumnRequest request) {
        ColumnResponse response = columnService.updateColumn(columnId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Xóa cột",
               description = "Xóa cột Kanban. Nếu có task trong cột, task sẽ được chuyển về cột mặc định đầu tiên.")
    @DeleteMapping("/columns/{columnId}")
    public ResponseEntity<ApiResponse<DeleteColumnResponse>> deleteColumn(@PathVariable UUID columnId) {
        DeleteColumnResponse response = columnService.deleteColumn(columnId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Sắp xếp lại thứ tự cột")
    @PatchMapping("/projects/{projectId}/columns/reorder")
    public ResponseEntity<ApiResponse<List<ColumnResponse>>> reorderColumns(
            @PathVariable UUID projectId,
            @Valid @RequestBody ReorderColumnsRequest request) {
        List<ColumnResponse> response = columnService.reorderColumns(projectId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
