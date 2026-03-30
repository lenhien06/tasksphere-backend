package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.response.ProjectResponse;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectMember;
import com.zone.tasksphere.entity.ProjectView;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.ProjectStatus;
import com.zone.tasksphere.entity.enums.ProjectVisibility;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.ProjectStatusColumnRepository;
import com.zone.tasksphere.repository.ProjectViewRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectStatusColumnRepository columnRepository;
    @Mock
    private ProjectViewRepository projectViewRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private com.zone.tasksphere.service.impl.MinioStorageService minioStorageService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query nativeQuery;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private EmailService emailService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private com.zone.tasksphere.component.DefaultColumnSeeder defaultColumnSeeder;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void getProjectById_recordsViewForAuthenticatedNonMemberOnPublicProject() {
        User owner = user("owner@tasksphere.local");
        User viewer = user("viewer@tasksphere.local");
        Project project = publicProject(owner);

        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(project.getId(), viewer.getId())).thenReturn(false);
        when(projectViewRepository.findByProjectIdAndUserId(project.getId(), viewer.getId())).thenReturn(Optional.empty());
        when(userRepository.getReferenceById(viewer.getId())).thenReturn(viewer);

        ProjectResponse response = projectService.getProjectById(project.getId(), viewer.getId(), false);

        ArgumentCaptor<ProjectView> captor = ArgumentCaptor.forClass(ProjectView.class);
        verify(projectViewRepository).save(captor.capture());
        ProjectView savedView = captor.getValue();

        assertThat(response.getId()).isEqualTo(project.getId());
        assertThat(savedView.getProject()).isEqualTo(project);
        assertThat(savedView.getUser()).isEqualTo(viewer);
        assertThat(savedView.getLastViewedAt()).isNotNull();
    }

    @Test
    void getProjectById_doesNotRecordViewForProjectMember() {
        User owner = user("owner@tasksphere.local");
        User member = user("member@tasksphere.local");
        Project project = publicProject(owner);

        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(project.getId(), member.getId())).thenReturn(true);
        when(projectMemberRepository.findByProjectIdAndUserId(project.getId(), member.getId()))
                .thenReturn(Optional.of(ProjectMember.builder()
                        .id(UUID.randomUUID())
                        .project(project)
                        .user(member)
                        .projectRole(ProjectRole.MEMBER)
                        .build()));

        projectService.getProjectById(project.getId(), member.getId(), false);

        verify(projectViewRepository, never()).save(any());
    }

    @Test
    void getProjectById_rejectsPrivateProjectForNonMember() {
        User owner = user("owner@tasksphere.local");
        User stranger = user("stranger@tasksphere.local");
        Project project = privateProject(owner);

        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        when(projectMemberRepository.existsByProjectIdAndUserId(project.getId(), stranger.getId())).thenReturn(false);

        assertThatThrownBy(() -> projectService.getProjectById(project.getId(), stranger.getId(), false))
                .isInstanceOf(Forbidden.class)
                .hasMessageContaining("permission");
    }

    @Test
    void getProjects_marksVisitedPublicProjectsAsVisibleRoleViewer() {
        User owner = user("owner@tasksphere.local");
        User viewer = user("viewer@tasksphere.local");
        Project project = publicProject(owner);

        when(projectRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(PageRequest.of(0, 10))))
                .thenReturn(new PageImpl<>(List.of(project), PageRequest.of(0, 10), 1));
        when(projectMemberRepository.getProjectMemberCounts(List.of(project.getId()))).thenReturn(List.of());
        when(taskRepository.getProjectTaskStats(List.of(project.getId()))).thenReturn(List.of());
        when(projectMemberRepository.findByUserIdAndProjectIdIn(viewer.getId(), List.of(project.getId()))).thenReturn(List.of());

        var page = projectService.getProjects(null, ProjectStatus.ACTIVE, null, viewer.getId(), false, PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(project.getId());
            assertThat(item.getMyRole()).isEqualTo("VIEWER");
            assertThat(item.getVisibility()).isEqualTo(ProjectVisibility.PUBLIC);
        });
    }

    @Test
    void deleteProjectPermanently_removesProjectAndCleansStorageKeys() {
        User owner = user("owner@tasksphere.local");
        Project project = publicProject(owner);

        when(projectRepository.findByIdWithDeleted(project.getId())).thenReturn(Optional.of(project));
        when(entityManager.createNativeQuery(any(String.class))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("projectId"), eq(project.getId()))).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(eq("depth"), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of("attachments/demo/file.txt"));
        when(nativeQuery.getSingleResult()).thenReturn(1, 1);
        when(nativeQuery.executeUpdate()).thenReturn(1);

        projectService.deleteProjectPermanently(project.getId(), project.getName(), owner.getId(), false);

        verify(projectRepository, never()).delete(org.mockito.ArgumentMatchers.isA(Project.class));
        verify(projectRepository, never()).flush();
        verify(entityManager, atLeastOnce()).createNativeQuery(any(String.class));
        verify(minioStorageService).deleteFile("attachments/demo/file.txt");
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

    private Project publicProject(User owner) {
        return Project.builder()
                .id(UUID.randomUUID())
                .name("Public Roadmap")
                .projectKey("ROAD")
                .status(ProjectStatus.ACTIVE)
                .visibility(ProjectVisibility.PUBLIC)
                .owner(owner)
                .build();
    }

    private Project privateProject(User owner) {
        return Project.builder()
                .id(UUID.randomUUID())
                .name("Private Ops")
                .projectKey("POPS")
                .status(ProjectStatus.ACTIVE)
                .visibility(ProjectVisibility.PRIVATE)
                .owner(owner)
                .build();
    }
}
