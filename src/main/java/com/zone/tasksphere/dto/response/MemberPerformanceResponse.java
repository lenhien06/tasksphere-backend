package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Phản hồi báo cáo hiệu suất thành viên */
@Data
@Builder
@Schema(description = "Member Performance Response")
public class MemberPerformanceResponse {
    @Schema(description = "Period", example = "example")
    private PeriodInfo period;
    @Schema(description = "Members", example = "[]")
    private List<MemberStats> members;

    @Data
    @Builder
@Schema(description = "Period Info")
public static class PeriodInfo {
        @Schema(description = "Sprint id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID sprintId;
        @Schema(description = "Sprint name", example = "John Doe")
        private String sprintName;
        @Schema(description = "Date from", example = "2023-12-31T23:59:59Z")
        private LocalDate dateFrom;
        @Schema(description = "Date to", example = "2023-12-31T23:59:59Z")
        private LocalDate dateTo;
    }

    @Data
    @Builder
@Schema(description = "Member Stats")
public static class MemberStats {
        @Schema(description = "User", example = "example")
        private UserInfo user;
        @Schema(description = "Tasks assigned", example = "1")
        private long tasksAssigned;
        @Schema(description = "Tasks done", example = "1")
        private long tasksDone;
        @Schema(description = "Tasks in progress", example = "1")
        private long tasksInProgress;
        @Schema(description = "Tasks overdue", example = "1")
        private long tasksOverdue;
        @Schema(description = "Story points completed", example = "1")
        private long storyPointsCompleted;
        @Schema(description = "Completion rate", example = "10.5")
        private double completionRate;
        @Schema(description = "Avg completion days", example = "10.5")
        private double avgCompletionDays;
    }

    @Data
    @Builder
@Schema(description = "User Info")
public static class UserInfo {
        @Schema(description = "Id", example = "550e8400-e29b-41d4-a716-446655440000")
        private UUID id;
        @Schema(description = "Full name", example = "John Doe")
        private String fullName;
        @Schema(description = "Avatar url", example = "https://example.com/image.png")
        private String avatarUrl;
    }
}
