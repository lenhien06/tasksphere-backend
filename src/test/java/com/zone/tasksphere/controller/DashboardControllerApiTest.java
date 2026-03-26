package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.response.DashboardResponse;
import com.zone.tasksphere.dto.response.RoleDto;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.entity.enums.ProjectStatus;
import com.zone.tasksphere.entity.enums.ProjectVisibility;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.exception.GlobalExceptionHandler;
import com.zone.tasksphere.security.CustomUserDetail;
import com.zone.tasksphere.service.DashboardService;
import com.zone.tasksphere.utils.CookieUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerApiTest {

    @Mock
    private DashboardService dashboardService;
    @Mock
    private CookieUtils cookieUtils;

    private MockMvc mockMvc;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DashboardController(dashboardService))
                .setControllerAdvice(new GlobalExceptionHandler(cookieUtils))
                .build();

        currentUserId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UserDetail userDetail = UserDetail.builder()
                .id(currentUserId)
                .email("qa@tasksphere.local")
                .fullName("QA User")
                .systemRole(SystemRole.USER)
                .status(UserStatus.ACTIVE)
                .role(RoleDto.builder().slug("USER").displayName("User").build())
                .build();
        CustomUserDetail principal = new CustomUserDetail(
                userDetail.getEmail(),
                "secret",
                true,
                true,
                userDetail,
                List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getMyDashboard_returnsAggregatePayload() throws Exception {
        UUID projectId = UUID.fromString("b364d07b-523b-4a33-a546-9c9b60125694");
        UUID taskId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        DashboardResponse response = DashboardResponse.builder()
                .kpis(DashboardResponse.Kpis.builder()
                        .overdueTasks(2)
                        .dueTodayTasks(1)
                        .assignedOpenTasks(5)
                        .completedToday(1)
                        .completedThisWeek(4)
                        .unreadNotifications(3)
                        .build())
                .myTasks(List.of(
                        DashboardResponse.TaskItem.builder()
                                .id(taskId)
                                .taskCode("TS-101")
                                .title("Fix dashboard API")
                                .projectId(projectId)
                                .projectName("TaskSphere")
                                .status(TaskStatus.IN_PROGRESS)
                                .priority(TaskPriority.HIGH)
                                .dueDate(LocalDate.of(2026, 3, 29))
                                .build()
                ))
                .upcomingDeadlines(List.of())
                .recentActivity(List.of())
                .activeProjects(List.of(
                        DashboardResponse.ProjectSummaryItem.builder()
                                .id(projectId)
                                .name("TaskSphere")
                                .projectKey("TS")
                                .progress(65.0)
                                .taskCount(10L)
                                .memberCount(4L)
                                .overdueCount(1L)
                                .status(ProjectStatus.ACTIVE)
                                .visibility(ProjectVisibility.PRIVATE)
                                .myRole("PROJECT_MANAGER")
                                .build()
                ))
                .hasProjects(true)
                .hasTasks(true)
                .upcomingDays(5)
                .generatedAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();

        when(dashboardService.getMyDashboard(currentUserId, 5)).thenReturn(response);

        mockMvc.perform(get("/api/v1/dashboard/me").param("upcomingDays", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.overdueTasks").value(2))
                .andExpect(jsonPath("$.data.kpis.unreadNotifications").value(3))
                .andExpect(jsonPath("$.data.myTasks[0].projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.data.activeProjects[0].projectKey").value("TS"))
                .andExpect(jsonPath("$.data.hasProjects").value(true))
                .andExpect(jsonPath("$.data.hasTasks").value(true));
    }
}
