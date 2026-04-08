package com.zone.tasksphere.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.dto.response.CalendarViewResponse;
import com.zone.tasksphere.dto.response.RoleDto;
import com.zone.tasksphere.dto.response.TimelineViewResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.TaskPriority;
import com.zone.tasksphere.entity.enums.TaskStatus;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.exception.GlobalExceptionHandler;
import com.zone.tasksphere.exception.StructuredApiException;
import com.zone.tasksphere.security.CustomUserDetail;
import com.zone.tasksphere.service.TaskService;
import com.zone.tasksphere.utils.CookieUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaskControllerApiTest {

    @Mock
    private TaskService taskService;
    @Mock
    private CookieUtils cookieUtils;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TaskController(taskService))
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
    void getTimeline_returnsTaskAndDependencyPayload() throws Exception {
        UUID projectId = UUID.fromString("b364d07b-523b-4a33-a546-9c9b60125694");
        UUID taskAId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID taskBId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID linkId = UUID.fromString("99999999-9999-9999-9999-999999999999");

        TimelineViewResponse response = TimelineViewResponse.builder()
                .projectId(projectId)
                .totalTasks(2)
                .totalDependencies(1)
                .tasks(List.of(
                        TimelineViewResponse.TimelineTaskItem.builder()
                                .id(taskAId)
                                .taskCode("TS-10")
                                .title("Design API contract")
                                .status(TaskStatus.DONE)
                                .priority(TaskPriority.HIGH)
                                .startDate(LocalDate.of(2026, 3, 25))
                                .endDate(LocalDate.of(2026, 3, 26))
                                .dueDate(LocalDate.of(2026, 3, 27))
                                .blockedBy(List.of())
                                .blocking(List.of(
                                        TimelineViewResponse.DependencyRef.builder()
                                                .linkId(linkId)
                                                .taskId(taskBId)
                                                .taskCode("TS-11")
                                                .title("Implement backend")
                                                .linkType("BLOCKS")
                                                .build()
                                ))
                                .build(),
                        TimelineViewResponse.TimelineTaskItem.builder()
                                .id(taskBId)
                                .taskCode("TS-11")
                                .title("Implement backend")
                                .status(TaskStatus.IN_PROGRESS)
                                .priority(TaskPriority.HIGH)
                                .startDate(LocalDate.of(2026, 3, 27))
                                .endDate(LocalDate.of(2026, 3, 29))
                                .dueDate(LocalDate.of(2026, 3, 30))
                                .parentTaskId(taskAId)
                                .blockedBy(List.of(
                                        TimelineViewResponse.DependencyRef.builder()
                                                .linkId(linkId)
                                                .taskId(taskAId)
                                                .taskCode("TS-10")
                                                .title("Design API contract")
                                                .linkType("BLOCKED_BY")
                                                .build()
                                ))
                                .blocking(List.of())
                                .build()
                ))
                .dependencies(List.of(
                        TimelineViewResponse.TimelineDependencyEdge.builder()
                                .linkId(linkId)
                                .linkType("BLOCKS")
                                .sourceTaskId(taskAId)
                                .targetTaskId(taskBId)
                                .blockerTaskId(taskAId)
                                .blockerTaskCode("TS-10")
                                .blockerTitle("Design API contract")
                                .blockedTaskId(taskBId)
                                .blockedTaskCode("TS-11")
                                .blockedTaskTitle("Implement backend")
                                .build()
                ))
                .build();

        when(taskService.getTimelineView(projectId, currentUserId)).thenReturn(response);

        String json = mockMvc.perform(get("/api/v1/projects/{projectId}/tasks/timeline", projectId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(projectId.toString()))
                .andExpect(jsonPath("$.data.totalTasks").value(2))
                .andExpect(jsonPath("$.data.totalDependencies").value(1))
                .andExpect(jsonPath("$.data.tasks[0].taskCode").value("TS-10"))
                .andExpect(jsonPath("$.data.tasks[1].blockedBy[0].taskCode").value("TS-10"))
                .andExpect(jsonPath("$.data.dependencies[0].blockedTaskCode").value("TS-11"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(json).contains("\"projectId\":\"b364d07b-523b-4a33-a546-9c9b60125694\"");
        System.out.println("TIMELINE_RESPONSE=" + json);
    }

    @Test
    void getCalendar_serializesIsOverdueFlag() throws Exception {
        UUID projectId = UUID.fromString("b364d07b-523b-4a33-a546-9c9b60125694");
        UUID taskId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID assigneeId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        CalendarViewResponse response = CalendarViewResponse.builder()
                .year(2026)
                .month(4)
                .totalTasks(1)
                .tasks(List.of(
                        CalendarViewResponse.CalendarTaskItem.builder()
                                .id(taskId)
                                .taskCode("TS-12")
                                .title("Fix overdue badge")
                                .priority(TaskPriority.HIGH)
                                .taskStatus(TaskStatus.IN_PROGRESS)
                                .dueDate(LocalDate.of(2026, 4, 2))
                                .isOverdue(true)
                                .assignee(CalendarViewResponse.UserSummary.builder()
                                        .id(assigneeId)
                                        .fullName("QA User")
                                        .avatarUrl("https://example.com/avatar.png")
                                        .build())
                                .build()
                ))
                .build();

        when(taskService.getCalendarView(
                eq(projectId),
                eq(2026),
                eq(4),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(currentUserId)
        )).thenReturn(response);

        String json = mockMvc.perform(get("/api/v1/projects/{projectId}/tasks/calendar", projectId)
                        .param("year", "2026")
                        .param("month", "4")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalTasks").value(1))
                .andExpect(jsonPath("$.data.tasks[0].taskCode").value("TS-12"))
                .andExpect(jsonPath("$.data.tasks[0].isOverdue").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(json).contains("\"isOverdue\":true");
    }

    @Test
    void updateStatus_returnsStructuredBlockedErrorResponse() throws Exception {
        UUID projectId = UUID.fromString("b364d07b-523b-4a33-a546-9c9b60125694");
        UUID taskId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        StructuredApiException blocked = new StructuredApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "TASK_DEPENDENCY_BLOCKED",
                "Task không thể chuyển sang DONE vì còn dependency blocker chưa hoàn thành",
                Map.of(
                        "blockingTasks", List.of(
                                Map.of(
                                        "id", "11111111-1111-1111-1111-111111111111",
                                        "taskCode", "TS-10",
                                        "title", "Design API contract",
                                        "reason", "Task blocker chưa ở trạng thái DONE"
                                )
                        )
                )
        );

        doThrow(blocked).when(taskService)
                .updateStatus(eq(projectId), eq(taskId), any(), eq(currentUserId));

        String json = mockMvc.perform(patch("/api/v1/projects/{projectId}/tasks/{taskId}/status", projectId, taskId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DONE"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("TASK_DEPENDENCY_BLOCKED"))
                .andExpect(jsonPath("$.message").value("Task không thể chuyển sang DONE vì còn dependency blocker chưa hoàn thành"))
                .andExpect(jsonPath("$.meta.blockingTasks[0].taskCode").value("TS-10"))
                .andExpect(jsonPath("$.meta.blockingTasks[0].title").value("Design API contract"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(json).contains("\"error\":\"TASK_DEPENDENCY_BLOCKED\"");
        System.out.println("BLOCKED_RESPONSE=" + json);
    }
}
