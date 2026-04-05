package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.*;
import com.zone.tasksphere.dto.response.*;
import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.TaskType;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.security.CustomUserDetail;
import com.zone.tasksphere.service.SprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "9. Sprint Management", description = "Agile Sprint — tạo, bắt đầu, hoàn thành. Chỉ 1 sprint ACTIVE tại 1 thời điểm (BR-19).")
@SecurityRequirement(name = "bearerAuth")
public class SprintController {

    private final SprintService sprintService;

    @Operation(summary = "Tạo Sprint mới",
               description = "PM only. Validate: name unique, dates valid, no overlap (SPR_002, SPR_004).")
    @PostMapping("/api/v1/projects/{projectId}/sprints")
    public ResponseEntity<ApiResponse<SprintDetailResponse>> createSprint(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateSprintRequest request) {
        SprintDetailResponse response = sprintService.createSprint(projectId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Sprint đã được tạo thành công"));
    }

    @Operation(summary = "Cập nhật Sprint", description = "PM only. Không sửa được sprint COMPLETED.")
    @PutMapping("/api/v1/sprints/{sprintId}")
    public ResponseEntity<ApiResponse<SprintDetailResponse>> updateSprint(
            @PathVariable UUID sprintId,
            @Valid @RequestBody UpdateSprintRequest request) {
        SprintDetailResponse response = sprintService.updateSprint(sprintId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Sprint đã được cập nhật"));
    }

    @Operation(summary = "Xóa Sprint (soft delete)",
               description = "PM only. Chỉ xóa được sprint PLANNED. Tasks sẽ về backlog.")
    @DeleteMapping("/api/v1/sprints/{sprintId}")
    public ResponseEntity<ApiResponse<DeleteSprintResponse>> deleteSprint(
            @PathVariable UUID sprintId) {
        DeleteSprintResponse response = sprintService.deleteSprint(sprintId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy danh sách Sprint của dự án",
               description = "All members. Sort: ACTIVE → PLANNED by startDate → COMPLETED by completedAt DESC.")
    @GetMapping("/api/v1/projects/{projectId}/sprints")
    public ResponseEntity<ApiResponse<List<SprintSummaryResponse>>> getSprintsByProject(
            @PathVariable UUID projectId) {
        List<SprintSummaryResponse> response = sprintService.getSprintsByProject(projectId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Bắt đầu Sprint",
        description = """
            **BR-19:** Chỉ được bắt đầu khi KHÔNG có sprint ACTIVE nào khác.
            **Lỗi SPR_003:** 422 nếu đã có sprint đang chạy.
            
            **Sau khi start:**
            - status → ACTIVE, startedAt = NOW()
            - Gửi notification SPRINT_STARTED đến tất cả member
            - Emit WebSocket sprint.started
            - Ghi activity log
            """
    )
    @PatchMapping("/api/v1/sprints/{sprintId}/start")
    public ResponseEntity<ApiResponse<SprintStartedResponse>> startSprint(
            @PathVariable UUID sprintId) {
        SprintStartedResponse response = sprintService.startSprint(sprintId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Kết thúc Sprint",
        description = """
            **BR-21:** Khi kết thúc sprint:
            1. Task DONE/CANCELLED → giữ trong sprint
            2. Task chưa DONE:
               - action=backlog → về Backlog (sprint_id=NULL)
               - action=nextSprint → chuyển sang sprint mới
            3. Tính velocity = SUM(storyPoints) task DONE
            4. Tạo Sprint Report tự động
            5. status → COMPLETED, không sửa được nữa
            6. Emit WebSocket sprint.completed
            
            **BR-22:** Velocity chỉ tính task DONE, có storyPoints.
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = """
                **unfinishedTasksAction** (required): "backlog" | "nextSprint"
                **nextSprintId** (required nếu nextSprint): UUID sprint PLANNED
                """
        )
    )
    @PatchMapping("/api/v1/sprints/{sprintId}/complete")
    public ResponseEntity<ApiResponse<SprintCompletedResponse>> completeSprint(
            @PathVariable UUID sprintId,
            @Valid @RequestBody CompleteSprintRequest request) {
        SprintCompletedResponse response = sprintService.completeSprint(sprintId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Lấy danh sách task trong Backlog",
        description = "All members. Lọc task chưa gán vào sprint nào.",
        tags = {"10. Backlog"}
    )
    @GetMapping("/api/v1/projects/{projectId}/backlog")
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> getBacklog(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, name = "priority") List<TaskPriority> priorities,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) TaskType type,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        TaskFilterParams params = new TaskFilterParams();
        params.setProjectId(projectId);
        params.setQ(q);
        params.setPriorities(priorities);
        params.setAssigneeId(assigneeId);
        params.setType(type);

        PageResponse<TaskResponse> response = sprintService.getBacklog(projectId, params, pageable, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Gán task vào sprint",
        description = """
            **BR-20:** Khi sprint đang ACTIVE → chỉ PM mới gán/bỏ task.
            
            **Validate:**
            - Sprint phải PLANNED hoặc ACTIVE
            - Sprint COMPLETED → 422 "Không thể thêm vào sprint đã hoàn thành"
            - Epic KHÔNG thể vào sprint trực tiếp (BR-17)
            
            **sprintId = null:** Chuyển task về Backlog.
            """,
        tags = {"10. Backlog"}
    )
    @PatchMapping("/api/v1/tasks/{taskId}/sprint")
    public ResponseEntity<ApiResponse<TaskResponse>> assignTaskToSprint(
            @PathVariable UUID taskId,
            @RequestBody AssignSprintRequest request) {
        TaskResponse response = sprintService.assignTaskToSprint(taskId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response, "Task đã được gán vào sprint"));
    }

    @Operation(
        summary = "Gán nhiều task vào sprint (Batch)",
        description = """
            Atomic operation — tất cả hoặc không có task nào được gán.
            
            **Validate:** Tương tự single assign. Skip task không thuộc project
            thay vì fail toàn bộ.
            
            **Response:** updatedCount + danh sách failedIds (nếu có).
            """,
        tags = {"10. Backlog"}
    )
    @PatchMapping("/api/v1/projects/{projectId}/tasks/batch-sprint")
    public ResponseEntity<ApiResponse<BatchSprintResponse>> batchAssignSprint(
            @PathVariable UUID projectId,
            @Valid @RequestBody BatchAssignSprintRequest request) {
        BatchSprintResponse response = sprintService.batchAssignSprint(projectId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
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
