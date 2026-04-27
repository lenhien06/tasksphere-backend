package com.zone.tasksphere.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.ai.config.LlmClient;
import com.zone.tasksphere.ai.dto.*;
import com.zone.tasksphere.ai.entity.AiTaskAssignment;
import com.zone.tasksphere.ai.repository.AiTaskAssignmentRepository;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.*;
import com.zone.tasksphere.repository.*;
import com.zone.tasksphere.service.ActivityLogService;
import com.zone.tasksphere.service.NotificationService;
import com.zone.tasksphere.utils.TaskCodeGenerator;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private static final int TOP_K = 3;
    private static final BigDecimal MIN_RECOMMEND_SCORE = new BigDecimal("0.200");

    private static final String SYS_GENERATE = """
            Bạn là AI assistant cho hệ thống quản lý dự án Agile/Scrum.
            LUÔN trả về JSON array hợp lệ, KHÔNG có text nào khác ngoài JSON.
            KHÔNG dùng markdown code block.

            Schema bắt buộc mỗi phần tử:
            {"title":"string","description":"string","type":"task|story|bug",\
            "priority":"critical|high|medium|low","story_points":1|2|3|5|8|13,\
            "skill_tags_required":["string"],"acceptance_criteria":"string"}

            HƯỚNG DẪN phân loại type:
            - story: tính năng mô tả từ góc nhìn user \
            (dùng khi requirement dùng từ như: cho phép, hiển thị, \
            người dùng có thể...)
            - task: công việc kỹ thuật thuần túy \
            (implement, build, tích hợp, deploy, validate...)
            - bug: sửa lỗi đang tồn tại
            """;

    private static final String SYS_REASON = """
            Tạo reason_text ngắn (≤120 ký tự tiếng Việt) cho từng gợi ý phân công.
            Trả về JSON object: key = "taskId_userId", value = reason string.
            KHÔNG có text nào khác ngoài JSON.
            """;

    private final LlmClient llmClient;
    private final ScoringEngine scoringEngine;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final AiTaskAssignmentRepository aiAssignmentRepository;
    private final TaskCodeGenerator taskCodeGenerator;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;

    /** Self-reference so internal calls to @Transactional methods go through the Spring proxy. */
    @Lazy @Autowired
    private AiService self;

    // ═══════════════════════════════════════════════════════════════════════════
    // FEATURE 1 — AI Task Generation
    // ═══════════════════════════════════════════════════════════════════════════

    /** Calls Claude, returns task suggestions. Nothing is saved to DB. */
    public List<GeneratedTaskDto> generateTasks(UUID projectId, GenerateTasksRequest req) {
        requireProject(projectId);

        String userMsg = String.format(
                "Dự án: %s%nTech stack: %s%nRequirements: %s%nSinh tối đa %d task. Trả về JSON array thuần.",
                req.getProjectName(),
                req.getTechStack() != null ? req.getTechStack() : "không xác định",
                req.getRequirementsText(),
                req.getMaxTasks());

        log.info("[AI] generate-tasks project={} maxTasks={}", projectId, req.getMaxTasks());
        String raw = llmClient.call(SYS_GENERATE, userMsg);

        try {
            List<GeneratedTaskDto> result = objectMapper.readValue(raw, new TypeReference<>() {});
            log.info("[AI] {} suggestions for project={}", result.size(), projectId);
            return result;
        } catch (Exception e) {
            log.error("[AI] Failed to parse generate-tasks response: {}", raw, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI trả về dữ liệu không hợp lệ. Vui lòng thử lại.");
        }
    }

    /**
     * Persists PM-approved tasks: assignee=NULL, status=TODO, aiGenerated=true.
     * Returns memberCount so the frontend can offer a direct link to AI assign.
     */
    @Transactional
    public ConfirmTasksResponse confirmTasks(UUID projectId, ConfirmTasksRequest req, User reporter) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> notFound("Project not found: " + projectId));

        List<String> createdIds = new ArrayList<>();

        for (GeneratedTaskDto dto : req.getTasks()) {
            String taskCode = taskCodeGenerator.generateTaskCode(project);

            Task task = Task.builder()
                    .project(project)
                    .taskCode(taskCode)
                    .title(cap80(dto.getTitle()))
                    .description(mergeDescription(dto))
                    .type(safeType(dto.getType()))
                    .taskStatus(TaskStatus.TODO)
                    .priority(safePriority(dto.getPriority()))
                    .storyPoints(dto.getStoryPoints())
                    .skillTagsRequired(dto.getSkillTagsRequired())
                    .aiGenerated(true)
                    .reporter(reporter)
                    .build();

            if (req.getSprintId() != null) {
                try {
                    task.setSprint(Sprint.builder().build());
                } catch (Exception ignored) { /* sprint optional */ }
            }

            Task saved = taskRepository.save(task);
            createdIds.add(saved.getId().toString());
            logAiTaskCreated(project.getId(), reporter.getId(), saved.getId(), saved.getTaskCode());
        }

        int memberCount = (int) projectMemberRepository.countByProjectId(projectId);

        log.info("[AI] confirm-tasks created={} project={} memberCount={}", createdIds.size(), projectId, memberCount);
        return ConfirmTasksResponse.builder()
                .createdTaskIds(createdIds)
                .count(createdIds.size())
                .memberCount(memberCount)
                .projectId(projectId.toString())
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FEATURE 2 — AI Assignment Suggestions
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Scores (task × member), calls LLM once for all reasons,
     * persists PENDING suggestions, returns the full review payload.
     *
     * NOT @Transactional at the top level — the LLM call can take 60+ seconds
     * and must NOT hold a DB connection/transaction. DB reads use per-operation
     * transactions (Spring Data default); the final batch-save uses its own
     * short @Transactional (see persistSuggestions).
     */
    public SuggestAssignmentsResponse suggestAssignments(UUID projectId, SuggestAssignmentsRequest req) {
        requireProject(projectId);

        // ── Step 1: load data (short per-operation transactions) ──────────────
        List<Task> tasks = (req != null
                && req.getTaskIds() != null
                && !req.getTaskIds().isEmpty())
                ? taskRepository.findUnassignedByIds(
                        req.getTaskIds().stream().map(UUID::fromString).toList())
                : taskRepository.findUnassignedActiveByProjectId(projectId);
        if (tasks.isEmpty()) {
            return SuggestAssignmentsResponse.builder()
                    .totalTasks(0).totalSuggestions(0).suggestions(List.of()).build();
        }

        List<ProjectMember> members = projectMemberRepository.findByProjectIdWithUser(projectId);
        if (members.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Project không có member để gợi ý phân công.");
        }

        // ── Load workspace members once for skill-priority fallback ───────────
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> notFound("Project not found: " + projectId));
        UUID workspaceId = project.getWorkspace() != null ? project.getWorkspace().getId() : null;
        Map<UUID, WorkspaceMember> wsMemberMap = workspaceId != null
                ? workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                        .collect(Collectors.toMap(wm -> wm.getUser().getId(), wm -> wm))
                : Map.of();

        // ── Step 2: score (pure Java, no DB) ──────────────────────────────────
        List<Slot> allSlots = new ArrayList<>();
        Map<UUID, String> noSuggestionReasons = new HashMap<>();
        for (Task task : tasks) {
            List<Slot> ranked = members.stream()
                    .map(pm -> {
                        List<String> effSkills = getEffectiveSkillTags(pm, wsMemberMap);
                        return new Slot(task, pm, scoringEngine.score(
                                task.getSkillTagsRequired(),
                                effSkills,
                                pm.getActiveTaskCount(),
                                pm.getAvgStoryPoints() != null
                                        ? pm.getAvgStoryPoints().doubleValue() : null,
                                task.getStoryPoints()), effSkills);
                    })
                    .sorted(Comparator.comparing(s -> s.score().totalScore(),
                            Comparator.reverseOrder()))
                    .collect(Collectors.toList());

            List<Slot> accepted = filterAcceptedSuggestions(task, ranked);
            if (accepted.isEmpty()) {
                noSuggestionReasons.put(task.getId(), buildNoSuggestionReason(task, ranked));
            }
            allSlots.addAll(accepted.stream().limit(TOP_K).toList());
        }

        // ── Step 3: LLM call — outside any transaction ────────────────────────
        Map<String, String> reasons = fetchReasonsBatch(allSlots);

        // ── Step 4: persist in a dedicated short transaction ──────────────────
        return self.persistSuggestions(projectId, tasks, allSlots, reasons, noSuggestionReasons);
    }

    @Transactional
    public SuggestAssignmentsResponse persistSuggestions(
            UUID projectId, List<Task> tasks, List<Slot> allSlots, Map<String, String> reasons,
            Map<UUID, String> noSuggestionReasons) {

        Map<UUID, List<Slot>> byTask = allSlots.stream()
                .collect(Collectors.groupingBy(s -> s.task().getId(),
                        LinkedHashMap::new, Collectors.toList()));

        List<TaskAssignmentSuggestion> responseList = new ArrayList<>();
        int savedCount = 0;

        for (Task task : tasks) {
            List<Slot> top = byTask.getOrDefault(task.getId(), List.of());
            List<MemberSuggestion> memberSuggestions = new ArrayList<>();

            for (Slot slot : top) {
                User u  = slot.member().getUser();
                String key = task.getId() + "_" + u.getId();
                String reason = reasons.getOrDefault(key,
                        "Phù hợp dựa trên kỹ năng và khối lượng công việc hiện tại.");

                AiTaskAssignment saved = aiAssignmentRepository.save(
                        AiTaskAssignment.builder()
                                .taskId(task.getId())
                                .suggestedUserId(u.getId())
                                .skillScore(slot.score().skillScore())
                                .workloadScore(slot.score().workloadScore())
                                .difficultyScore(slot.score().difficultyScore())
                                .totalScore(slot.score().totalScore())
                                .reasonText(reason)
                                .status(AiAssignmentStatus.PENDING)
                                .build());
                savedCount++;

                memberSuggestions.add(MemberSuggestion.builder()
                        .suggestionId(saved.getId().toString())
                        .userId(u.getId().toString())
                        .fullName(u.getFullName())
                        .avatarUrl(u.getAvatarUrl())
                        .skillTags(slot.effectiveSkills())
                        .activeTaskCount(slot.member().getActiveTaskCount())
                        .skillScore(slot.score().skillScore())
                        .workloadScore(slot.score().workloadScore())
                        .difficultyScore(slot.score().difficultyScore())
                        .totalScore(slot.score().totalScore())
                        .reasonText(reason)
                        .build());
            }

            responseList.add(TaskAssignmentSuggestion.builder()
                    .taskId(task.getId().toString())
                    .taskTitle(task.getTitle())
                    .taskType(task.getType().name().toLowerCase())
                    .taskPriority(task.getPriority().name().toLowerCase())
                    .storyPoints(task.getStoryPoints())
                    .skillTagsRequired(task.getSkillTagsRequired() != null
                            ? task.getSkillTagsRequired() : List.of())
                    .noSuggestionReason(noSuggestionReasons.get(task.getId()))
                    .topSuggestions(memberSuggestions)
                    .build());
        }

        log.info("[AI] suggest-assignments saved={} project={}", savedCount, projectId);
        return SuggestAssignmentsResponse.builder()
                .totalTasks(tasks.size()).totalSuggestions(savedCount).suggestions(responseList).build();
    }

    /**
     * Validates BR-16, writes assignee_id, marks audit statuses, updates workload count.
     */
    @Transactional
    public AssignmentConfirmResult confirmAssignments(
            UUID projectId, ConfirmAssignmentsRequest req, UUID confirmerId) {

        requireProject(projectId);

        // ── BR-16: pre-validate entire batch before writing anything ────────────
        List<String> br16Violations = new ArrayList<>();
        for (ConfirmAssignmentsRequest.AssignmentItem item : req.getAssignments()) {
            UUID assigneeId = UUID.fromString(item.getAssigneeId());
            if (!projectMemberRepository.existsByProject_IdAndUser_Id(projectId, assigneeId)) {
                log.warn("[BR-16] user={} not a member of project={}", assigneeId, projectId);
                br16Violations.add(item.getAssigneeId());
            }
        }
        if (!br16Violations.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "BR-16: Các user sau không phải member của project: " + String.join(", ", br16Violations));
        }

        User confirmerUser = userRepository.findById(confirmerId).orElse(null);

        List<String> failed = new ArrayList<>();
        int aiConfirmed = 0, pmOverridden = 0;

        for (ConfirmAssignmentsRequest.AssignmentItem item : req.getAssignments()) {
            UUID taskId    = UUID.fromString(item.getTaskId());
            UUID assigneeId = UUID.fromString(item.getAssigneeId());

            Task task = taskRepository.findByIdAndProject_Id(taskId, projectId).orElse(null);
            if (task == null) { failed.add(item.getTaskId()); continue; }

            User assignee = userRepository.findById(assigneeId).orElse(null);
            if (assignee == null) { failed.add(item.getTaskId()); continue; }

            task.setAssignee(assignee);
            taskRepository.save(task);

            // FR-14: notify assignee
            if (confirmerUser != null) {
                notificationService.sendTaskAssigned(task, assignee, confirmerUser);
            }

            // ── Determine CONFIRMED vs OVERRIDDEN ───────────────────────────────
            boolean isAiPick = false;
            if (item.getSuggestionId() != null) {
                UUID suggId = UUID.fromString(item.getSuggestionId());
                Optional<AiTaskAssignment> sugg = aiAssignmentRepository.findById(suggId);
                if (sugg.isPresent()) {
                    isAiPick = sugg.get().getSuggestedUserId().equals(assigneeId);
                    aiAssignmentRepository.updateStatus(suggId,
                            isAiPick ? AiAssignmentStatus.CONFIRMED : AiAssignmentStatus.OVERRIDDEN,
                            confirmerId);
                }
            }
            aiAssignmentRepository.overrideAllPendingForTask(taskId, confirmerId);

            if (isAiPick) aiConfirmed++; else pmOverridden++;

            // ── Increment active_task_count ────────────────────────────────────
            projectMemberRepository.findByProject_IdAndUser_Id(projectId, assigneeId)
                    .ifPresent(pm -> {
                        pm.setActiveTaskCount(pm.getActiveTaskCount() + 1);
                        projectMemberRepository.save(pm);
                    });
        }

        log.info("[AI] confirm-assignments project={} total={} ai={} pm={} failed={}",
                projectId, aiConfirmed + pmOverridden, aiConfirmed, pmOverridden, failed.size());
        return AssignmentConfirmResult.builder()
                .totalAssigned(aiConfirmed + pmOverridden)
                .aiConfirmed(aiConfirmed).pmOverridden(pmOverridden).failedTaskIds(failed).build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private record Slot(Task task, ProjectMember member, ScoringEngine.ScoreResult score, List<String> effectiveSkills) {}

    private Map<String, String> fetchReasonsBatch(List<Slot> slots) {
        if (slots.isEmpty()) return Map.of();
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < slots.size(); i++) {
            Slot s    = slots.get(i);
            User u    = s.member().getUser();
            String key = s.task().getId() + "_" + u.getId();
            if (i > 0) sb.append(",\n");
            sb.append("  {\"key\":\"").append(key)
              .append("\",\"task\":\"").append(esc(s.task().getTitle()))
              .append("\",\"member\":\"").append(esc(u.getFullName()))
              .append("\",\"skills\":").append(jsonArr(s.effectiveSkills()))
              .append(",\"skill_score\":").append(s.score().skillScore())
              .append(",\"workload_score\":").append(s.score().workloadScore())
              .append(",\"difficulty_score\":").append(s.score().difficultyScore())
              .append(",\"total_score\":").append(s.score().totalScore())
              .append("}");
        }
        sb.append("\n]");
        try {
            String raw = llmClient.call(SYS_REASON, sb.toString());
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("[AI] Failed to parse reason-batch response", e);
            return Map.of();
        }
    }

    /**
     * Resolves the effective skill tags for a project member using priority order:
     * 1. project_members.skill_tags (PM-set per project)
     * 2. workspace_members.skill_tags (PM-set per workspace)
     * 3. users.skill_tags (user's own profile)
     */
    private List<String> getEffectiveSkillTags(ProjectMember pm, Map<UUID, WorkspaceMember> wsMemberMap) {
        // Priority 1: project-scoped skill
        if (pm.getSkillTags() != null && !pm.getSkillTags().isEmpty()) {
            log.debug("[Skill] user={} project={} → project skill: {}", pm.getUser().getId(), pm.getProject().getId(), pm.getSkillTags());
            return pm.getSkillTags();
        }
        // Priority 2: workspace-scoped skill
        WorkspaceMember wm = wsMemberMap.get(pm.getUser().getId());
        if (wm != null && wm.getSkillTags() != null && !wm.getSkillTags().isEmpty()) {
            log.debug("[Skill] user={} → workspace skill: {}", pm.getUser().getId(), wm.getSkillTags());
            return wm.getSkillTags();
        }
        // Priority 3: user profile skill
        List<String> profileSkills = pm.getUser().getSkillTags() != null ? pm.getUser().getSkillTags() : List.of();
        log.debug("[Skill] user={} → profile skill: {}", pm.getUser().getId(), profileSkills);
        return profileSkills;
    }

    private List<Slot> filterAcceptedSuggestions(Task task, List<Slot> ranked) {
        if (ranked.isEmpty()) {
            return List.of();
        }
        boolean hasRequiredSkills = task.getSkillTagsRequired() != null && !task.getSkillTagsRequired().isEmpty();
        return ranked.stream()
                .filter(slot -> slot.score().totalScore().compareTo(MIN_RECOMMEND_SCORE) >= 0)
                .filter(slot -> !hasRequiredSkills || slot.score().skillScore().compareTo(new BigDecimal("0.100")) >= 0)
                .toList();
    }

    private String buildNoSuggestionReason(Task task, List<Slot> ranked) {
        if (ranked.isEmpty()) {
            return "No active project member is available for recommendation right now.";
        }

        boolean hasRequiredSkills = task.getSkillTagsRequired() != null && !task.getSkillTagsRequired().isEmpty();
        boolean noSkillMatch = hasRequiredSkills && ranked.stream()
                .allMatch(slot -> slot.score().skillScore().compareTo(new BigDecimal("0.100")) < 0);
        if (noSkillMatch) {
            return "No project member matches the required skill tags for this task.";
        }

        boolean allOverloaded = ranked.stream()
                .allMatch(slot -> slot.score().workloadScore().compareTo(new BigDecimal("0.100")) <= 0);
        if (allOverloaded) {
            return "All likely assignees are already overloaded, so the system could not recommend a safe owner.";
        }

        return "The available candidates did not reach the minimum confidence threshold for an assignment recommendation.";
    }

    private void requireProject(UUID projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw notFound("Project not found: " + projectId);
        }
    }

    private ResponseStatusException notFound(String detail) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, detail);
    }

    private String cap80(String s) {
        if (s == null) return "Untitled Task";
        return s.length() > 80 ? s.substring(0, 80) : s;
    }

    private String mergeDescription(GeneratedTaskDto dto) {
        StringBuilder sb = new StringBuilder();
        if (dto.getDescription() != null) sb.append(dto.getDescription());
        if (dto.getAcceptanceCriteria() != null && !dto.getAcceptanceCriteria().isBlank())
            sb.append("\n\n**Acceptance Criteria:**\n").append(dto.getAcceptanceCriteria());
        return sb.toString();
    }

    private TaskType safeType(String raw) {
        if (raw == null) return TaskType.TASK;
        return switch (raw.toLowerCase()) {
            case "story" -> TaskType.STORY;
            case "bug"   -> TaskType.BUG;
            case "epic"  -> TaskType.EPIC;
            default      -> TaskType.TASK;
        };
    }

    private TaskPriority safePriority(String raw) {
        if (raw == null) return TaskPriority.MEDIUM;
        return switch (raw.toLowerCase()) {
            case "critical" -> TaskPriority.CRITICAL;
            case "high"     -> TaskPriority.HIGH;
            case "low"      -> TaskPriority.LOW;
            default         -> TaskPriority.MEDIUM;
        };
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }

    private String jsonArr(List<String> items) {
        if (items == null || items.isEmpty()) return "[]";
        StringJoiner sj = new StringJoiner(",", "[", "]");
        items.forEach(i -> sj.add("\"" + esc(i) + "\""));
        return sj.toString();
    }

    /** BR-23: log task creation from AI confirm. Uses RequestContextHolder to grab current HTTP request. */
    private void logAiTaskCreated(UUID projectId, UUID actorId, UUID taskId, String taskCode) {
        try {
            HttpServletRequest httpRequest = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getRequest();
            activityLogService.logActivity(projectId, actorId, EntityType.TASK, taskId,
                    ActionType.TASK_CREATED, null, taskCode, httpRequest);
        } catch (Exception e) {
            log.warn("[AI] Failed to log task-created activity for task={}: {}", taskId, e.getMessage());
        }
    }
}
