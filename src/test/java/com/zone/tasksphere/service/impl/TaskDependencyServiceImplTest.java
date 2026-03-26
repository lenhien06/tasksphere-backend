package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.AddDependencyRequest;
import com.zone.tasksphere.dto.response.DependencyResponse;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectMember;
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
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.TaskDependencyRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskDependencyServiceImplTest {

    @Mock
    private TaskDependencyRepository dependencyRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectMemberRepository memberRepository;

    @InjectMocks
    private TaskDependencyServiceImpl service;

    @Test
    void addDependency_createsBlocksAndInverseLink() {
        Project project = project();
        User actor = user("pm@tasksphere.local");
        Task source = task(project, actor, "TS-1", "Task A", TaskStatus.IN_PROGRESS);
        Task target = task(project, actor, "TS-2", "Task B", TaskStatus.TODO);

        AddDependencyRequest request = new AddDependencyRequest();
        request.setTargetTaskId(target.getId());
        request.setLinkType(DependencyType.BLOCKS);

        when(taskRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(taskRepository.findByIdAndProjectId(target.getId(), project.getId())).thenReturn(Optional.of(target));
        when(memberRepository.findByProjectIdAndUserId(project.getId(), actor.getId()))
                .thenReturn(Optional.of(member(project, actor, ProjectRole.MEMBER)));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(dependencyRepository.existsByBlockingTaskIdAndBlockedTaskId(any(UUID.class), any(UUID.class))).thenReturn(false);
        when(dependencyRepository.save(any(TaskDependency.class))).thenAnswer(invocation -> {
            TaskDependency dependency = invocation.getArgument(0);
            if (dependency.getId() == null) {
                dependency.setId(UUID.randomUUID());
            }
            if (dependency.getCreatedAt() == null) {
                dependency.setCreatedAt(Instant.parse("2026-03-27T08:00:00Z"));
            }
            return dependency;
        });

        DependencyResponse response = service.addDependency(project.getId(), source.getId(), request, actor.getId());

        assertThat(response.getLinkType()).isEqualTo(DependencyType.BLOCKS);
        assertThat(response.getTask().getId()).isEqualTo(source.getId());
        assertThat(response.getDependsOnTask().getId()).isEqualTo(target.getId());

        ArgumentCaptor<TaskDependency> captor = ArgumentCaptor.forClass(TaskDependency.class);
        verify(dependencyRepository, times(2)).save(captor.capture());

        List<TaskDependency> savedDependencies = captor.getAllValues();
        assertThat(savedDependencies).hasSize(2);

        TaskDependency direct = savedDependencies.get(0);
        assertThat(direct.getBlockingTask().getId()).isEqualTo(source.getId());
        assertThat(direct.getBlockedTask().getId()).isEqualTo(target.getId());
        assertThat(direct.getLinkType()).isEqualTo(DependencyType.BLOCKS);

        TaskDependency inverse = savedDependencies.get(1);
        assertThat(inverse.getBlockingTask().getId()).isEqualTo(target.getId());
        assertThat(inverse.getBlockedTask().getId()).isEqualTo(source.getId());
        assertThat(inverse.getLinkType()).isEqualTo(DependencyType.BLOCKED_BY);
    }

    @Test
    void addDependency_rejectsSelfDependency() {
        Project project = project();
        User actor = user("pm@tasksphere.local");
        Task source = task(project, actor, "TS-1", "Task A", TaskStatus.IN_PROGRESS);

        AddDependencyRequest request = new AddDependencyRequest();
        request.setTargetTaskId(source.getId());
        request.setLinkType(DependencyType.BLOCKS);

        when(taskRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(memberRepository.findByProjectIdAndUserId(project.getId(), actor.getId()))
                .thenReturn(Optional.of(member(project, actor, ProjectRole.MEMBER)));

        assertThatThrownBy(() -> service.addDependency(project.getId(), source.getId(), request, actor.getId()))
                .isInstanceOf(StructuredApiException.class)
                .satisfies(ex -> {
                    StructuredApiException error = (StructuredApiException) ex;
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getErrorCode()).isEqualTo("DEPENDENCY_SELF_REFERENCE");
                });
    }

    @Test
    void addDependency_rejectsCircularDependency() {
        Project project = project();
        User actor = user("pm@tasksphere.local");
        Task source = task(project, actor, "TS-1", "Task A", TaskStatus.IN_PROGRESS);
        Task target = task(project, actor, "TS-2", "Task B", TaskStatus.TODO);

        AddDependencyRequest request = new AddDependencyRequest();
        request.setTargetTaskId(target.getId());
        request.setLinkType(DependencyType.BLOCKS);

        when(taskRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(taskRepository.findByIdAndProjectId(target.getId(), project.getId())).thenReturn(Optional.of(target));
        when(memberRepository.findByProjectIdAndUserId(project.getId(), actor.getId()))
                .thenReturn(Optional.of(member(project, actor, ProjectRole.MEMBER)));
        when(dependencyRepository.existsByBlockingTaskIdAndBlockedTaskId(any(UUID.class), any(UUID.class))).thenReturn(false);
        when(dependencyRepository.findDependsOnIdsByTaskId(source.getId())).thenReturn(List.of(target.getId()));

        assertThatThrownBy(() -> service.addDependency(project.getId(), source.getId(), request, actor.getId()))
                .isInstanceOf(StructuredApiException.class)
                .satisfies(ex -> {
                    StructuredApiException error = (StructuredApiException) ex;
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(error.getErrorCode()).isEqualTo("DEPENDENCY_CYCLE_DETECTED");
                });
    }

    @Test
    void addDependency_rejectsViewer() {
        Project project = project();
        User viewer = user("viewer@tasksphere.local");
        Task source = task(project, viewer, "TS-1", "Task A", TaskStatus.IN_PROGRESS);

        AddDependencyRequest request = new AddDependencyRequest();
        request.setTargetTaskId(UUID.randomUUID());
        request.setLinkType(DependencyType.BLOCKS);

        when(taskRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(memberRepository.findByProjectIdAndUserId(project.getId(), viewer.getId()))
                .thenReturn(Optional.of(member(project, viewer, ProjectRole.VIEWER)));

        assertThatThrownBy(() -> service.addDependency(project.getId(), source.getId(), request, viewer.getId()))
                .isInstanceOf(StructuredApiException.class)
                .satisfies(ex -> {
                    StructuredApiException error = (StructuredApiException) ex;
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.getErrorCode()).isEqualTo("DEPENDENCY_WRITE_FORBIDDEN");
                });

        verify(dependencyRepository, never()).save(any(TaskDependency.class));
    }

    @Test
    void removeDependency_rejectsViewer() {
        Project project = project();
        User viewer = user("viewer@tasksphere.local");
        User owner = user("pm@tasksphere.local");
        Task source = task(project, owner, "TS-1", "Task A", TaskStatus.IN_PROGRESS);
        Task target = task(project, owner, "TS-2", "Task B", TaskStatus.TODO);
        TaskDependency dependency = TaskDependency.builder()
                .id(UUID.randomUUID())
                .blockingTask(source)
                .blockedTask(target)
                .linkType(DependencyType.BLOCKS)
                .createdBy(owner)
                .build();

        when(taskRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(dependencyRepository.findById(dependency.getId())).thenReturn(Optional.of(dependency));
        when(memberRepository.findByProjectIdAndUserId(project.getId(), viewer.getId()))
                .thenReturn(Optional.of(member(project, viewer, ProjectRole.VIEWER)));

        assertThatThrownBy(() -> service.removeDependency(project.getId(), source.getId(), dependency.getId(), viewer.getId()))
                .isInstanceOf(StructuredApiException.class)
                .satisfies(ex -> {
                    StructuredApiException error = (StructuredApiException) ex;
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(error.getErrorCode()).isEqualTo("DEPENDENCY_WRITE_FORBIDDEN");
                });

        verify(dependencyRepository, never()).delete(any(TaskDependency.class));
    }

    private Project project() {
        return Project.builder()
                .id(UUID.randomUUID())
                .name("TaskSphere")
                .projectKey("TS")
                .status(ProjectStatus.ACTIVE)
                .visibility(ProjectVisibility.PRIVATE)
                .owner(user("owner@tasksphere.local"))
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

    private Task task(Project project, User reporter, String code, String title, TaskStatus status) {
        return Task.builder()
                .id(UUID.randomUUID())
                .project(project)
                .reporter(reporter)
                .assignee(reporter)
                .taskCode(code)
                .title(title)
                .taskStatus(status)
                .priority(TaskPriority.MEDIUM)
                .type(TaskType.TASK)
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
}
