package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.*;
import com.zone.tasksphere.dto.response.*;
import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.TaskType;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.security.CustomUserDetail;
import com.zone.tasksphere.service.TaskService;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/tasks")
@RequiredArgsConstructor
@Tag(name = "4. Task Management", description = "CRUD task, Kanban board, Sub-task, filter, calendar view.")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;

    @Operation(
        summary = "Tạo task mới",
        description = """
            **FR-14:** Tạo task trong dự án với các thông tin cơ bản.
            
            **Auto-generate:** taskCode = PROJECT_KEY + "-" + số tăng dần (PROJ-001)
            sử dụng atomic counter trong bảng projects để đảm bảo thread-safe.
            
            **Validate (BR):**
            - BR-13: taskCode auto-gen, không thể tùy chỉnh
            - BR-15: Nếu parentTaskId → depth ≤ 3; Epic không thể là sub-task
            - BR-16: assigneeId phải là member của project
            - BR-17: sprintId → task chỉ thuộc 1 sprint; Epic không vào sprint
            - FR-14: due_date ≥ ngày hiện tại (nếu có)
            
            **Quyền:** PM hoặc MEMBER (VIEWER không tạo được)
            """,
        security = @SecurityRequirement(name = "bearerAuth"),
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = """
                **title** (required): Tiêu đề task, 1-255 ký tự
                **type**: TASK | BUG | FEATURE | STORY | EPIC | SUB_TASK (default: TASK)
                **priority**: CRITICAL | HIGH | MEDIUM | LOW (default: MEDIUM)
                **assigneeId**: UUID của member (optional)
                **dueDate**: Hạn chót ISO 8601 (optional, phải >= hôm nay)
                **storyPoints**: Story points 1-100 (optional, null = chưa gán)
                **sprintId**: UUID sprint (null = backlog)
                **parentTaskId**: UUID task cha (null = root task)
                **statusColumnId**: UUID cột Kanban (null = cột đầu tiên)
                """
        )
    )
    @PostMapping
    public ResponseEntity<ApiResponse<TaskDetailResponse>> createTask(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateTaskRequest request) {
        TaskDetailResponse response = taskService.createTask(projectId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(
        summary = "Calendar View — Task theo tháng",
        description = """
            **FR-19:** Lấy task có due_date trong tháng/năm được chỉ định.
            Dùng để render Calendar View.
            
            **Lưu ý mapping:** Endpoint này phải đăng ký TRƯỚC /{taskId}
            trong Spring MVC để tránh nhầm "calendar" là taskId.
            
            **Performance:** ≤ 500ms cho ≤ 200 tasks/tháng (NFR Calendar).
            **isOverdue:** true nếu dueDate < hôm nay AND status != DONE/CANCELLED.
            """,
        parameters = {
            @Parameter(name = "year",  description = "Năm (2020-2030)", required = true, example = "2026"),
            @Parameter(name = "month", description = "Tháng (1-12)",    required = true, example = "3")
        }
    )
    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<CalendarViewResponse>> getCalendar(
            @PathVariable UUID projectId,
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) UUID sprintId,
            @RequestParam(required = false) TaskPriority priority) {
        CalendarViewResponse response = taskService.getCalendarView(
            projectId, year, month, q, status, assigneeId, sprintId, priority, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Timeline / Gantt View — task và dependency trong dự án",
        description = """
            Trả về toàn bộ task trong dự án cùng danh sách dependency blocker để FE render Timeline/Gantt.

            Response bao gồm:
            - task metadata: id, taskCode, title, status, priority, assignee, startDate, dueDate, parentTaskId
            - blockedBy / blocking cho từng task
            - dependency edges chuẩn hoá ở cấp project
            """
    )
    @GetMapping("/timeline")
    public ResponseEntity<ApiResponse<TimelineViewResponse>> getTimeline(
            @PathVariable UUID projectId) {
        TimelineViewResponse response = taskService.getTimelineView(projectId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Cập nhật due_date của task (Calendar drag/drop)")
    @PatchMapping("/{taskId}/due-date")
    public ResponseEntity<ApiResponse<TaskDetailResponse>> updateDueDate(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskDueDateRequest request) {
        TaskDetailResponse response = taskService.updateDueDate(projectId, taskId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Danh sách task / Kanban Board",
        description = """
            **FR-15:** Lấy danh sách task của dự án, dùng để render Kanban Board.
            
            **Sort mặc định:** theo statusColumn.sortOrder ASC, taskPosition ASC
            (đúng thứ tự cột và vị trí trong cột).
            
            **VIEWER:** Chỉ có quyền GET, không thể tạo/sửa.
            """,
        parameters = {
            @Parameter(name = "q",           description = "Tìm theo title hoặc taskCode"),
            @Parameter(name = "status",       description = "TODO | IN_PROGRESS | IN_REVIEW | DONE | CANCELLED"),
            @Parameter(name = "assigneeId",   description = "UUID user, hoặc 'me' để lọc task của mình"),
            @Parameter(name = "sprintId",     description = "UUID sprint, hoặc 'backlog' để lọc task chưa gán sprint"),
            @Parameter(name = "priority",     description = "CRITICAL | HIGH | MEDIUM | LOW"),
            @Parameter(name = "type",         description = "TASK | BUG | FEATURE | STORY | EPIC | SUB_TASK"),
            @Parameter(name = "overdue",      description = "true: chỉ task quá hạn (dueDate < NOW AND status != DONE)"),
            @Parameter(name = "dueSoon",      description = "FR-27: true = dueDate trong 7 ngày tới, status != DONE/CANCELLED"),
            @Parameter(name = "limit",       description = "Giới hạn số kết quả (vd: Due Soon dùng limit=5)"),
            @Parameter(name = "sortBy",      description = "Field sort: dueDate, priority, createdAt"),
            @Parameter(name = "order",       description = "asc hoặc desc"),
            @Parameter(name = "page",        description = "Số trang (bắt đầu từ 0)"),
            @Parameter(name = "size",         description = "Số item mỗi trang (default 20)")
        }
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<TaskResponse>>> getTasks(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) UUID sprintId,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) TaskType type,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(required = false) Boolean dueSoon,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false, defaultValue = "asc") String order,
            @PageableDefault(size = 20) Pageable pageable) {

        TaskFilterParams params = new TaskFilterParams();
        params.setProjectId(projectId);
        params.setQ(q);
        params.setStatus(status);
        params.setAssigneeId(assigneeId);
        params.setSprintId(sprintId);
        params.setPriority(priority);
        params.setType(type);
        params.setOverdue(overdue);
        params.setDueSoon(dueSoon);

        Pageable effectivePageable = buildPageable(pageable, dueSoon, limit, sortBy, order);
        PageResponse<TaskResponse> response =
            taskService.getTasks(projectId, params, effectivePageable, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Pageable buildPageable(Pageable defaultPageable, Boolean dueSoon, Integer limit,
                                   String sortBy, String order) {
        boolean useLimitMode = (limit != null && limit > 0) || Boolean.TRUE.equals(dueSoon);
        int size = useLimitMode
            ? (limit != null && limit > 0 ? Math.min(limit, 100) : 5)
            : defaultPageable.getPageSize();
        int page = useLimitMode ? 0 : defaultPageable.getPageNumber();

        String sortField = null;
        if (sortBy != null && !sortBy.isBlank()) {
            sortField = switch (sortBy.toLowerCase()) {
                case "duedate" -> "dueDate";
                case "priority" -> "priority";
                case "createdat" -> "createdAt";
                default -> sortBy;
            };
        } else if (Boolean.TRUE.equals(dueSoon)) {
            sortField = "dueDate";
        }

        if (sortField != null) {
            Sort.Direction dir = "desc".equalsIgnoreCase(order) ? Sort.Direction.DESC : Sort.Direction.ASC;
            return PageRequest.of(page, size, Sort.by(dir, sortField));
        }
        return PageRequest.of(page, size);
    }

    @Operation(
        summary = "Chi tiết task",
        description = """
            **FR-15:** Lấy thông tin đầy đủ của 1 task bao gồm:
            - Metadata cơ bản (title, type, status, priority...)
            - Assignee và Reporter (với avatar)
            - Sprint info
            - Parent task (nếu là sub-task)
            - Direct sub-tasks (cấp 1)
            - Checklist summary (total/completed/items)
            - Count: commentsCount, attachmentsCount
            - Custom field values
            
            **ETag header:** Trả về `ETag: "{version}"` để dùng cho
            optimistic locking khi cập nhật (NFR-19).
            """,
        security = @SecurityRequirement(name = "bearerAuth"),
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "TaskDetailResponse + ETag header",
                headers = @Header(name = "ETag",
                    description = "Version number cho optimistic locking",
                    schema = @Schema(example = "\"3\""))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Task không tồn tại hoặc đã bị xóa")
        }
    )
    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskDetailResponse>> getTask(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId) {
        TaskDetailResponse response = taskService.getTaskById(projectId, taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Cập nhật task",
        description = """
            Cập nhật các thông tin cơ bản của task.
            Hỗ trợ Optimistic Locking qua ETag/If-Match.
            """
    )
    @PutMapping("/{taskId}")
    public ResponseEntity<ApiResponse<TaskDetailResponse>> updateTask(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request) {
        TaskDetailResponse response =
            taskService.updateTask(projectId, taskId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Đổi trạng thái task (Drag & Drop Kanban)",
        description = """
            **FR-16:** Chuyển task sang trạng thái mới theo workflow.
            
            **Business Rules kiểm tra theo thứ tự:**
            1. **Transition mở (Jira-like):**
               Cho phép chuyển từ bất kỳ status nào sang bất kỳ status nào.
            
            2. **BR-18 — Done condition:**
               Khi chuyển sang DONE: TẤT CẢ sub-task cấp 1 phải DONE/CANCELLED.
            
            3. **BR-28 — Dependencies:**
               Task bị block bởi task khác chưa DONE → không thể DONE.
            
            **Optimistic Locking (NFR-19):**
            Gửi header `If-Match: "{version}"` để tránh conflict.
            Nếu version không khớp → **409 Conflict**.
            
            **Sau khi thành công:**
            - Ghi activity_log với old_status + new_status
            - Emit WebSocket event `task.status_changed`
            - Gửi notification nếu cần
            """,
        security = @SecurityRequirement(name = "bearerAuth"),
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = """
                **status** (required): Trạng thái mới
                **statusColumnId** (required): UUID cột Kanban đích
                **comment** (optional): Ghi chú vào activity log
                """
        ),
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đổi trạng thái thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Chỉ PM hoặc Assignee mới được đổi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "ETag conflict — dữ liệu đã thay đổi"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Vi phạm BR-18/BR-28")
        }
    )
    @PatchMapping("/{taskId}/status")
    public ResponseEntity<ApiResponse<TaskStatusChangedResponse>> updateStatus(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @Valid @RequestBody UpdateTaskStatusRequest request) {
        TaskStatusChangedResponse response =
            taskService.updateStatus(projectId, taskId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Cập nhật vị trí task (Kéo thả trong cột)",
        description = """
            Cập nhật vị trí (position) của task trong cột Kanban sau khi kéo thả.
            
            **Luồng:** FE kéo task → gọi PATCH /status (nếu đổi cột) + PATCH /position.
            
            **statusColumnId**: Cột đích (bắt buộc, kể cả khi giữ nguyên cột).
            **newPosition**: Vị trí trong cột (≥ 0), các task còn lại được rebalance.
            """,
        requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = """
                **statusColumnId** (required): UUID cột đích
                **newPosition** (required): Vị trí mới trong cột (≥ 0)
                """
        )
    )
    @PatchMapping("/{taskId}/position")
    public ResponseEntity<ApiResponse<Void>> updatePosition(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskPositionRequest request) {
        taskService.updatePosition(projectId, taskId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }

    @Operation(
        summary = "Xóa task (Soft Delete)",
        description = """
            **Quyền:** PM only.
            
            **BR-24:** Soft delete — set deleted_at = NOW().
            Task KHÔNG bị xóa vật lý khỏi database.
            
            **Cascade:** TẤT CẢ sub-task (cháu, chắt) cũng bị soft-delete đệ quy.
            Comments, Attachments, Worklogs được GIỮ NGUYÊN.
            """,
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Đã xóa thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Chỉ PM mới xóa được task")
        }
    )
    @DeleteMapping("/{taskId}")
    public ResponseEntity<ApiResponse<Void>> deleteTask(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId) {
        taskService.deleteTask(projectId, taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success((Void) null));
    }

    @Operation(
        summary = "Tạo sub-task",
        description = """
            **FR-17 + BR-15:** Tạo task con cho task cha.
            
            **Validate:**
            - depth ≤ 3: task.depth + 1 ≤ 3 (TSK_003)
            - Task cha không phải EPIC (EPIC không thể là sub-task)
            - Task cha chưa bị xóa
            
            **Auto-set:**
            - parentTaskId = parentTask.id
            - depth = parentTask.depth + 1
            - project = parentTask.project
            - taskCode mới được tạo tự động
            
            **Lỗi TSK_003:** 422 khi depth vượt quá 3 cấp.
            """,
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Sub-task tạo thành công"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "TSK_003: Sub-task depth limit (max 3 cấp)")
        }
    )
    @PostMapping("/{parentTaskId}/subtasks")
    public ResponseEntity<ApiResponse<TaskDetailResponse>> createSubTask(
            @PathVariable UUID projectId,
            @PathVariable UUID parentTaskId,
            @Valid @RequestBody CreateTaskRequest request) {
        TaskDetailResponse response = taskService.createSubTask(parentTaskId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy danh sách sub-tasks")
    @GetMapping("/{taskId}/subtasks")
    public ResponseEntity<ApiResponse<List<SubTaskResponse>>> getSubTasks(
            @PathVariable UUID projectId,
            @PathVariable UUID taskId) {
        List<SubTaskResponse> response = taskService.getSubTasks(taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(
        summary = "Promote sub-task thành task độc lập",
        description = """
            Biến sub-task thành task độc lập (không còn là con của task cha).
            
            **Xử lý:**
            - parentTaskId = NULL
            - depth = 0
            - Giữ nguyên: project, assignee, description, priority, taskCode
            - Giữ nguyên: sprintId nếu có
            
            **SRS FR:** Convert sub-task thành task lớn riêng biệt.
            """,
        responses = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Task đã được promote — message xác nhận")
        }
    )
    @PostMapping("/{subtaskId}/promote")
    public ResponseEntity<ApiResponse<TaskDetailResponse>> promoteSubTask(
            @PathVariable UUID projectId,
            @PathVariable UUID subtaskId,
            @RequestBody(required = false) PromoteSubTaskRequest request) {
        TaskDetailResponse response =
            taskService.promoteSubTask(subtaskId, request, getCurrentUserId(), projectId);
        return ResponseEntity.ok(ApiResponse.success(response, "Đã chuyển thành task độc lập"));
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
