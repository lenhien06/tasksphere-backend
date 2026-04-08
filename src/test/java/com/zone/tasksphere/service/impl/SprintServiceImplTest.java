package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectMember;
import com.zone.tasksphere.entity.Sprint;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.NotificationType;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.ProjectStatus;
import com.zone.tasksphere.entity.enums.ProjectVisibility;
import com.zone.tasksphere.entity.enums.SprintStatus;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.mapper.TaskMapper;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.SprintRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.service.ActivityLogService;
import com.zone.tasksphere.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SprintServiceImplTest {

    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TaskMapper taskMapper;

    @InjectMocks
    private SprintServiceImpl service;

    @Test
    void autoCloseExpiredSprints_completesSprintAndMovesUnfinishedTasksToBacklog() {
        User owner = user("owner@tasksphere.local");
        User pm = user("pm@tasksphere.local");
        Project project = project(owner);
        Sprint sprint = Sprint.builder()
                .id(UUID.randomUUID())
                .project(project)
                .name("Sprint 1")
                .status(SprintStatus.ACTIVE)
                .startDate(LocalDate.now().minusDays(14))
                .endDate(LocalDate.now().minusDays(1))
                .tasks(List.of())
                .build();
        Task unfinished = task(project, "TS-101", TaskStatus.IN_PROGRESS);
        ProjectMember pmMember = ProjectMember.builder()
                .id(UUID.randomUUID())
                .project(project)
                .user(pm)
                .projectRole(ProjectRole.PROJECT_MANAGER)
                .joinedAt(Instant.now().minusSeconds(3600))
                .build();

        when(sprintRepository.findByStatusAndEndDateBeforeAndDeletedAtIsNull(
                eq(SprintStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(sprint));
        when(projectMemberRepository.findFirstByProjectIdAndProjectRoleOrderByJoinedAtAsc(
                project.getId(), ProjectRole.PROJECT_MANAGER))
                .thenReturn(Optional.of(pmMember));
        when(userRepository.findById(pm.getId())).thenReturn(Optional.of(pm));
        when(sprintRepository.findUnfinishedTasksBySprintId(sprint.getId())).thenReturn(List.of(unfinished));
        when(sprintRepository.countTasksBySprintId(sprint.getId())).thenReturn(1L);
        when(sprintRepository.countDoneTasksBySprintId(sprint.getId())).thenReturn(0L);
        when(sprintRepository.calculateVelocity(sprint.getId())).thenReturn(0);
        when(taskRepository.batchMoveToBacklog(List.of(unfinished.getId()), project.getId())).thenReturn(1);
        when(sprintRepository.save(any(Sprint.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectMemberRepository.findByProjectId(project.getId())).thenReturn(List.of(pmMember));

        service.autoCloseExpiredSprints();

        assertThat(sprint.getStatus()).isEqualTo(SprintStatus.COMPLETED);
        assertThat(sprint.getCompletedAt()).isNotNull();
        verify(taskRepository).batchMoveToBacklog(List.of(unfinished.getId()), project.getId());
        verify(notificationService).sendSprintCompleted(eq(sprint), eq(List.of(pm)), eq(pm));
        verify(notificationService).createNotification(
                eq(pm),
                eq(NotificationType.SPRINT_COMPLETED),
                any(String.class),
                any(String.class),
                eq("SPRINT"),
                eq(sprint.getId()));
    }

    private Project project(User owner) {
        return Project.builder()
                .id(UUID.randomUUID())
                .name("TaskSphere")
                .projectKey("TS")
                .status(ProjectStatus.ACTIVE)
                .visibility(ProjectVisibility.PRIVATE)
                .owner(owner)
                .build();
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

    private Task task(Project project, String code, TaskStatus status) {
        return Task.builder()
                .id(UUID.randomUUID())
                .project(project)
                .title(code)
                .taskCode(code)
                .taskStatus(status)
                .build();
    }
}
