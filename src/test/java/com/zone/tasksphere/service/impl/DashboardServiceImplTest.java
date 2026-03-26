package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.response.DashboardResponse;
import com.zone.tasksphere.entity.ActivityLog;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectMember;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.ProjectStatus;
import com.zone.tasksphere.entity.enums.ProjectVisibility;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.TaskType;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.repository.ActivityLogRepository;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private ActivityLogRepository activityLogRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Test
    void getMyDashboard_aggregatesUserScopedData() {
        User currentUser = user("me@tasksphere.local");
        Project ownedProject = project("Owned Project", currentUser);
        User owner2 = user("owner2@tasksphere.local");
        Project memberProject = project("Member Project", owner2);

        Task openTask = task(ownedProject, currentUser, "OWN-1", "Fix bug", TaskStatus.IN_PROGRESS);
        openTask.setDueDate(LocalDate.now().plusDays(1));
        Task upcomingTask = task(memberProject, currentUser, "MEM-2", "Prepare demo", TaskStatus.TODO);
        upcomingTask.setDueDate(LocalDate.now().plusDays(3));

        ActivityLog activityLog = ActivityLog.builder()
                .id(UUID.randomUUID())
                .projectId(ownedProject.getId())
                .actor(currentUser)
                .entityType(EntityType.TASK)
                .entityId(openTask.getId())
                .action(ActionType.STATUS_CHANGED)
                .createdAt(Instant.parse("2026-03-27T09:00:00Z"))
                .build();

        when(projectRepository.findOwnedOrMemberProjects(currentUser.getId()))
                .thenReturn(List.of(ownedProject, memberProject));
        when(taskRepository.findAssignedOpenTasksForDashboard(eq(currentUser.getId()), any(Pageable.class)))
                .thenReturn(List.of(openTask));
        when(taskRepository.findUpcomingAssignedTasksForDashboard(eq(currentUser.getId()), any(), any(), any(Pageable.class)))
                .thenReturn(List.of(upcomingTask));
        when(taskRepository.countOverdueAssignedTasks(eq(currentUser.getId()), any())).thenReturn(2L);
        when(taskRepository.countDueTodayAssignedTasks(eq(currentUser.getId()), any())).thenReturn(1L);
        when(taskRepository.countAssignedOpenTasks(currentUser.getId())).thenReturn(4L);
        when(taskRepository.countCompletedAssignedTasksBetween(eq(currentUser.getId()), any(), any()))
                .thenReturn(3L, 5L);
        when(notificationService.getUnreadCount(currentUser.getId())).thenReturn(6L);
        when(taskRepository.existsWorkspaceTasks(currentUser.getId())).thenReturn(true);
        when(projectMemberRepository.getProjectMemberCounts(eq(List.of(ownedProject.getId(), memberProject.getId()))))
                .thenReturn(List.of(
                        projection(ownedProject.getId(), 3L),
                        projection(memberProject.getId(), 5L)
                ));
        when(taskRepository.getProjectTaskStats(eq(List.of(ownedProject.getId(), memberProject.getId()))))
                .thenReturn(List.of(
                        stats(ownedProject.getId(), 10L, 6L, 1L),
                        stats(memberProject.getId(), 4L, 1L, 0L)
                ));
        when(projectMemberRepository.findByUserIdAndProjectIdIn(eq(currentUser.getId()), eq(List.of(ownedProject.getId(), memberProject.getId()))))
                .thenReturn(List.of(ProjectMember.builder()
                        .id(UUID.randomUUID())
                        .project(memberProject)
                        .user(currentUser)
                        .projectRole(ProjectRole.MEMBER)
                        .build()));
        when(activityLogRepository.findRecentByProjectIds(eq(List.of(ownedProject.getId(), memberProject.getId())), any(Pageable.class)))
                .thenReturn(List.of(activityLog));

        DashboardResponse response = dashboardService.getMyDashboard(currentUser.getId(), 5);

        assertThat(response.isHasProjects()).isTrue();
        assertThat(response.isHasTasks()).isTrue();
        assertThat(response.getUpcomingDays()).isEqualTo(5);
        assertThat(response.getKpis().getOverdueTasks()).isEqualTo(2);
        assertThat(response.getKpis().getDueTodayTasks()).isEqualTo(1);
        assertThat(response.getKpis().getAssignedOpenTasks()).isEqualTo(4);
        assertThat(response.getKpis().getCompletedToday()).isEqualTo(3);
        assertThat(response.getKpis().getCompletedThisWeek()).isEqualTo(5);
        assertThat(response.getKpis().getUnreadNotifications()).isEqualTo(6);
        assertThat(response.getMyTasks()).singleElement().satisfies(item -> {
            assertThat(item.getProjectId()).isEqualTo(ownedProject.getId());
            assertThat(item.getProjectName()).isEqualTo("Owned Project");
        });
        assertThat(response.getUpcomingDeadlines()).singleElement().satisfies(item -> {
            assertThat(item.getTaskCode()).isEqualTo("MEM-2");
            assertThat(item.getProjectName()).isEqualTo("Member Project");
        });
        assertThat(response.getRecentActivity()).singleElement().satisfies(item -> {
            assertThat(item.getProjectId()).isEqualTo(ownedProject.getId());
            assertThat(item.getAction()).isEqualTo(ActionType.STATUS_CHANGED);
        });
        assertThat(response.getActiveProjects()).hasSize(2);
    }

    @Test
    void getMyDashboard_rejectsInvalidUpcomingDays() {
        assertThatThrownBy(() -> dashboardService.getMyDashboard(UUID.randomUUID(), 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("upcomingDays");
    }

    private User user(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .fullName(email)
                .passwordHash("secret")
                .systemRole(SystemRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }

    private Project project(String name, User owner) {
        return Project.builder()
                .id(UUID.randomUUID())
                .name(name)
                .projectKey(name.substring(0, Math.min(4, name.length())).toUpperCase())
                .status(ProjectStatus.ACTIVE)
                .visibility(ProjectVisibility.PRIVATE)
                .owner(owner)
                .build();
    }

    private Task task(Project project, User user, String code, String title, TaskStatus status) {
        return Task.builder()
                .id(UUID.randomUUID())
                .project(project)
                .taskCode(code)
                .title(title)
                .taskStatus(status)
                .priority(TaskPriority.HIGH)
                .type(TaskType.TASK)
                .reporter(user)
                .assignee(user)
                .build();
    }

    private ProjectMemberRepository.ProjectMemberCountProjection projection(UUID projectId, long count) {
        return new ProjectMemberRepository.ProjectMemberCountProjection() {
            @Override
            public UUID getProjectId() {
                return projectId;
            }

            @Override
            public Long getMemberCount() {
                return count;
            }
        };
    }

    private TaskRepository.ProjectTaskStatsProjection stats(UUID projectId, long total, long done, long overdue) {
        return new TaskRepository.ProjectTaskStatsProjection() {
            @Override
            public UUID getProjectId() {
                return projectId;
            }

            @Override
            public Long getTotal() {
                return total;
            }

            @Override
            public Long getDone() {
                return done;
            }

            @Override
            public Long getOverdue() {
                return overdue;
            }
        };
    }
}
