package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.response.ProjectOverviewResponse;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectMember;
import com.zone.tasksphere.entity.Sprint;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.ProjectVisibility;
import com.zone.tasksphere.entity.enums.SprintStatus;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.SprintRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectMemberRepository projectMemberRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private SprintRepository sprintRepository;
    @Mock private UserRepository userRepository;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private ReportServiceImpl reportService;

    private UUID projectId;
    private UUID currentUserId;
    private Project project;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        currentUserId = UUID.randomUUID();

        project = new Project();
        project.setId(projectId);
        project.setName("E-Commerce Platform");
        project.setVisibility(ProjectVisibility.PRIVATE);
        project.setCreatedAt(Instant.now().minusSeconds(10L * 24 * 3600));

        User currentUser = new User();
        currentUser.setId(currentUserId);
        currentUser.setSystemRole(SystemRole.ADMIN);

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        lenient().when(projectMemberRepository.getMemberCountWithNewJoins(projectId)).thenReturn(new Object[]{7L, 3L});
        lenient().when(sprintRepository.findByProject_IdAndStatusAndDeletedAtIsNull(projectId, SprintStatus.ACTIVE))
                .thenReturn(Optional.empty());
    }

    @Test
    void getOverview_shouldReturnAllNewDeltaFields() {
        when(taskRepository.getProjectOverviewWithDeltaStatsAll(projectId)).thenReturn(Collections.singletonList(defaultRow()));

        ProjectOverviewResponse response = reportService.getOverview(projectId, null, currentUserId);

        assertEquals(60.0, response.getCompletionRate());
        assertEquals(22.5, response.getCompletionRateDelta());
        assertEquals(4, response.getBacklogCount());
        assertEquals(-2, response.getBacklogCountDelta());
        assertNull(response.getSprintDaysRemaining());
        assertEquals(7, response.getMemberCount());
        assertEquals(3, response.getNewMembersLast7Days());
    }

    @Test
    void getOverview_shouldSetCompletionRateDeltaNull_whenProjectIsNewerThan7Days() {
        project.setCreatedAt(Instant.now().minusSeconds(3L * 24 * 3600));
        when(taskRepository.getProjectOverviewWithDeltaStatsAll(projectId)).thenReturn(Collections.singletonList(defaultRow()));

        ProjectOverviewResponse response = reportService.getOverview(projectId, null, currentUserId);

        assertNull(response.getCompletionRateDelta());
    }

    @Test
    void getOverview_shouldRoundCompletionRateDeltaToOneDecimal() {
        when(taskRepository.getProjectOverviewWithDeltaStatsAll(projectId)).thenReturn(Collections.singletonList(
                new Object[]{9, 5, 2, 1, 1, 0, 1, 20, 10, 3, 2, 3, 7}
        ));

        ProjectOverviewResponse response = reportService.getOverview(projectId, null, currentUserId);

        assertEquals(12.7, response.getCompletionRateDelta());
    }

    @Test
    void getOverview_shouldSetBacklogCountDeltaNull_whenProjectIsNewerThan7Days() {
        project.setCreatedAt(Instant.now().minusSeconds(2L * 24 * 3600));
        when(taskRepository.getProjectOverviewWithDeltaStatsAll(projectId)).thenReturn(Collections.singletonList(defaultRow()));

        ProjectOverviewResponse response = reportService.getOverview(projectId, null, currentUserId);

        assertNull(response.getBacklogCountDelta());
    }

    @Test
    void getOverview_shouldComputeBacklogCountDelta_whenProjectHasHistory() {
        when(taskRepository.getProjectOverviewWithDeltaStatsAll(projectId)).thenReturn(Collections.singletonList(defaultRow()));

        ProjectOverviewResponse response = reportService.getOverview(projectId, null, currentUserId);

        assertEquals(-2, response.getBacklogCountDelta());
    }

    @Test
    void getOverview_shouldSetSprintDaysRemainingNull_whenNoActiveSprint() {
        when(taskRepository.getProjectOverviewWithDeltaStatsAll(projectId)).thenReturn(Collections.singletonList(defaultRow()));

        ProjectOverviewResponse response = reportService.getOverview(projectId, null, currentUserId);

        assertNull(response.getSprintDaysRemaining());
    }

    @Test
    void getOverview_shouldSetSprintDaysRemainingZero_whenActiveSprintIsOverdue() {
        Sprint overdueSprint = new Sprint();
        overdueSprint.setEndDate(LocalDate.now().minusDays(2));
        when(sprintRepository.findByProject_IdAndStatusAndDeletedAtIsNull(projectId, SprintStatus.ACTIVE))
                .thenReturn(Optional.of(overdueSprint));
        when(taskRepository.getProjectOverviewWithDeltaStatsAll(projectId)).thenReturn(Collections.singletonList(defaultRow()));

        ProjectOverviewResponse response = reportService.getOverview(projectId, null, currentUserId);

        assertEquals(0, response.getSprintDaysRemaining());
    }

    @Test
    void getOverview_shouldReturnCachedResponse_whenCacheHit() {
        ProjectOverviewResponse cached = ProjectOverviewResponse.builder()
                .projectId(projectId)
                .projectName("Cached")
                .build();
        when(valueOperations.get(anyString())).thenReturn(cached);

        ProjectOverviewResponse response = reportService.getOverview(projectId, null, currentUserId);

        assertSame(cached, response);
        verify(taskRepository, never()).getProjectOverviewWithDeltaStatsAll(any());
        verify(taskRepository, never()).getProjectOverviewWithDeltaStatsBySprint(any(), any());
    }

    @Test
    void getOverview_shouldUseSprintSpecificQuery_whenSprintIdProvided() {
        UUID sprintId = UUID.randomUUID();
        Sprint sprint = new Sprint();
        sprint.setId(sprintId);
        sprint.setName("Sprint 4");
        when(sprintRepository.findByIdAndProject_IdAndDeletedAtIsNull(sprintId, projectId))
                .thenReturn(Optional.of(sprint));
        when(taskRepository.getProjectOverviewWithDeltaStatsBySprint(projectId, sprintId))
                .thenReturn(Collections.singletonList(defaultRow()));

        ProjectOverviewResponse response = reportService.getOverview(projectId, sprintId, currentUserId);

        assertEquals(sprintId, response.getSprintId());
        assertEquals("Sprint 4", response.getSprintName());
        verify(taskRepository).getProjectOverviewWithDeltaStatsBySprint(eq(projectId), eq(sprintId));
    }

    @Test
    void getOverview_shouldBypassCache_whenCachedDataIsStaleZero() {
        ProjectOverviewResponse cached = ProjectOverviewResponse.builder()
                .projectId(projectId)
                .projectName("Cached")
                .totalTasks(0)
                .memberCount(0)
                .build();
        Task existingTask = new Task();
        existingTask.setId(UUID.randomUUID());
        existingTask.setProject(project);

        when(valueOperations.get(anyString())).thenReturn(cached);
        when(taskRepository.findAllByProjectIdOrderByTaskPositionAsc(projectId)).thenReturn(List.of(existingTask));
        when(taskRepository.getProjectOverviewWithDeltaStatsAll(projectId)).thenReturn(Collections.singletonList(defaultRow()));

        ProjectOverviewResponse response = reportService.getOverview(projectId, null, currentUserId);

        assertNotSame(cached, response);
        assertEquals(10, response.getTotalTasks());
        verify(taskRepository).getProjectOverviewWithDeltaStatsAll(projectId);
    }

    @Test
    void getOverview_shouldNotThrow_whenNativeRowHasMissingColumns() {
        when(taskRepository.getProjectOverviewWithDeltaStatsAll(projectId))
                .thenReturn(Collections.singletonList(new Object[]{10}));
        when(projectMemberRepository.getMemberCountWithNewJoins(projectId))
                .thenReturn(new Object[]{7});

        ProjectOverviewResponse response = reportService.getOverview(projectId, null, currentUserId);
        assertEquals(10, response.getTotalTasks());
        assertEquals(7, response.getMemberCount());
        assertEquals(0, response.getNewMembersLast7Days());
    }

    @Test
    void getOverview_shouldParseNestedRowShape_fromNativeDriver() {
        Object[] nestedOverview = new Object[]{10, 6, 2, 1, 1, 0, 2, 30, 18, 4, 6, 3, 8};
        when(taskRepository.getProjectOverviewWithDeltaStatsAll(projectId))
                .thenReturn(Collections.singletonList(new Object[]{nestedOverview}));
        when(projectMemberRepository.getMemberCountWithNewJoins(projectId))
                .thenReturn(new Object[]{new Object[]{7, 3}});

        ProjectOverviewResponse response = reportService.getOverview(projectId, null, currentUserId);

        assertEquals(10, response.getTotalTasks());
        assertEquals(60.0, response.getCompletionRate());
        assertEquals(4, response.getBacklogCount());
        assertEquals(7, response.getMemberCount());
        assertEquals(3, response.getNewMembersLast7Days());
    }

    @Test
    void getOverview_shouldFallbackToEntityAggregation_whenNativeReturnsZero() {
        Task todo = new Task();
        todo.setProject(project);
        todo.setTaskStatus(TaskStatus.TODO);
        todo.setStoryPoints(5);

        Task done = new Task();
        done.setProject(project);
        done.setTaskStatus(TaskStatus.DONE);
        done.setStoryPoints(8);

        Task inProgress = new Task();
        inProgress.setProject(project);
        inProgress.setTaskStatus(TaskStatus.IN_PROGRESS);
        inProgress.setStoryPoints(3);
        inProgress.setDueDate(LocalDate.now().minusDays(1));

        ProjectMember m1 = ProjectMember.builder().joinedAt(Instant.now().minusSeconds(2 * 24 * 3600)).build();
        ProjectMember m2 = ProjectMember.builder().joinedAt(Instant.now().minusSeconds(10 * 24 * 3600)).build();

        when(taskRepository.getProjectOverviewWithDeltaStatsAll(projectId))
                .thenReturn(Collections.singletonList(new Object[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        when(taskRepository.findAllByProjectIdOrderByTaskPositionAsc(projectId))
                .thenReturn(List.of(todo, done, inProgress));
        when(projectMemberRepository.getMemberCountWithNewJoins(projectId))
                .thenReturn(new Object[]{0, 0});
        when(projectMemberRepository.findByProjectId(projectId))
                .thenReturn(List.of(m1, m2));

        ProjectOverviewResponse response = reportService.getOverview(projectId, null, currentUserId);

        assertEquals(3, response.getTotalTasks());
        assertEquals(1, response.getOverdueTasks());
        assertEquals(16, response.getTotalStoryPoints());
        assertEquals(8, response.getDoneStoryPoints());
        assertEquals(2, response.getMemberCount());
        assertEquals(1, response.getNewMembersLast7Days());
    }

    private Object[] defaultRow() {
        // [total, done, todo, inProgress, inReview, cancelled, overdue, totalSp, doneSp,
        //  backlog_count, backlog_count_7d_ago, done_count_7d_ago, total_7d_ago]
        return new Object[]{10, 6, 2, 1, 1, 0, 2, 30, 18, 4, 6, 3, 8};
    }
}
