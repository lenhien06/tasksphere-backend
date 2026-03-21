package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.response.MemberPerformanceResponse;
import com.zone.tasksphere.dto.response.ProjectOverviewResponse;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.Sprint;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.ProjectVisibility;
import com.zone.tasksphere.entity.enums.SprintStatus;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.exception.BusinessRuleException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.*;
import com.zone.tasksphere.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private static final String CACHE_KEY_OVERVIEW = "report:overview:%s:%s";
    private static final long   CACHE_TTL_SECONDS  = 300L; // 5 minutes
    private static final int    OVERVIEW_ROW_COLUMNS = 13;
    private static final int    MEMBER_ROW_COLUMNS = 2;
    private static final Object[] EMPTY_ROW = new Object[0];

    private final TaskRepository taskRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final SprintRepository sprintRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public MemberPerformanceResponse getMemberPerformance(
            UUID projectId, UUID sprintId, LocalDate dateFrom, LocalDate dateTo, UUID currentUserId) {

        // PM hoặc Admin mới được xem
        var member = projectMemberRepository.findByProjectIdAndUserId(projectId, currentUserId)
                .orElseThrow(() -> new Forbidden("Bạn không phải thành viên dự án này"));
        if (member.getProjectRole() != ProjectRole.PROJECT_MANAGER) {
            throw new Forbidden("Chỉ Project Manager mới được xem báo cáo này");
        }

        // Build period info
        MemberPerformanceResponse.PeriodInfo period = buildPeriodInfo(sprintId, dateFrom, dateTo);

        // Convert dates to Instant
        Instant instantFrom = dateFrom != null
                ? dateFrom.atStartOfDay().toInstant(ZoneOffset.UTC) : null;
        Instant instantTo = dateTo != null
                ? dateTo.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC) : null;

        // Lấy stats aggregate
        List<Object[]> rawStats = taskRepository.getMemberStats(projectId, sprintId, instantFrom, instantTo);

        List<MemberPerformanceResponse.MemberStats> memberStatsList = rawStats.stream()
                .map(row -> {
                    UUID assigneeId = (UUID) row[0];
                    long assigned = toLong(row[1]);
                    long done = toLong(row[2]);
                    long inProgress = toLong(row[3]);
                    long storyPoints = toLong(row[4]);

                    // Lấy overdue count
                    long overdue = taskRepository.countOverdueTasksByAssignee(
                            projectId, assigneeId, sprintId, instantFrom, instantTo);

                    double completionRate = assigned > 0
                            ? Math.round(done * 1000.0 / assigned) / 10.0 : 0.0;

                    // avgCompletionDays: simplified — không có completedAt field trong task
                    // Trả về 0.0 do không có snapshot field
                    double avgCompletionDays = 0.0;

                    User user = userRepository.findById(assigneeId).orElse(null);
                    if (user == null) return null;

                    return MemberPerformanceResponse.MemberStats.builder()
                            .user(MemberPerformanceResponse.UserInfo.builder()
                                    .id(user.getId())
                                    .fullName(user.getFullName())
                                    .avatarUrl(user.getAvatarUrl())
                                    .build())
                            .tasksAssigned(assigned)
                            .tasksDone(done)
                            .tasksInProgress(inProgress)
                            .tasksOverdue(overdue)
                            .storyPointsCompleted(storyPoints)
                            .completionRate(completionRate)
                            .avgCompletionDays(avgCompletionDays)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();

        return MemberPerformanceResponse.builder()
                .period(period)
                .members(memberStatsList)
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private MemberPerformanceResponse.PeriodInfo buildPeriodInfo(
            UUID sprintId, LocalDate dateFrom, LocalDate dateTo) {

        if (sprintId != null) {
            Sprint sprint = sprintRepository.findById(sprintId)
                    .orElseThrow(() -> new NotFoundException("Sprint không tồn tại"));
            return MemberPerformanceResponse.PeriodInfo.builder()
                    .sprintId(sprint.getId())
                    .sprintName(sprint.getName())
                    .dateFrom(sprint.getStartDate())
                    .dateTo(sprint.getEndDate())
                    .build();
        }

        return MemberPerformanceResponse.PeriodInfo.builder()
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .build();
    }

    // ── P7-BE: Project Overview ───────────────────────────────────────────────

    @Override
    public ProjectOverviewResponse getOverview(UUID projectId, UUID sprintId, UUID currentUserId) {

        // 1. Project tồn tại
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Dự án không tồn tại hoặc đã bị xóa"));

        // 2. Kiểm tra quyền xem (member hoặc project không private, hoặc ADMIN)
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Người dùng không tồn tại"));

        boolean isAdmin = currentUser.getSystemRole() == SystemRole.ADMIN;
        if (!isAdmin) {
            boolean isMember = projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUserId);
            if (!isMember && project.getVisibility() == ProjectVisibility.PRIVATE) {
                throw new Forbidden("Bạn không có quyền xem báo cáo dự án này");
            }
        }

        // 3. Nếu có sprintId — validate sprint thuộc project
        Sprint sprint = null;
        if (sprintId != null) {
            sprint = sprintRepository.findByIdAndProject_IdAndDeletedAtIsNull(sprintId, projectId)
                    .orElseThrow(() -> new BusinessRuleException("Sprint không thuộc dự án này"));
        }

        // 4. Kiểm tra cache Redis (graceful fallback nếu Redis down)
        String cacheKey = String.format(CACHE_KEY_OVERVIEW,
                projectId, sprintId != null ? sprintId : "all");
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof ProjectOverviewResponse cachedResponse) {
                if (!isLikelyStaleCachedOverview(cachedResponse, projectId, sprintId)) {
                    return cachedResponse;
                }
                log.info("[ReportCache] Ignoring stale overview cache for project {}", projectId);
            }
        } catch (Exception e) {
            log.warn("[ReportCache] Redis unavailable, querying DB: {}", e.getMessage());
        }

        // 5. Query DB — một query duy nhất (conditional aggregation)
        ProjectOverviewResponse result = buildOverviewFromDB(project, sprint, sprintId);

        // 6. Lưu cache (graceful, không throw nếu Redis lỗi)
        try {
            redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[ReportCache] Failed to cache overview: {}", e.getMessage());
        }

        return result;
    }

    @Override
    public void invalidateOverviewCache(UUID projectId) {
        try {
            String pattern = String.format("report:overview:%s:*", projectId);
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("[ReportCache] Invalidated {} overview cache keys for project {}", keys.size(), projectId);
            }
        } catch (Exception e) {
            log.warn("[ReportCache] Failed to invalidate cache for project {}: {}", projectId, e.getMessage());
        }
    }

    private ProjectOverviewResponse buildOverviewFromDB(Project project, Sprint sprint, UUID sprintId) {
        // 1. Single-query aggregation for all stats (current + delta)
        List<Object[]> rows = sprintId == null
                ? taskRepository.getProjectOverviewWithDeltaStatsAll(project.getId())
                : taskRepository.getProjectOverviewWithDeltaStatsBySprint(project.getId(), sprintId);

        int total = 0, doneCount = 0, todoCount = 0, inProgressCount = 0,
                inReviewCount = 0, cancelledCount = 0, overdueCount = 0,
                totalSp = 0, doneSp = 0, backlogCount = 0,
                backlogCount7dAgo = 0, doneCount7dAgo = 0, total7dAgo = 0;

        if (!rows.isEmpty() && rows.get(0) != null) {
            Object[] row = normalizeRow(rows.get(0));
            if (row.length < OVERVIEW_ROW_COLUMNS) {
                log.warn("[ReportOverview] Unexpected overview row shape: expected {} columns, got {}",
                        OVERVIEW_ROW_COLUMNS, row.length);
            }
            total             = readIntAt(row, 0);
            doneCount         = readIntAt(row, 1);
            todoCount         = readIntAt(row, 2);
            inProgressCount   = readIntAt(row, 3);
            inReviewCount     = readIntAt(row, 4);
            cancelledCount    = readIntAt(row, 5);
            overdueCount      = readIntAt(row, 6);
            totalSp           = readIntAt(row, 7);
            doneSp            = readIntAt(row, 8);
            backlogCount      = readIntAt(row, 9);
            backlogCount7dAgo = readIntAt(row, 10);
            doneCount7dAgo    = readIntAt(row, 11);
            total7dAgo        = readIntAt(row, 12);
        }

        // Fallback: nếu native query trả zero nhưng DB thực tế có task,
        // tính lại từ entity để tránh sai số liệu trên dashboard.
        List<Task> scopedTasks = getScopedTasks(project.getId(), sprintId);
        if (total == 0 && !scopedTasks.isEmpty()) {
            log.warn("[ReportOverview] Native overview returned 0 but scoped tasks exist (projectId={}, sprintId={})",
                    project.getId(), sprintId);
            total = scopedTasks.size();
            doneCount = (int) scopedTasks.stream().filter(t -> t.getTaskStatus() == TaskStatus.DONE).count();
            todoCount = (int) scopedTasks.stream().filter(t -> t.getTaskStatus() == TaskStatus.TODO).count();
            inProgressCount = (int) scopedTasks.stream().filter(t -> t.getTaskStatus() == TaskStatus.IN_PROGRESS).count();
            inReviewCount = (int) scopedTasks.stream().filter(t -> t.getTaskStatus() == TaskStatus.IN_REVIEW).count();
            cancelledCount = (int) scopedTasks.stream().filter(t -> t.getTaskStatus() == TaskStatus.CANCELLED).count();
            overdueCount = (int) scopedTasks.stream()
                    .filter(t -> t.getDueDate() != null)
                    .filter(t -> t.getDueDate().isBefore(LocalDate.now()))
                    .filter(t -> t.getTaskStatus() != TaskStatus.DONE && t.getTaskStatus() != TaskStatus.CANCELLED)
                    .count();
            totalSp = scopedTasks.stream().map(Task::getStoryPoints).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
            doneSp = scopedTasks.stream()
                    .filter(t -> t.getTaskStatus() == TaskStatus.DONE)
                    .map(Task::getStoryPoints).filter(Objects::nonNull)
                    .mapToInt(Integer::intValue).sum();
            backlogCount = (int) taskRepository.findAllByProjectIdOrderByTaskPositionAsc(project.getId()).stream()
                    .filter(t -> t.getSprint() == null)
                    .filter(t -> t.getTaskStatus() != TaskStatus.DONE && t.getTaskStatus() != TaskStatus.CANCELLED)
                    .count();
        }

        double completionRate = total > 0
                ? Math.round(doneCount * 1000.0 / total) / 10.0 : 0.0;

        // Data đủ để so sánh 7 ngày trước: project đã tồn tại >= 7 ngày.
        boolean hasSevenDayHistory = project.getCreatedAt() != null
                && !project.getCreatedAt().isAfter(Instant.now().minus(7, ChronoUnit.DAYS));

        // 2. Completion rate delta (null nếu không đủ data)
        Double completionRateDelta = null;
        if (hasSevenDayHistory && total7dAgo > 0) {
            double rate7dAgo = Math.round(doneCount7dAgo * 1000.0 / total7dAgo) / 10.0;
            completionRateDelta = Math.round((completionRate - rate7dAgo) * 10.0) / 10.0;
        }

        // 3. Backlog delta (null nếu không đủ data)
        Integer backlogCountDelta = hasSevenDayHistory
                ? backlogCount - backlogCount7dAgo : null;

        // 4. Active sprint days remaining + sprintId/sprintName cho Card 3
        Sprint activeSprint = sprintRepository
                .findByProject_IdAndStatusAndDeletedAtIsNull(project.getId(), SprintStatus.ACTIVE)
                .orElse(null);
        Integer sprintDaysRemaining = null;
        UUID responseSprintId = sprint != null ? sprint.getId() : null;
        String responseSprintName = sprint != null ? sprint.getName() : null;
        if (activeSprint != null && activeSprint.getEndDate() != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), activeSprint.getEndDate());
            sprintDaysRemaining = (int) Math.max(days, 0);
            // Khi không filter sprint: trả active sprint để FE hiển thị sub-text và gọi burndown
            if (sprint == null) {
                responseSprintId = activeSprint.getId();
                responseSprintName = activeSprint.getName();
            }
        }

        // 5. Member count + new joins
        int memberCount = 0, newMembersLast7Days = 0;
        Object[] memberRow = projectMemberRepository.getMemberCountWithNewJoins(project.getId());
        if (memberRow != null) {
            Object[] normalizedMemberRow = normalizeRow(memberRow);
            if (normalizedMemberRow.length < MEMBER_ROW_COLUMNS) {
                log.warn("[ReportOverview] Unexpected member row shape: expected {} columns, got {}",
                        MEMBER_ROW_COLUMNS, normalizedMemberRow.length);
            }
            memberCount        = readIntAt(normalizedMemberRow, 0);
            newMembersLast7Days = readIntAt(normalizedMemberRow, 1);
        }
        if (memberCount == 0) {
            var members = projectMemberRepository.findByProjectId(project.getId());
            if (!members.isEmpty()) {
                log.warn("[ReportOverview] Native member aggregate returned 0 but members exist (projectId={})", project.getId());
                memberCount = members.size();
                Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
                newMembersLast7Days = (int) members.stream()
                        .filter(m -> m.getJoinedAt() != null && !m.getJoinedAt().isBefore(sevenDaysAgo))
                        .count();
            }
        }

        List<ProjectOverviewResponse.StatusDistributionItem> distribution = buildDistribution(
                total, todoCount, inProgressCount, inReviewCount, doneCount, cancelledCount);

        Instant now = Instant.now();
        return ProjectOverviewResponse.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .sprintId(responseSprintId)
                .sprintName(responseSprintName)
                .totalTasks(total)
                .completionRate(completionRate)
                .overdueTasks(overdueCount)
                .totalStoryPoints(totalSp)
                .doneStoryPoints(doneSp)
                .statusDistribution(distribution)
                .overallProgress(completionRate)
                .generatedAt(now)
                .cachedUntil(now.plusSeconds(CACHE_TTL_SECONDS))
                // delta fields
                .completionRateDelta(completionRateDelta)
                .backlogCount(backlogCount)
                .backlogCountDelta(backlogCountDelta)
                .sprintDaysRemaining(sprintDaysRemaining)
                .memberCount(memberCount)
                .newMembersLast7Days(newMembersLast7Days)
                .build();
    }

    private boolean isLikelyStaleCachedOverview(ProjectOverviewResponse cached, UUID projectId, UUID sprintId) {
        if (cached == null) return true;

        if (cached.getTotalTasks() == 0) {
            List<Task> projectTasks = Optional.ofNullable(
                    taskRepository.findAllByProjectIdOrderByTaskPositionAsc(projectId)
            ).orElseGet(List::of);
            boolean hasTasksInScope = projectTasks.stream().anyMatch(task ->
                    sprintId == null
                            ? true
                            : task.getSprint() != null && sprintId.equals(task.getSprint().getId())
            );
            if (hasTasksInScope) return true;
        }

        if (cached.getMemberCount() == 0) {
            List<?> members = Optional.ofNullable(projectMemberRepository.findByProjectId(projectId))
                    .orElseGet(List::of);
            if (!members.isEmpty()) return true;
        }

        return false;
    }

    private List<ProjectOverviewResponse.StatusDistributionItem> buildDistribution(
            int total, int todo, int inProgress, int inReview, int done, int cancelled) {

        return List.of(
                toDistItem("todo",        todo,       total),
                toDistItem("in_progress", inProgress, total),
                toDistItem("in_review",   inReview,   total),
                toDistItem("done",        done,        total),
                toDistItem("cancelled",   cancelled,  total)
        );
    }

    private ProjectOverviewResponse.StatusDistributionItem toDistItem(String status, int count, int total) {
        double pct = total > 0 ? Math.round(count * 1000.0 / total) / 10.0 : 0.0;
        return ProjectOverviewResponse.StatusDistributionItem.builder()
                .status(status).count(count).percentage(pct).build();
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        if (val instanceof Number n) return n.longValue();
        return 0L;
    }

    private int toInt(Object val) {
        if (val == null) return 0;
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    private int readIntAt(Object[] row, int index) {
        if (row == null || index < 0 || index >= row.length) {
            return 0;
        }
        return toInt(row[index]);
    }

    private Object[] normalizeRow(Object rawRow) {
        if (rawRow == null) return EMPTY_ROW;
        if (!(rawRow instanceof Object[] arr)) return new Object[]{rawRow};
        if (arr.length == 1 && arr[0] instanceof Object[] nested) return nested;
        return arr;
    }

    private List<Task> getScopedTasks(UUID projectId, UUID sprintId) {
        List<Task> projectTasks = Optional.ofNullable(taskRepository.findAllByProjectIdOrderByTaskPositionAsc(projectId))
                .orElseGet(List::of);
        if (sprintId == null) return projectTasks;
        return projectTasks.stream()
                .filter(t -> t.getSprint() != null && sprintId.equals(t.getSprint().getId()))
                .toList();
    }
}
