package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.TaskType;
import lombok.Builder;
import lombok.Data;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Task Detail Response")
public class TaskDetailResponse {

    // ── Định danh ─────────────────────────
    @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID id;
    @Schema(description = "Task code", example = "CODE-123")
    private String taskCode;
    private Long version;           // ETag cho optimistic locking (NFR-19)

    // ── Nội dung ──────────────────────────
    @Schema(description = "Title", example = "Item Title")
    private String title;
    @Schema(description = "Description", example = "Description of the item")
    private String description;     // Markdown
    @Schema(description = "Type", example = "TASK")
    private TaskType type;
    @Schema(description = "Task status", example = "ACTIVE")
    private TaskStatus taskStatus;
    @Schema(description = "Priority", example = "HIGH")
    private TaskPriority priority;

    // ── Kanban ────────────────────────────
    @Schema(description = "Task position", example = "1")
    private int taskPosition;
    @Schema(description = "Status column", example = "ACTIVE")
    private ColumnSummary statusColumn;

    // ── Tiến độ ───────────────────────────
    @Schema(description = "Story points", example = "1")
    private Integer storyPoints;
    @Schema(description = "Estimated hours", example = "10.5")
    private BigDecimal estimatedHours;
    @Schema(description = "Actual hours logged", example = "3.5")
    private BigDecimal actualHours;
    @Schema(description = "Start date", example = "2023-12-01")
    private LocalDate startDate;
    @Schema(description = "Due date", example = "2023-12-31T23:59:59Z")
    private LocalDate dueDate;
    @Schema(description = "Overdue", example = "true")
    private boolean overdue;

    // ── Recurring ─────────────────────────
    @Schema(description = "Is recurring task", example = "false")
    private boolean isRecurring;

    // ── Sub-task ──────────────────────────
    @Schema(description = "Depth", example = "1")
    private int depth;
    @Schema(description = "Subtask count", example = "10")
    private int subtaskCount;
    @Schema(description = "Subtask done count", example = "10")
    private int subtaskDoneCount;
    @Schema(description = "Subtask progress 0–100, null if no subtasks", example = "60")
    private Integer subtaskProgress;
    @Schema(description = "Parent task", example = "example")
    private TaskSummary parentTask;
    @Schema(description = "Subtasks", example = "[]")
    private List<SubTaskSummary> subtasks;

    // ── Thống kê ──────────────────────────
    @Schema(description = "Comment count", example = "10")
    private int commentCount;
    @Schema(description = "Checklist total items", example = "5")
    @Setter private int checklistTotal;
    @Schema(description = "Checklist done items", example = "3")
    @Setter private int checklistDone;
    @Schema(description = "Attachment count", example = "10")
    private int attachmentCount;

    // ── Quan hệ & dependencies ─────────────────────
    @Schema(description = "Assignee", example = "example")
    private UserSummary assignee;
    @Schema(description = "Reporter", example = "example")
    private UserSummary reporter;
    @Schema(description = "Sprint", example = "example")
    private SprintSummary sprint;
    @Schema(description = "Project version", example = "example")
    private VersionSummary projectVersion;
    @Schema(description = "Task links/dependencies")
    @Setter private List<TaskLinkSummary> links;

    // ── Permissions ───────────────────────
    @Schema(description = "Current user can edit this task", example = "true")
    @Setter private boolean canEdit;
    @Schema(description = "Current user can delete this task", example = "false")
    @Setter private boolean canDelete;

    // ── Custom Fields ─────────────────────
    @Schema(description = "Custom field values", example = "[]")
    private List<CustomFieldValueResponse> customFieldValues;

    // ── Audit ─────────────────────────────
    @Schema(description = "Created at", example = "2023-12-31T23:59:59Z")
    private Instant createdAt;
    @Schema(description = "Updated at", example = "2023-12-31T23:59:59Z")
    private Instant updatedAt;

    // ── Nested DTOs ───────────────────────

    @Data @Builder
@Schema(description = "User Summary")
public static class UserSummary {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Full name", example = "John Doe")
        private String fullName;
        @Schema(description = "Avatar url", example = "https://example.com/image.png")
        private String avatarUrl;
    }

    @Data @Builder
@Schema(description = "Column Summary")
public static class ColumnSummary {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Name", example = "John Doe")
        private String name;
        @Schema(description = "Color hex", example = "string")
        private String colorHex;
        @Schema(description = "Sort order", example = "1")
        private int sortOrder;
    }

    @Data @Builder
@Schema(description = "Sprint Summary")
public static class SprintSummary {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Name", example = "John Doe")
        private String name;
    }

    @Data @Builder
@Schema(description = "Task Summary")
public static class TaskSummary {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Task code", example = "CODE-123")
        private String taskCode;
        @Schema(description = "Title", example = "Item Title")
        private String title;
    }

    @Data @Builder
@Schema(description = "Sub Task Summary")
public static class SubTaskSummary {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Task code", example = "CODE-123")
        private String taskCode;
        @Schema(description = "Title", example = "Item Title")
        private String title;
        @Schema(description = "Task status", example = "ACTIVE")
        private TaskStatus taskStatus;
        @Schema(description = "Priority", example = "MEDIUM")
        private TaskPriority priority;
        @Schema(description = "Assignee", example = "example")
        private UserSummary assignee;
    }

    @Data @Builder
    @Schema(description = "Task Link Summary")
    public static class TaskLinkSummary {
        @Schema(description = "Link id")
        private UUID id;
        @Schema(description = "Link type from this task's perspective",
                example = "BLOCKS",
                allowableValues = {"BLOCKS", "BLOCKED_BY", "RELATES_TO", "DUPLICATES", "IS_DUPLICATED_BY"})
        private String linkType;
        @Schema(description = "The other task in this link")
        private TaskRef targetTask;

        @Data @Builder
        @Schema(description = "Task Reference in link")
        public static class TaskRef {
            private UUID id;
            @Schema(description = "Human-readable task code", example = "PROJ-007")
            private String taskId;      // taskCode
            private String title;
            private TaskStatus status;
        }
    }

    @Data @Builder
@Schema(description = "Version Summary")
public static class VersionSummary {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Name", example = "John Doe")
        private String name;
    }
}
