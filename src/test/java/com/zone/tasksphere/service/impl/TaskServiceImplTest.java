package com.zone.tasksphere.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.component.DefaultColumnSeeder;
import com.zone.tasksphere.dto.request.UpdateTaskPositionRequest;
import com.zone.tasksphere.dto.request.UpdateTaskStatusRequest;
import com.zone.tasksphere.dto.response.TaskStatusChangedResponse;
import com.zone.tasksphere.dto.response.TimelineViewResponse;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectMember;
import com.zone.tasksphere.entity.ProjectStatusColumn;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.TaskDependency;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.DependencyType;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.ProjectStatus;
import com.zone.tasksphere.entity.enums.ProjectVisibility;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.TaskType;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.exception.StructuredApiException;
import com.zone.tasksphere.mapper.TaskMapper;
import com.zone.tasksphere.repository.ChecklistItemRepository;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.ProjectStatusColumnRepository;
import com.zone.tasksphere.repository.SprintRepository;
import com.zone.tasksphere.repository.TaskDependencyRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.service.ActivityLogService;
import com.zone.tasksphere.service.NotificationService;
import com.zone.tasksphere.service.ReportService;
import com.zone.tasksphere.service.WebSocketService;
import com.zone.tasksphere.utils.TaskCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectStatusColumnRepository columnRepository;
    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ActivityLogService activityLogService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TaskCodeGenerator taskCodeGenerator;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private DefaultColumnSeeder defaultColumnSeeder;
    @Mock
    private TaskDependencyRepository dependencyRepository;
    @Mock
    private ChecklistItemRepository checklistItemRepository;
    @Mock
    private WebSocketService webSocketService;
    @Mock
    private ReportService reportService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TaskServiceImpl service;

    @Test
    void updateStatus_rejectsDoneWhenBlockingTaskIsNotDone() {
        Project project = project();
        User actor = user("member@tasksphere.local");
        Task blocker = task(project, actor, "TS-1", "Task A", TaskStatus.IN_PROGRESS);
        Task blocked = task(project, actor, "TS-2", "Task B", TaskStatus.TODO);

        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest();
        request.setStatus(TaskStatus.DONE);

        when(taskRepository.findByIdAndProjectId(blocked.getId(), project.getId())).thenReturn(Optional.of(blocked));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(projectMemberRepository.findByProjectIdAndUserId(project.getId(), actor.getId()))
                .thenReturn(Optional.of(member(project, actor, ProjectRole.MEMBER)));
        when(taskRepository.findUnfinishedSubtasks(eq(blocked.getId()), any())).thenReturn(List.of());
        when(dependencyRepository.findBlockingTasksByBlockedTaskId(blocked.getId())).thenReturn(List.of(blocker));

        assertThatThrownBy(() -> service.updateStatus(project.getId(), blocked.getId(), request, actor.getId()))
                .isInstanceOf(StructuredApiException.class)
                .satisfies(ex -> {
                    StructuredApiException error = (StructuredApiException) ex;
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(error.getErrorCode()).isEqualTo("TASK_DEPENDENCY_BLOCKED");
                    assertThat(error.getMeta()).containsKey("blockingTasks");
                });

        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void updateStatus_allowsDoneWhenBlockingTaskIsDone() {
        Project project = project();
        User actor = user("member@tasksphere.local");
        Task blocker = task(project, actor, "TS-1", "Task A", TaskStatus.DONE);
        Task blocked = task(project, actor, "TS-2", "Task B", TaskStatus.TODO);

        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest();
        request.setStatus(TaskStatus.DONE);

        stubObjectMapper();

        when(taskRepository.findByIdAndProjectId(blocked.getId(), project.getId())).thenReturn(Optional.of(blocked));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(projectMemberRepository.findByProjectIdAndUserId(project.getId(), actor.getId()))
                .thenReturn(Optional.of(member(project, actor, ProjectRole.MEMBER)));
        when(projectMemberRepository.findFirstByProjectIdAndProjectRoleOrderByJoinedAtAsc(project.getId(), ProjectRole.PROJECT_MANAGER))
                .thenReturn(Optional.empty());
        when(taskRepository.findUnfinishedSubtasks(eq(blocked.getId()), any())).thenReturn(List.of());
        when(dependencyRepository.findBlockingTasksByBlockedTaskId(blocked.getId())).thenReturn(List.of(blocker));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task saved = invocation.getArgument(0);
            saved.setUpdatedAt(Instant.parse("2026-03-27T09:00:00Z"));
            return saved;
        });

        TaskStatusChangedResponse response = service.updateStatus(project.getId(), blocked.getId(), request, actor.getId());

        assertThat(response.getId()).isEqualTo(blocked.getId());
        assertThat(response.getOldStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(response.getNewStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(blocked.getCompletedAt()).isNotNull();
        verify(taskRepository).save(blocked);
        verify(webSocketService).sendToProject(eq(project.getId().toString()), eq("task.status_changed"), any(TaskStatusChangedResponse.class));
    }

    @Test
    void updateStatus_usesRequestedStatusColumnWhenProvided() {
        Project project = project();
        User actor = user("member@tasksphere.local");
        Task task = task(project, actor, "TS-3", "Move me", TaskStatus.TODO);

        ProjectStatusColumn sourceColumn = ProjectStatusColumn.builder()
                .id(UUID.randomUUID())
                .project(project)
                .name("To Do")
                .mappedStatus(TaskStatus.TODO)
                .build();
        ProjectStatusColumn targetColumn = ProjectStatusColumn.builder()
                .id(UUID.randomUUID())
                .project(project)
                .name("In Progress")
                .mappedStatus(TaskStatus.IN_PROGRESS)
                .build();
        task.setStatusColumn(sourceColumn);

        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest();
        request.setStatus(TaskStatus.IN_PROGRESS);
        request.setStatusColumnId(targetColumn.getId());

        stubObjectMapper();

        when(taskRepository.findByIdAndProjectId(task.getId(), project.getId())).thenReturn(Optional.of(task));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(projectMemberRepository.findByProjectIdAndUserId(project.getId(), actor.getId()))
                .thenReturn(Optional.of(member(project, actor, ProjectRole.MEMBER)));
        when(columnRepository.findById(targetColumn.getId())).thenReturn(Optional.of(targetColumn));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> {
            Task saved = invocation.getArgument(0);
            saved.setUpdatedAt(Instant.parse("2026-03-27T10:00:00Z"));
            return saved;
        });

        TaskStatusChangedResponse response = service.updateStatus(project.getId(), task.getId(), request, actor.getId());

        assertThat(task.getTaskStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getStatusColumn()).isEqualTo(targetColumn);
        assertThat(response.getColumnId()).isEqualTo(targetColumn.getId());
        verify(columnRepository, never()).findFirstByProjectIdAndMappedStatusOrderBySortOrderAsc(any(), any());
        verify(taskRepository).save(task);
    }

    @Test
    void updatePosition_movesTaskAcrossColumnsAndReindexesSafely() {
        Project project = project();
        User actor = user("member@tasksphere.local");
        Task movingTask = task(project, actor, "TS-4", "Move me", TaskStatus.TODO);
        Task remainingTodo = task(project, actor, "TS-5", "Stay put", TaskStatus.TODO);

        ProjectStatusColumn sourceColumn = ProjectStatusColumn.builder()
                .id(UUID.randomUUID())
                .project(project)
                .name("To Do")
                .mappedStatus(TaskStatus.TODO)
                .build();
        ProjectStatusColumn targetColumn = ProjectStatusColumn.builder()
                .id(UUID.randomUUID())
                .project(project)
                .name("In Progress")
                .mappedStatus(TaskStatus.IN_PROGRESS)
                .build();
        movingTask.setStatusColumn(sourceColumn);
        movingTask.setTaskPosition(0);
        movingTask.setUpdatedAt(Instant.parse("2026-04-08T00:00:00Z"));
        remainingTodo.setStatusColumn(sourceColumn);
        remainingTodo.setTaskPosition(1);

        UpdateTaskPositionRequest request = new UpdateTaskPositionRequest();
        request.setStatusColumnId(targetColumn.getId());
        request.setNewPosition(0);

        stubObjectMapper();

        when(taskRepository.findByIdAndProjectId(movingTask.getId(), project.getId())).thenReturn(Optional.of(movingTask));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(projectMemberRepository.existsByProjectIdAndUserId(project.getId(), actor.getId())).thenReturn(true);
        when(columnRepository.findById(targetColumn.getId())).thenReturn(Optional.of(targetColumn));
        when(taskRepository.findByProjectIdAndStatusColumnIdOrderByTaskPositionAsc(project.getId(), sourceColumn.getId()))
                .thenReturn(List.of(movingTask, remainingTodo));
        when(taskRepository.findByProjectIdAndStatusColumnIdOrderByTaskPositionAsc(project.getId(), targetColumn.getId()))
                .thenReturn(List.of());
        when(taskRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Task> saved = invocation.getArgument(0);
            saved.forEach(task -> {
                if (task.getId().equals(movingTask.getId())) {
                    task.setUpdatedAt(Instant.parse("2026-04-08T00:05:00Z"));
                }
            });
            return saved;
        });

        service.updatePosition(project.getId(), movingTask.getId(), request, actor.getId());

        assertThat(movingTask.getStatusColumn()).isEqualTo(targetColumn);
        assertThat(movingTask.getTaskStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(movingTask.getTaskPosition()).isEqualTo(0);
        assertThat(remainingTodo.getTaskPosition()).isEqualTo(0);
        verify(taskRepository).saveAll(argThat(tasks -> {
            List<Task> saved = toList(tasks);
            return saved.size() == 1 && saved.get(0).getId().equals(remainingTodo.getId());
        }));
        verify(taskRepository).saveAll(argThat(tasks -> {
            List<Task> saved = toList(tasks);
            return saved.size() == 1 && saved.get(0).getId().equals(movingTask.getId());
        }));
        verify(webSocketService).sendToProject(eq(project.getId().toString()), eq("task.position_updated"), any());
    }

    @Test
    void getTimelineView_returnsTasksAndDependencies() {
        Project project = project();
        User actor = user("member@tasksphere.local");
        User assignee = user("dev@tasksphere.local");

        Task blocker = task(project, assignee, "TS-1", "Task A", TaskStatus.DONE);
        blocker.setStartDate(LocalDate.of(2026, 3, 25));
        blocker.setDueDate(LocalDate.of(2026, 3, 26));

        Task blocked = task(project, assignee, "TS-2", "Task B", TaskStatus.IN_PROGRESS);
        blocked.setStartDate(LocalDate.of(2026, 3, 27));
        blocked.setDueDate(LocalDate.of(2026, 3, 30));
        blocked.setParentTask(blocker);

        TaskDependency edge = TaskDependency.builder()
                .id(UUID.randomUUID())
                .blockingTask(blocker)
                .blockedTask(blocked)
                .linkType(DependencyType.BLOCKS)
                .createdBy(actor)
                .build();

        when(projectMemberRepository.existsByProjectIdAndUserId(project.getId(), actor.getId())).thenReturn(true);
        when(taskRepository.findTimelineTasksByProjectId(project.getId())).thenReturn(List.of(blocker, blocked));
        when(dependencyRepository.findBlockingEdgesByProjectId(project.getId())).thenReturn(List.of(edge));

        TimelineViewResponse response = service.getTimelineView(project.getId(), actor.getId());

        assertThat(response.getProjectId()).isEqualTo(project.getId());
        assertThat(response.getTotalTasks()).isEqualTo(2);
        assertThat(response.getTotalDependencies()).isEqualTo(1);
        assertThat(response.getDependencies()).singleElement().satisfies(item -> {
            assertThat(item.getLinkType()).isEqualTo("BLOCKS");
            assertThat(item.getBlockerTaskId()).isEqualTo(blocker.getId());
            assertThat(item.getBlockedTaskId()).isEqualTo(blocked.getId());
        });
        assertThat(response.getTasks()).hasSize(2);

        TimelineViewResponse.TimelineTaskItem blockedItem = response.getTasks().stream()
                .filter(item -> item.getId().equals(blocked.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(blockedItem.getBlockedBy()).singleElement().satisfies(ref -> {
            assertThat(ref.getTaskId()).isEqualTo(blocker.getId());
            assertThat(ref.getLinkType()).isEqualTo("BLOCKED_BY");
        });

        TimelineViewResponse.TimelineTaskItem blockerItem = response.getTasks().stream()
                .filter(item -> item.getId().equals(blocker.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(blockerItem.getBlocking()).singleElement().satisfies(ref -> {
            assertThat(ref.getTaskId()).isEqualTo(blocked.getId());
            assertThat(ref.getLinkType()).isEqualTo("BLOCKS");
        });
    }

    private Project project() {
        User owner = user("owner@tasksphere.local");
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

    private Task task(Project project, User actor, String code, String title, TaskStatus status) {
        return Task.builder()
                .id(UUID.randomUUID())
                .project(project)
                .taskCode(code)
                .title(title)
                .taskStatus(status)
                .priority(TaskPriority.HIGH)
                .type(TaskType.TASK)
                .reporter(actor)
                .assignee(actor)
                .build();
    }

    private ProjectMember member(Project project, User user, ProjectRole role) {
        return ProjectMember.builder()
                .id(UUID.randomUUID())
                .project(project)
                .user(user)
                .projectRole(role)
                .build();
    }

    private void stubObjectMapper() {
        try {
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Task> toList(Iterable<Task> tasks) {
        if (tasks instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Task> typed = (List<Task>) list;
            return typed;
        }
        java.util.ArrayList<Task> collected = new java.util.ArrayList<>();
        tasks.forEach(collected::add);
        return collected;
    }
}
