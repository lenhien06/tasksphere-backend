package com.zone.tasksphere.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.ai.config.LlmClient;
import com.zone.tasksphere.ai.dto.*;
import com.zone.tasksphere.ai.entity.AiTaskAssignment;
import com.zone.tasksphere.ai.repository.AiTaskAssignmentRepository;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.*;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.*;
import com.zone.tasksphere.utils.TaskCodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceAiService {

        // ── System Prompts ────────────────────────────────────────────────────────

        private static final String SYS_ANALYZE = """
                        Bạn là AI assistant cho TaskSphere — hệ thống quản lý dự án.
                        Phân tích mô tả dự án và trả về JSON. KHÔNG có text nào khác ngoài JSON.
                        KHÔNG dùng markdown code block.

                        Schema:
                        {
                          "projectName": "Tên dự án tiếng Anh (PascalCase hoặc Title Case)",
                          "projectType": "web app | mobile app | api | tool | other",
                          "features": ["feature 1", "feature 2"],
                          "techStack": "React,Node.js,..." or null,
                          "deadlineWeeks": number or null,
                          "missingFields": ["techStack", "deadline", "teamMembers"]
                        }

                        missingFields chỉ chứa các field còn thiếu trong số: techStack, deadline, teamMembers.
                        """;

        private static final String SYS_GENERATE_PLAN = """
                        Bạn là AI assistant cho hệ thống quản lý dự án Agile/Scrum TaskSphere.
                        Nhiệm vụ: sinh project plan hoàn chỉnh từ thông tin đã thu thập.
                        LUÔN trả về JSON hợp lệ, KHÔNG có text nào khác. KHÔNG dùng markdown code block.

                        QUY TẮC:
                        - Chia sprint đều nhau, mỗi sprint 1-2 tuần.
                        - Sprint 1: setup, auth, infrastructure (priority: high).
                        - Sprint cuối: testing, polish, deployment.
                        - Mỗi sprint 3-6 tasks, không quá nhiều.
                        - Story points: 1-2 (đơn giản), 3-5 (trung bình), 8-13 (phức tạp).
                        - suggestedAssigneeId: chọn userId có skillTags phù hợp nhất với task. Có thể null nếu không ai phù hợp.
                        - projectKey: 2-6 ký tự IN HOA từ tên project (VD: PFM, SHOP, HR).
                        - type: "task" | "story" | "bug".
                        - priority: "critical" | "high" | "medium" | "low".

                        JSON Schema:
                        {
                          "project": {
                            "name": "string",
                            "projectKey": "string",
                            "description": "string (2-3 câu mô tả dự án)",
                            "estimatedWeeks": number
                          },
                          "sprints": [
                            {
                              "name": "Sprint N — Theme",
                              "goal": "string",
                              "weekStart": number,
                              "weekEnd": number,
                              "tasks": [
                                {
                                  "title": "string (bắt đầu bằng động từ, ≤80 ký tự)",
                                  "type": "task|story|bug",
                                  "priority": "critical|high|medium|low",
                                  "storyPoints": number,
                                  "skillTagsRequired": ["string"],
                                  "suggestedAssigneeId": "uuid or null"
                                }
                              ]
                            }
                          ],
                          "totalTasks": number,
                          "totalStoryPoints": number
                        }
                        """;

        // ── Dependencies ──────────────────────────────────────────────────────────

        private final LlmClient llmClient;
        private final ObjectMapper objectMapper;
        private final WorkspaceRepository workspaceRepository;
        private final WorkspaceMemberRepository workspaceMemberRepository;
        private final ProjectRepository projectRepository;
        private final ProjectMemberRepository projectMemberRepository;
        private final UserRepository userRepository;
        private final SprintRepository sprintRepository;
        private final TaskRepository taskRepository;
        private final AiTaskAssignmentRepository aiAssignmentRepository;
        private final TaskCodeGenerator taskCodeGenerator;

        // ═════════════════════════════════════════════════════════════════════════
        // 1. Analyze Description
        // ═════════════════════════════════════════════════════════════════════════

        /**
         * AI phân tích mô tả, trả về những gì đã hiểu và danh sách câu hỏi còn thiếu.
         * LLM call happens outside any transaction.
         */
        public AnalyzeDescriptionResponse analyzeDescription(UUID wsId,
                        AnalyzeDescriptionRequest req,
                        UUID currentUserId) {
                requireWorkspaceMember(wsId, currentUserId);

                log.info("[WS-AI] analyze-description ws={} user={}", wsId, currentUserId);

                // ── LLM call ─────────────────────────────────────────────────────────
                String userMsg = "Mô tả dự án: \"" + req.getDescription() + "\"";
                String raw = llmClient.call(SYS_ANALYZE, userMsg);

                JsonNode parsed = parseJson(raw, "analyze-description");

                AnalyzeDescriptionResponse.UnderstoodFields understood = AnalyzeDescriptionResponse.UnderstoodFields
                                .builder()
                                .projectName(parsed.path("projectName").asText("Untitled Project"))
                                .projectType(parsed.path("projectType").asText("web app"))
                                .features(jsonArrayToList(parsed.path("features")))
                                .build();

                List<String> missingFields = jsonArrayToList(parsed.path("missingFields"));

                // ── Build questions ───────────────────────────────────────────────────
                List<AnalyzeDescriptionResponse.AiQuestion> questions = new ArrayList<>();

                if (missingFields.contains("techStack") || parsed.path("techStack").isNull()) {
                        questions.add(buildTechStackQuestion());
                }
                if (missingFields.contains("deadline") || parsed.path("deadlineWeeks").isNull()) {
                        questions.add(buildDeadlineQuestion());
                }
                // Team members: always ask (shows WS members with skill tags)
                questions.add(buildMemberSelectQuestion(wsId));

                log.info("[WS-AI] analyze-description done: missing={}", missingFields);
                return AnalyzeDescriptionResponse.builder()
                                .understood(understood)
                                .missingFields(missingFields)
                                .questions(questions)
                                .build();
        }

        // ═════════════════════════════════════════════════════════════════════════
        // 2. Generate Project Plan
        // ═════════════════════════════════════════════════════════════════════════

        /**
         * Sinh project plan hoàn chỉnh từ data đã thu thập.
         * LLM call happens outside any transaction.
         */
        public GenerateProjectPlanResponse generateProjectPlan(UUID wsId,
                        GenerateProjectPlanRequest req,
                        UUID currentUserId) {
                requireWorkspaceMember(wsId, currentUserId);

                int weeks = req.getDeadlineWeeks() != null ? req.getDeadlineWeeks() : 4;

                // ── Load members for skill-matching context ───────────────────────────
                String membersContext = buildMembersContext(wsId, req.getMemberIds());

                String userMsg = String.format("""
                                Mô tả dự án: "%s"
                                Tech stack: %s
                                Deadline: %d tuần kể từ hôm nay
                                Thành viên team (userId, tên, kỹ năng):
                                %s

                                Sinh project plan. Sử dụng đúng userId ở trên cho suggestedAssigneeId.
                                """,
                                req.getDescription(),
                                req.getTechStack() != null ? req.getTechStack() : "chưa xác định",
                                weeks,
                                membersContext);

                log.info("[WS-AI] generate-project-plan ws={} weeks={} members={}",
                                wsId, weeks, req.getMemberIds() != null ? req.getMemberIds().size() : 0);

                String raw = llmClient.call(SYS_GENERATE_PLAN, userMsg);

                try {
                        GenerateProjectPlanResponse response = objectMapper.readValue(raw,
                                        GenerateProjectPlanResponse.class);
                        // Compute totals in case LLM skips them
                        if (response.getSprints() != null) {
                                int totalTasks = response.getSprints().stream()
                                                .mapToInt(s -> s.getTasks() != null ? s.getTasks().size() : 0).sum();
                                int totalSP = response.getSprints().stream()
                                                .flatMap(s -> s.getTasks() != null ? s.getTasks().stream()
                                                                : java.util.stream.Stream.empty())
                                                .mapToInt(GenerateProjectPlanResponse.TaskPlanDto::getStoryPoints)
                                                .sum();
                                response.setTotalTasks(totalTasks);
                                response.setTotalStoryPoints(totalSP);
                        }
                        log.info("[WS-AI] generate-project-plan done: tasks={} sp={}",
                                        response.getTotalTasks(), response.getTotalStoryPoints());
                        return response;
                } catch (Exception e) {
                        log.error("[WS-AI] Failed to parse generate-project-plan response: {}", raw, e);
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                        "AI trả về dữ liệu không hợp lệ. Vui lòng thử lại.");
                }
        }

        // ═════════════════════════════════════════════════════════════════════════
        // 3. Confirm Project Plan — atomic @Transactional
        // ═════════════════════════════════════════════════════════════════════════

        @Transactional
        public ConfirmProjectPlanResponse confirmProjectPlan(UUID wsId,
                        ConfirmProjectPlanRequest req,
                        UUID creatorId) {
                requireWorkspaceMember(wsId, creatorId);

                Workspace workspace = workspaceRepository.findById(wsId)
                                .orElseThrow(() -> new NotFoundException("Workspace không tồn tại"));

                User creator = userRepository.findById(creatorId)
                                .orElseThrow(() -> new NotFoundException("Người dùng không tồn tại"));

                ConfirmProjectPlanRequest.PlanPayload plan = req.getPlan();
                GenerateProjectPlanResponse.ProjectPlanDto projectDto = plan.getProject();

                // ── 1. Resolve unique project key ─────────────────────────────────────
                String projectKey = resolveUniqueProjectKey(projectDto.getProjectKey());

                // ── 2. Create project ─────────────────────────────────────────────────
                Project project = Project.builder()
                                .name(projectDto.getName())
                                .projectKey(projectKey)
                                .description(projectDto.getDescription())
                                .status(ProjectStatus.ACTIVE)
                                .visibility(ProjectVisibility.PRIVATE)
                                .taskCounter(0)
                                .owner(creator)
                                .workspace(workspace)
                                .build();

                // Default Kanban columns (cascade saved with project)
                List<ProjectStatusColumn> columns = new ArrayList<>();
                columns.add(ProjectStatusColumn.builder().project(project).name("To Do")
                                .colorHex("#D9D9D9").sortOrder(1).isDefault(true).mappedStatus(TaskStatus.TODO)
                                .build());
                columns.add(ProjectStatusColumn.builder().project(project).name("In Progress")
                                .colorHex("#1677FF").sortOrder(2).mappedStatus(TaskStatus.IN_PROGRESS).build());
                columns.add(ProjectStatusColumn.builder().project(project).name("In Review")
                                .colorHex("#FAAD14").sortOrder(3).mappedStatus(TaskStatus.IN_REVIEW).build());
                columns.add(ProjectStatusColumn.builder().project(project).name("Testing")
                                .colorHex("#722ED1").sortOrder(4).mappedStatus(TaskStatus.TESTING).build());
                columns.add(ProjectStatusColumn.builder().project(project).name("Done")
                                .colorHex("#52C41A").sortOrder(5).mappedStatus(TaskStatus.DONE).build());
                project.setStatusColumns(columns);

                Project saved = projectRepository.save(project);
                ProjectStatusColumn defaultTodoColumn = columns.stream()
                                .filter(col -> col.getMappedStatus() == TaskStatus.TODO)
                                .findFirst()
                                .orElse(columns.get(0));

                // ── 3. Add creator as PROJECT_MANAGER ─────────────────────────────────
                projectMemberRepository.save(ProjectMember.builder()
                                .project(saved).user(creator)
                                .projectRole(ProjectRole.PROJECT_MANAGER)
                                .joinedAt(Instant.now())
                                .build());

                // ── 4. Add selected WS members as MEMBER ─────────────────────────────
                Map<UUID, User> memberUserMap = new HashMap<>();
                if (plan.getMemberIds() != null) {
                        for (String memberId : plan.getMemberIds()) {
                                try {
                                        UUID uid = UUID.fromString(memberId);
                                        if (uid.equals(creatorId))
                                                continue; // creator already added as PM
                                        userRepository.findById(uid).ifPresent(u -> {
                                                memberUserMap.put(uid, u);
                                                projectMemberRepository.save(ProjectMember.builder()
                                                                .project(saved).user(u)
                                                                .projectRole(ProjectRole.MEMBER)
                                                                .joinedAt(Instant.now())
                                                                .build());
                                        });
                                } catch (IllegalArgumentException ignored) {
                                        /* skip invalid UUIDs */ }
                        }
                }

                // ── 5. Create sprints + tasks ─────────────────────────────────────────
                LocalDate today = LocalDate.now();
                int taskCount = 0;
                int assignedCount = 0;
                int nextTaskCounter = saved.getTaskCounter() != null ? saved.getTaskCounter() : 0;
                int nextTodoPosition = 0;

                List<GenerateProjectPlanResponse.SprintPlanDto> sprintDtos = plan.getSprints() != null
                                ? plan.getSprints()
                                : List.of();

                for (GenerateProjectPlanResponse.SprintPlanDto sprintDto : sprintDtos) {
                        LocalDate sprintStart = today.plusDays((long) (sprintDto.getWeekStart() - 1) * 7);
                        LocalDate sprintEnd = today.plusDays((long) sprintDto.getWeekEnd() * 7 - 1);

                        Sprint sprint = Sprint.builder()
                                        .project(saved)
                                        .name(sprintDto.getName())
                                        .goal(sprintDto.getGoal())
                                        .status(SprintStatus.PLANNED)
                                        .startDate(sprintStart)
                                        .endDate(sprintEnd)
                                        .build();
                        Sprint savedSprint = sprintRepository.save(sprint);

                        List<GenerateProjectPlanResponse.TaskPlanDto> taskDtos = sprintDto.getTasks() != null
                                        ? sprintDto.getTasks()
                                        : List.of();

                        for (GenerateProjectPlanResponse.TaskPlanDto taskDto : taskDtos) {
                                // Project vừa được tạo trong transaction hiện tại nên chưa visible cho
                                // TaskCodeGenerator(REQUIRES_NEW). Sinh tuần tự ngay trong transaction này
                                // để tránh lỗi "Project not found" khi confirm AI plan.
                                nextTaskCounter++;
                                String taskCode = String.format("%s-%03d", saved.getProjectKey(), nextTaskCounter);

                                User assignee = null;
                                if (taskDto.getSuggestedAssigneeId() != null
                                                && !taskDto.getSuggestedAssigneeId().isBlank()) {
                                        try {
                                                UUID assigneeId = UUID.fromString(taskDto.getSuggestedAssigneeId());
                                                assignee = memberUserMap.get(assigneeId);
                                                if (assignee == null && assigneeId.equals(creatorId)) {
                                                        assignee = creator;
                                                }
                                        } catch (IllegalArgumentException ignored) {
                                                /* LLM returned bad UUID */ }
                                }

                                Task task = Task.builder()
                                                .project(saved)
                                                .sprint(savedSprint)
                                                .statusColumn(defaultTodoColumn)
                                                .taskCode(taskCode)
                                                .title(cap(taskDto.getTitle(), 255))
                                                .type(safeType(taskDto.getType()))
                                                .taskStatus(TaskStatus.TODO)
                                                .taskPosition(nextTodoPosition++)
                                                .priority(safePriority(taskDto.getPriority()))
                                                .storyPoints(taskDto.getStoryPoints() > 0 ? taskDto.getStoryPoints()
                                                                : 3)
                                                .startDate(sprintStart)
                                                .dueDate(sprintEnd)
                                                .skillTagsRequired(taskDto.getSkillTagsRequired())
                                                .aiGenerated(true)
                                                .reporter(creator)
                                                .assignee(assignee)
                                                .build();

                                Task savedTask = taskRepository.save(task);
                                taskCount++;

                                if (assignee != null) {
                                        assignedCount++;
                                        // Audit trail
                                        aiAssignmentRepository.save(AiTaskAssignment.builder()
                                                        .taskId(savedTask.getId())
                                                        .suggestedUserId(assignee.getId())
                                                        .skillScore(BigDecimal.valueOf(0.5))
                                                        .workloadScore(BigDecimal.valueOf(0.5))
                                                        .difficultyScore(BigDecimal.valueOf(0.5))
                                                        .totalScore(BigDecimal.valueOf(0.5))
                                                        .reasonText("AI-assigned via project plan generation")
                                                        .status(AiAssignmentStatus.CONFIRMED)
                                                        .confirmedBy(creatorId)
                                                        .build());

                                        // Increment active_task_count for assigned member
                                        projectMemberRepository
                                                        .findByProjectIdAndUserId(saved.getId(), assignee.getId())
                                                        .ifPresent(pm -> {
                                                                pm.setActiveTaskCount(pm.getActiveTaskCount() + 1);
                                                                projectMemberRepository.save(pm);
                                                        });
                                }
                        }
                }

                saved.setTaskCounter(nextTaskCounter);

                log.info("[WS-AI] confirm-project-plan done: project={} sprints={} tasks={} assigned={}",
                                saved.getId(), sprintDtos.size(), taskCount, assignedCount);

                return ConfirmProjectPlanResponse.builder()
                                .projectId(saved.getId().toString())
                                .projectKey(saved.getProjectKey())
                                .sprintCount(sprintDtos.size())
                                .taskCount(taskCount)
                                .assignedCount(assignedCount)
                                .projectUrl("/projects/" + saved.getId())
                                .build();
        }

        // ═════════════════════════════════════════════════════════════════════════
        // Helpers
        // ═════════════════════════════════════════════════════════════════════════

        private void requireWorkspaceMember(UUID wsId, UUID userId) {
                if (!workspaceMemberRepository.existsByIdWorkspaceIdAndIdUserId(wsId, userId)) {
                        throw new Forbidden("Bạn không phải thành viên của workspace này");
                }
        }

        private AnalyzeDescriptionResponse.AiQuestion buildTechStackQuestion() {
                return AnalyzeDescriptionResponse.AiQuestion.builder()
                                .field("techStack")
                                .question("Tech stack dự án sử dụng là gì?")
                                .options(List.of(
                                                AnalyzeDescriptionResponse.QuestionOption.builder()
                                                                .label("React + Node.js + MongoDB")
                                                                .value("React,Node.js,MongoDB").build(),
                                                AnalyzeDescriptionResponse.QuestionOption.builder()
                                                                .label("React + Java Spring Boot + MySQL")
                                                                .value("React,Java,Spring Boot,MySQL").build(),
                                                AnalyzeDescriptionResponse.QuestionOption.builder()
                                                                .label("Vue.js + Python Django")
                                                                .value("Vue.js,Python,Django").build(),
                                                AnalyzeDescriptionResponse.QuestionOption.builder()
                                                                .label("Angular + .NET + SQL Server")
                                                                .value("Angular,.NET,SQL Server").build()))
                                .allowCustom(true)
                                .customPlaceholder("Nhập tech stack khác...")
                                .build();
        }

        private AnalyzeDescriptionResponse.AiQuestion buildDeadlineQuestion() {
                return AnalyzeDescriptionResponse.AiQuestion.builder()
                                .field("deadline")
                                .question("Deadline dự kiến của dự án?")
                                .options(List.of(
                                                AnalyzeDescriptionResponse.QuestionOption.builder().label("2 tuần")
                                                                .value("2").build(),
                                                AnalyzeDescriptionResponse.QuestionOption.builder().label("1 tháng")
                                                                .value("4").build(),
                                                AnalyzeDescriptionResponse.QuestionOption.builder().label("6 tuần")
                                                                .value("6").build(),
                                                AnalyzeDescriptionResponse.QuestionOption.builder().label("3 tháng+")
                                                                .value("12").build()))
                                .allowCustom(true)
                                .customPlaceholder("Nhập số tuần cụ thể...")
                                .build();
        }

        private AnalyzeDescriptionResponse.AiQuestion buildMemberSelectQuestion(UUID wsId) {
                List<WorkspaceMember> wms = workspaceMemberRepository.findByWorkspaceId(wsId);
                List<AnalyzeDescriptionResponse.MemberOption> memberOptions = wms.stream()
                                .map(wm -> AnalyzeDescriptionResponse.MemberOption.builder()
                                                .userId(wm.getUser().getId().toString())
                                                .fullName(wm.getUser().getFullName())
                                                .avatarUrl(wm.getUser().getAvatarUrl())
                                                .skillTags(wm.getSkillTags() != null ? wm.getSkillTags()
                                                                : (wm.getUser().getSkillTags() != null
                                                                                ? wm.getUser().getSkillTags()
                                                                                : List.of()))
                                                .build())
                                .collect(Collectors.toList());

                return AnalyzeDescriptionResponse.AiQuestion.builder()
                                .field("teamMembers")
                                .question("Chọn thành viên tham gia dự án:")
                                .type("member-select")
                                .members(memberOptions)
                                .allowCustom(false)
                                .build();
        }

        private String buildMembersContext(UUID wsId, List<String> memberIds) {
                if (memberIds == null || memberIds.isEmpty())
                        return "(chưa chọn thành viên)";

                List<WorkspaceMember> wms = workspaceMemberRepository.findByWorkspaceId(wsId);
                Map<String, WorkspaceMember> wmMap = wms.stream()
                                .collect(Collectors.toMap(wm -> wm.getUser().getId().toString(), wm -> wm));

                StringBuilder sb = new StringBuilder();
                for (String id : memberIds) {
                        WorkspaceMember wm = wmMap.get(id);
                        if (wm == null)
                                continue;
                        List<String> skills = wm.getSkillTags() != null ? wm.getSkillTags()
                                        : (wm.getUser().getSkillTags() != null ? wm.getUser().getSkillTags()
                                                        : List.of());
                        sb.append(String.format("- userId: %s | %s | skills: %s%n",
                                        id, wm.getUser().getFullName(), String.join(", ", skills)));
                }
                return sb.toString().isBlank() ? "(chưa chọn thành viên)" : sb.toString();
        }

        private String resolveUniqueProjectKey(String base) {
                if (base == null || base.isBlank())
                        base = "PROJ";
                String key = base.toUpperCase().replaceAll("[^A-Z0-9]", "").substring(0, Math.min(base.length(), 6));
                if (key.isBlank())
                        key = "PROJ";
                if (!projectRepository.existsByProjectKey(key))
                        return key;
                int suffix = 2;
                while (projectRepository.existsByProjectKey(key + suffix))
                        suffix++;
                return key + suffix;
        }

        private JsonNode parseJson(String raw, String context) {
                try {
                        return objectMapper.readTree(raw);
                } catch (Exception e) {
                        log.error("[WS-AI] Failed to parse {} response: {}", context, raw, e);
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                        "AI trả về dữ liệu không hợp lệ. Vui lòng thử lại.");
                }
        }

        private List<String> jsonArrayToList(JsonNode node) {
                if (!node.isArray())
                        return List.of();
                List<String> result = new ArrayList<>();
                node.forEach(n -> result.add(n.asText()));
                return result;
        }

        private static String cap(String s, int max) {
                if (s == null)
                        return "";
                return s.length() <= max ? s : s.substring(0, max);
        }

        private static TaskType safeType(String type) {
                if (type == null)
                        return TaskType.TASK;
                return switch (type.toLowerCase()) {
                        case "story" -> TaskType.STORY;
                        case "bug" -> TaskType.BUG;
                        default -> TaskType.TASK;
                };
        }

        private static TaskPriority safePriority(String p) {
                if (p == null)
                        return TaskPriority.MEDIUM;
                return switch (p.toLowerCase()) {
                        case "critical" -> TaskPriority.CRITICAL;
                        case "high" -> TaskPriority.HIGH;
                        case "low" -> TaskPriority.LOW;
                        default -> TaskPriority.MEDIUM;
                };
        }
}
