package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.CreateChecklistItemRequest;
import com.zone.tasksphere.dto.request.ReorderChecklistRequest;
import com.zone.tasksphere.dto.request.UpdateChecklistItemRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.ChecklistItemResponse;
import com.zone.tasksphere.dto.response.ChecklistSummary;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.ChecklistService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "5. Checklist", description = "Quản lý danh sách việc nhỏ trong task. completedBy và completedAt tự động set khi tick.")
@SecurityRequirement(name = "bearerAuth")
public class ChecklistController {

    private final ChecklistService checklistService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    @Operation(
        summary = "Lấy checklist của task",
        description = """
            Trả về checklist với summary (total/completed) và danh sách items.
            Sort theo sortOrder ASC.

            **Progress bar:** FE tính = completed/total * 100.
            """
    )
    @GetMapping({"/tasks/{taskId}/checklist", "/projects/{projectId}/tasks/{taskId}/checklists"})
    public ResponseEntity<ApiResponse<ChecklistSummary>> getChecklist(
            @PathVariable UUID taskId,
            @PathVariable(required = false) UUID projectId) {
        ChecklistSummary response = checklistService.getChecklist(taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Thêm checklist item",
        description = """
            Thêm 1 item mới vào cuối checklist.
            sortOrder = MAX(sortOrder) + 1 trong task đó.
            """
    )
    @PostMapping({"/tasks/{taskId}/checklist", "/projects/{projectId}/tasks/{taskId}/checklists"})
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> addChecklistItem(
            @PathVariable UUID taskId,
            @PathVariable(required = false) UUID projectId,
            @Valid @RequestBody CreateChecklistItemRequest request) {
        ChecklistItemResponse response = checklistService.addItem(taskId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(
        summary = "Tick/Untick hoặc đổi tên item",
        description = """
            **Khi isDone = true:**
            - completedBy = currentUser
            - completedAt = NOW()

            **Khi isDone = false:**
            - completedBy = NULL
            - completedAt = NULL

            Cho phép vừa đổi tên vừa tick trong 1 request.
            """
    )
    @PatchMapping("/checklist/{itemId}")
    public ResponseEntity<ApiResponse<ChecklistItemResponse>> updateChecklistItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateChecklistItemRequest request) {
        ChecklistItemResponse response = checklistService.updateItem(itemId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Xóa checklist item",
        description = "Soft delete item. Không ảnh hưởng đến các item khác."
    )
    @DeleteMapping("/checklist/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteChecklistItem(@PathVariable UUID itemId) {
        checklistService.deleteItem(itemId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }

    @Operation(
        summary = "Sắp xếp lại thứ tự checklist",
        description = """
            Gửi danh sách orderedIds theo thứ tự mới.
            Backend cập nhật sortOrder theo index trong mảng.

            **Dùng cho:** Drag & drop reorder checklist items.
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "{ orderedIds: [\"id1\", \"id2\", \"id3\"] }"
        )
    )
    @PatchMapping("/tasks/{taskId}/checklist/reorder")
    public ResponseEntity<ApiResponse<Void>> reorderChecklist(
            @PathVariable UUID taskId,
            @Valid @RequestBody ReorderChecklistRequest request) {
        checklistService.reorder(taskId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }
}
