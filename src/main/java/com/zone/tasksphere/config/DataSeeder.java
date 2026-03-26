package com.zone.tasksphere.config;

import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.*;
import com.zone.tasksphere.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Development data seeder — idempotent, runs on every startup except in test profile.
 * Seeds 3 projects, 9 users, ~55 tasks, worklogs, comments and activity logs.
 */
@Component
@Profile("!test")
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository                  userRepository;
    private final RoleRepository                  roleRepository;
    private final ProjectRepository               projectRepository;
    private final ProjectMemberRepository         projectMemberRepository;
    private final ProjectStatusColumnRepository   projectStatusColumnRepository;
    private final SprintRepository                sprintRepository;
    private final TaskRepository                  taskRepository;
    private final CommentRepository               commentRepository;
    private final CommentMentionRepository        commentMentionRepository;
    private final ChecklistItemRepository         checklistItemRepository;
    private final CustomFieldRepository           customFieldRepository;
    private final CustomFieldValueRepository      customFieldValueRepository;
    private final TaskDependencyRepository        taskDependencyRepository;
    private final SavedFilterRepository           savedFilterRepository;
    private final ProjectInviteRepository         projectInviteRepository;
    private final NotificationRepository          notificationRepository;
    private final ProjectVersionRepository        projectVersionRepository;
    private final RecurringTaskConfigRepository   recurringTaskConfigRepository;
    private final WebhookRepository               webhookRepository;
    private final WebhookDeliveryLogRepository    webhookDeliveryLogRepository;
    private final AttachmentRepository            attachmentRepository;
    private final UploadJobRepository             uploadJobRepository;
    private final ExportJobRepository             exportJobRepository;
    private final ActivityLogRepository           activityLogRepository;
    private final WorklogRepository               worklogRepository;
    private final PasswordEncoder                 passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("[DataSeeder] Starting seed...");
        seedRoles();
        seedAdminUser();
        seedDemoUsers();
        seedDemoProject();
        seedEcomProject();
        seedMobileProject();
        seedProjectStatusColumns();
        seedAllSprints();
        seedDemoTasks();
        seedEcomTasks();
        seedMobileTasks();
        seedProjectVersions();
        seedTaskDependencies();
        seedCustomFieldsAndValues();
        seedSavedFilters();
        seedRecurringTaskConfigs();
        seedProjectInvites();
        seedComments();
        seedCommentMentions();
        seedNotifications();
        seedChecklists();
        seedAttachments();
        seedUploadJobs();
        seedWebhooks();
        seedExportJobs();
        seedWorklogs();
        seedActivityLogs();
        log.info("[DataSeeder] Seed completed.");
    }

    // ─── SEED 0: System roles ─────────────────────────────────────────────────

    @Transactional
    void seedRoles() {
        createRoleIfNotExists("ADMIN", "Quản trị viên", true);
        createRoleIfNotExists("USER",  "Người dùng",    false);
    }

    private void createRoleIfNotExists(String slug, String displayName, boolean isSystem) {
        if (roleRepository.findBySlug(slug).isEmpty()) {
            roleRepository.save(Role.builder()
                    .slug(slug).displayName(displayName)
                    .isActive(true).isSystem(isSystem).build());
        }
    }

    // ─── SEED 1: Admin user ───────────────────────────────────────────────────

    @Transactional
    void seedAdminUser() {
        if (userRepository.existsByEmail("admin@tasksphere.local")) return;
        Role adminRole = roleRepository.findBySlug("ADMIN").orElseThrow();
        userRepository.save(User.builder()
                .email("admin@tasksphere.local")
                .passwordHash(passwordEncoder.encode("Admin@123456"))
                .fullName("System Administrator")
                .systemRole(SystemRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .requirePasswordChange(true)
                .emailVerifiedAt(Instant.now())
                .role(adminRole)
                .build());
    }

    // ─── SEED 2: Demo users ───────────────────────────────────────────────────

    @Transactional
    void seedDemoUsers() {
        Role userRole = roleRepository.findBySlug("USER").orElseThrow();
        createUser("pm@tasksphere.local",       "Nguyễn Quản Lý",       userRole);
        createUser("dev1@tasksphere.local",     "Trần Lập Trình",        userRole);
        createUser("dev2@tasksphere.local",     "Lê Kỹ Thuật",           userRole);
        createUser("dev3@tasksphere.local",     "Hoàng Văn Backend",     userRole);
        createUser("dev4@tasksphere.local",     "Vũ Minh Frontend",      userRole);
        createUser("qa@tasksphere.local",       "Đặng Thị Kiểm Thử",    userRole);
        createUser("ba@tasksphere.local",       "Ngô Phân Tích",         userRole);
        createUser("designer@tasksphere.local", "Phan Thiết Kế",         userRole);
        createUser("viewer@tasksphere.local",   "Phạm Quan Sát",         userRole);
    }

    private void createUser(String email, String fullName, Role role) {
        if (!userRepository.existsByEmail(email)) {
            userRepository.save(User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode("Demo@123456"))
                    .fullName(fullName)
                    .systemRole(SystemRole.USER)
                    .status(UserStatus.ACTIVE)
                    .emailVerifiedAt(Instant.now())
                    .role(role)
                    .build());
        }
    }

    // ─── SEED 3: Projects + members ──────────────────────────────────────────

    @Transactional
    void seedDemoProject() {
        if (projectRepository.findByKeyWithDeleted("DEMO").isPresent()) return;
        User admin    = u("admin@tasksphere.local");
        User pm       = u("pm@tasksphere.local");
        User dev1     = u("dev1@tasksphere.local");
        User dev2     = u("dev2@tasksphere.local");
        User dev3     = u("dev3@tasksphere.local");
        User dev4     = u("dev4@tasksphere.local");
        User qa       = u("qa@tasksphere.local");
        User ba       = u("ba@tasksphere.local");
        User designer = u("designer@tasksphere.local");
        User viewer   = u("viewer@tasksphere.local");

        Project p = projectRepository.save(Project.builder()
                .name("TaskSphere Platform")
                .projectKey("DEMO")
                .description("Dự án phát triển nền tảng quản lý công việc TaskSphere — điều phối toàn bộ tính năng core của hệ thống từ auth, project, task đến real-time notification.")
                .status(ProjectStatus.ACTIVE)
                .visibility(ProjectVisibility.INTERNAL)
                .owner(admin)
                .build());

        addMember(p, admin,    ProjectRole.PROJECT_MANAGER);
        addMember(p, pm,       ProjectRole.PROJECT_MANAGER);
        addMember(p, dev1,     ProjectRole.MEMBER);
        addMember(p, dev2,     ProjectRole.MEMBER);
        addMember(p, dev3,     ProjectRole.MEMBER);
        addMember(p, dev4,     ProjectRole.MEMBER);
        addMember(p, qa,       ProjectRole.MEMBER);
        addMember(p, ba,       ProjectRole.MEMBER);
        addMember(p, designer, ProjectRole.MEMBER);
        addMember(p, viewer,   ProjectRole.VIEWER);
    }

    @Transactional
    void seedEcomProject() {
        if (projectRepository.findByKeyWithDeleted("ECOM").isPresent()) return;
        User admin = u("admin@tasksphere.local");
        User pm    = u("pm@tasksphere.local");
        User dev1  = u("dev1@tasksphere.local");
        User dev3  = u("dev3@tasksphere.local");
        User dev4  = u("dev4@tasksphere.local");
        User qa    = u("qa@tasksphere.local");
        User ba    = u("ba@tasksphere.local");

        Project p = projectRepository.save(Project.builder()
                .name("E-Commerce Platform")
                .projectKey("ECOM")
                .description("Xây dựng nền tảng thương mại điện tử với tính năng giỏ hàng, checkout, quản lý đơn hàng và tích hợp cổng thanh toán VNPay.")
                .status(ProjectStatus.ACTIVE)
                .visibility(ProjectVisibility.PRIVATE)
                .owner(pm)
                .build());

        addMember(p, admin, ProjectRole.PROJECT_MANAGER);
        addMember(p, pm,    ProjectRole.PROJECT_MANAGER);
        addMember(p, dev1,  ProjectRole.MEMBER);
        addMember(p, dev3,  ProjectRole.MEMBER);
        addMember(p, dev4,  ProjectRole.MEMBER);
        addMember(p, qa,    ProjectRole.MEMBER);
        addMember(p, ba,    ProjectRole.MEMBER);
    }

    @Transactional
    void seedMobileProject() {
        if (projectRepository.findByKeyWithDeleted("MOBILE").isPresent()) return;
        User admin    = u("admin@tasksphere.local");
        User pm       = u("pm@tasksphere.local");
        User dev2     = u("dev2@tasksphere.local");
        User dev4     = u("dev4@tasksphere.local");
        User qa       = u("qa@tasksphere.local");
        User designer = u("designer@tasksphere.local");

        Project p = projectRepository.save(Project.builder()
                .name("Mobile App Development")
                .projectKey("MOBILE")
                .description("Phát triển ứng dụng di động iOS/Android cho TaskSphere — bao gồm push notification, offline mode và biometric authentication.")
                .status(ProjectStatus.ACTIVE)
                .visibility(ProjectVisibility.INTERNAL)
                .owner(pm)
                .build());

        addMember(p, admin,    ProjectRole.PROJECT_MANAGER);
        addMember(p, pm,       ProjectRole.PROJECT_MANAGER);
        addMember(p, dev2,     ProjectRole.MEMBER);
        addMember(p, dev4,     ProjectRole.MEMBER);
        addMember(p, qa,       ProjectRole.MEMBER);
        addMember(p, designer, ProjectRole.MEMBER);
    }

    private void addMember(Project project, User user, ProjectRole role) {
        projectMemberRepository.save(ProjectMember.builder()
                .project(project).user(user)
                .projectRole(role).joinedAt(Instant.now()).build());
    }

    // ─── SEED 4: Kanban columns (5 per project) ───────────────────────────────

    @Transactional
    void seedProjectStatusColumns() {
        for (String key : List.of("DEMO", "ECOM", "MOBILE")) {
            Project p = projectRepository.findByProjectKey(key).orElse(null);
            if (p == null) continue;
            if (projectStatusColumnRepository.existsByProjectAndName(p, "To Do")) continue;
            projectStatusColumnRepository.save(col(p, "To Do",       "#D9D9D9", 1, TaskStatus.TODO));
            projectStatusColumnRepository.save(col(p, "In Progress", "#1677FF", 2, TaskStatus.IN_PROGRESS));
            projectStatusColumnRepository.save(col(p, "In Review",   "#FA8C16", 3, TaskStatus.IN_PROGRESS));
            projectStatusColumnRepository.save(col(p, "Testing",     "#722ED1", 4, TaskStatus.IN_PROGRESS));
            projectStatusColumnRepository.save(col(p, "Done",        "#52C41A", 5, TaskStatus.DONE));
        }
    }

    private ProjectStatusColumn col(Project p, String name, String color, int order, TaskStatus mapped) {
        return ProjectStatusColumn.builder()
                .project(p).name(name).colorHex(color)
                .sortOrder(order).isDefault(true).mappedStatus(mapped).build();
    }

    // ─── SEED 5: Sprints (3 per DEMO/ECOM, 2 per MOBILE) ────────────────────

    @Transactional
    void seedAllSprints() {
        seedSprintsFor("DEMO",
            new S("Sprint 1 — Khởi động & Setup", SprintStatus.COMPLETED,
                LocalDate.now().minusDays(56), LocalDate.now().minusDays(43), 20,
                "Thiết lập infrastructure, CI/CD pipeline và authentication module"),
            new S("Sprint 2 — Core Features", SprintStatus.COMPLETED,
                LocalDate.now().minusDays(42), LocalDate.now().minusDays(29), 35,
                "Xây dựng các tính năng cốt lõi: project management, task CRUD"),
            new S("Sprint 3 — Kanban & Realtime", SprintStatus.ACTIVE,
                LocalDate.now().minusDays(14), LocalDate.now().plusDays(0), null,
                "Hoàn thiện Kanban board, sprint management và real-time notifications"),
            new S("Sprint 4 — Polish & Release", SprintStatus.PLANNED,
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(14), null,
                "Performance optimization, bug fixing và chuẩn bị release v1.0")
        );

        seedSprintsFor("ECOM",
            new S("Sprint 1 — Foundation", SprintStatus.COMPLETED,
                LocalDate.now().minusDays(42), LocalDate.now().minusDays(29), 18,
                "Thiết kế database, auth module và product catalog API"),
            new S("Sprint 2 — Cart & Checkout", SprintStatus.ACTIVE,
                LocalDate.now().minusDays(14), LocalDate.now().plusDays(7), null,
                "Implement giỏ hàng, checkout flow và tích hợp VNPay"),
            new S("Sprint 3 — Orders & Inventory", SprintStatus.PLANNED,
                LocalDate.now().plusDays(8), LocalDate.now().plusDays(21), null,
                "Quản lý đơn hàng, inventory tracking và email notifications")
        );

        seedSprintsFor("MOBILE",
            new S("Sprint 1 — Project Setup", SprintStatus.ACTIVE,
                LocalDate.now().minusDays(7), LocalDate.now().plusDays(7), null,
                "React Native setup, navigation structure và authentication screens"),
            new S("Sprint 2 — Core Screens", SprintStatus.PLANNED,
                LocalDate.now().plusDays(8), LocalDate.now().plusDays(21), null,
                "Dashboard, task list, task detail và Kanban board screens")
        );
    }

    private record S(String name, SprintStatus status, LocalDate start, LocalDate end,
                     Integer velocity, String goal) {}

    private void seedSprintsFor(String projectKey, S... defs) {
        Project p = projectRepository.findByProjectKey(projectKey).orElse(null);
        if (p == null) return;
        if (!sprintRepository.findByProjectOrderByStartDateAsc(p).isEmpty()) return;
        for (S d : defs) {
            sprintRepository.save(Sprint.builder()
                    .project(p).name(d.name()).status(d.status())
                    .startDate(d.start()).endDate(d.end())
                    .velocity(d.velocity()).goal(d.goal()).build());
        }
    }

    // ─── SEED 6: DEMO tasks (~33 tasks) ──────────────────────────────────────

    @Transactional
    void seedDemoTasks() {
        if (taskRepository.findByTaskCode("DEMO-001").isPresent()) return;

        Project p      = projectRepository.findByProjectKey("DEMO").orElseThrow();
        User admin     = u("admin@tasksphere.local");
        User pm        = u("pm@tasksphere.local");
        User dev1      = u("dev1@tasksphere.local");
        User dev2      = u("dev2@tasksphere.local");
        User dev3      = u("dev3@tasksphere.local");
        User dev4      = u("dev4@tasksphere.local");
        User qa        = u("qa@tasksphere.local");
        User ba        = u("ba@tasksphere.local");
        User designer  = u("designer@tasksphere.local");

        var sprints = sprintRepository.findByProjectOrderByStartDateAsc(p);
        Sprint s1 = sprints.get(0); // COMPLETED
        Sprint s2 = sprints.get(1); // COMPLETED
        Sprint s3 = sprints.get(2); // ACTIVE
        Sprint s4 = sprints.get(3); // PLANNED

        var cols = projectStatusColumnRepository.findByProjectOrderBySortOrderAsc(p);
        ProjectStatusColumn todo   = cols.get(0);
        ProjectStatusColumn inProg = cols.get(1);
        ProjectStatusColumn review = cols.get(2);
        ProjectStatusColumn testing = cols.get(3);
        ProjectStatusColumn done   = cols.get(4);

        // ── Sprint 1 — all DONE ──
        t("DEMO-001", p, s1, done, TaskStatus.DONE, "Setup Spring Boot project structure",
                TaskType.TASK, TaskPriority.HIGH, dev1, admin, 3,
                LocalDate.now().minusDays(55), LocalDate.now().minusDays(50), bd(4.0),
                "Khởi tạo project Spring Boot 3.5.5, cấu hình Maven wrapper, Lombok, Spring Security placeholder.", 1);

        t("DEMO-002", p, s1, done, TaskStatus.DONE, "Thiết kế ERD & Database schema",
                TaskType.STORY, TaskPriority.HIGH, ba, admin, 8,
                LocalDate.now().minusDays(56), LocalDate.now().minusDays(48), bd(12.0),
                "Phân tích business requirements và thiết kế toàn bộ ERD 28 tables cho hệ thống TaskSphere.", 2);

        t("DEMO-003", p, s1, done, TaskStatus.DONE, "Setup Docker Compose — MySQL 8 & Redis",
                TaskType.TASK, TaskPriority.MEDIUM, dev3, admin, 2,
                LocalDate.now().minusDays(54), LocalDate.now().minusDays(52), bd(2.5),
                "Tạo docker-compose.yml với MySQL 8, Redis, khởi tạo task_sphere_db.", 3);

        t("DEMO-004", p, s1, done, TaskStatus.DONE, "Implement CI/CD với GitHub Actions",
                TaskType.TASK, TaskPriority.MEDIUM, dev1, admin, 5,
                LocalDate.now().minusDays(52), LocalDate.now().minusDays(47), bd(6.0),
                "Workflow build & test tự động, deploy preview lên staging khi merge vào develop.", 4);

        t("DEMO-005", p, s1, done, TaskStatus.DONE, "JWT Authentication — Login & Signup",
                TaskType.TASK, TaskPriority.HIGH, dev3, admin, 8,
                LocalDate.now().minusDays(50), LocalDate.now().minusDays(44), bd(10.0),
                "Login/signup với JWT access token (15m) + refresh token (7d). BCrypt pw strength=12.", 5);

        t("DEMO-007", p, s1, done, TaskStatus.DONE, "Email OTP Verification",
                TaskType.TASK, TaskPriority.LOW, dev1, admin, 3,
                LocalDate.now().minusDays(46), LocalDate.now().minusDays(43), bd(3.0),
                "Gửi OTP xác thực email khi đăng ký, expire sau 10 phút, max 3 lần thử.", 7);

        t("DEMO-008", p, s1, done, TaskStatus.DONE, "Viết tài liệu OpenAPI / Swagger",
                TaskType.TASK, TaskPriority.LOW, dev1, pm, 2,
                LocalDate.now().minusDays(44), LocalDate.now().minusDays(43), bd(3.0),
                "Cấu hình SpringDoc OpenAPI, annotate tất cả endpoint, export swagger.json.", 8);

        // ── Sprint 2 — all DONE ──
        t("DEMO-009", p, s2, done, TaskStatus.DONE, "Project CRUD API",
                TaskType.STORY, TaskPriority.HIGH, dev3, pm, 8,
                LocalDate.now().minusDays(42), LocalDate.now().minusDays(35), bd(10.0),
                "CRUD đầy đủ cho Project entity: tạo, sửa, xóa mềm, phân quyền PM/Member/Viewer.", 1);

        t("DEMO-010", p, s2, done, TaskStatus.DONE, "Member Management API",
                TaskType.TASK, TaskPriority.HIGH, dev3, pm, 5,
                LocalDate.now().minusDays(40), LocalDate.now().minusDays(34), bd(6.0),
                "API thêm/xóa/cập nhật quyền thành viên dự án, gửi email invite qua token.", 2);

        t("DEMO-011", p, s2, done, TaskStatus.DONE, "Task CRUD với Optimistic Locking",
                TaskType.STORY, TaskPriority.HIGH, dev1, pm, 13,
                LocalDate.now().minusDays(38), LocalDate.now().minusDays(30), bd(16.0),
                "CRUD task, sub-task, epic. @Version optimistic locking tránh race condition.", 3);

        t("DEMO-012", p, s2, done, TaskStatus.DONE, "Dynamic Task Filtering & Search",
                TaskType.TASK, TaskPriority.MEDIUM, dev1, pm, 5,
                LocalDate.now().minusDays(35), LocalDate.now().minusDays(30), bd(5.0),
                "Filter task theo status, priority, assignee, sprint dùng JPA Specification.", 4);

        t("DEMO-013", p, s2, done, TaskStatus.DONE, "Kanban Board — Move Task API",
                TaskType.TASK, TaskPriority.HIGH, dev4, pm, 8,
                LocalDate.now().minusDays(36), LocalDate.now().minusDays(30), bd(8.0),
                "API move task giữa columns, recalculate task_position, optimistic locking.", 5);

        t("DEMO-014", p, s2, done, TaskStatus.DONE, "Sprint Management API",
                TaskType.TASK, TaskPriority.MEDIUM, dev3, pm, 5,
                LocalDate.now().minusDays(34), LocalDate.now().minusDays(30), bd(5.0),
                "CRUD sprint, bắt đầu/hoàn thành sprint, move unfinished tasks sang sprint tiếp.", 6);

        t("DEMO-015", p, s2, done, TaskStatus.DONE, "UI/UX Design — Wireframe toàn hệ thống",
                TaskType.STORY, TaskPriority.HIGH, designer, pm, 13,
                LocalDate.now().minusDays(42), LocalDate.now().minusDays(30), bd(20.0),
                "Wireframe tất cả màn hình: Dashboard, Kanban, Task Detail, Sprint board, Settings.", 7);

        t("DEMO-016", p, s2, done, TaskStatus.DONE, "RBAC — Phân quyền Project Role",
                TaskType.TASK, TaskPriority.HIGH, dev3, pm, 5,
                LocalDate.now().minusDays(33), LocalDate.now().minusDays(30), bd(5.0),
                "Middleware kiểm tra ProjectRole (PM/MEMBER/VIEWER) trước mỗi request.", 8);

        // ── Sprint 3 — ACTIVE (mixed statuses) ──
        Task epic1 = taskRepository.save(task("DEMO-017", p, s3, todo, TaskStatus.TODO,
                "Epic: Real-time Notification System",
                TaskType.EPIC, TaskPriority.HIGH, null, admin, 34,
                LocalDate.now().minusDays(14), LocalDate.now().plusDays(14), null,
                "Hệ thống thông báo real-time cho toàn bộ sự kiện: task update, mentions, sprint events.", 1));
        sub("DEMO-018", p, todo,    "WebSocket Server Setup (STOMP/SockJS)", TaskPriority.HIGH,   dev3, admin, epic1);
        sub("DEMO-019", p, inProg,  "Notification Service & Event Publisher",  TaskPriority.HIGH,   dev3, admin, epic1);
        sub("DEMO-020", p, todo,    "Frontend — WebSocket Client Integration", TaskPriority.MEDIUM, dev4, admin, epic1);

        Task epic2 = taskRepository.save(task("DEMO-021", p, s3, inProg, TaskStatus.IN_PROGRESS,
                "Epic: Comment & Activity Feed",
                TaskType.EPIC, TaskPriority.MEDIUM, null, pm, 21,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(4), null,
                "Comment rich-text, @mention, reply thread và activity timeline cho mỗi task.", 2));
        sub("DEMO-022", p, done,    "Comment CRUD API (Quill rich-text)",       TaskPriority.MEDIUM, dev1, pm, epic2);
        sub("DEMO-023", p, inProg,  "@Mention parsing & Notification trigger",  TaskPriority.MEDIUM, dev1, pm, epic2);
        sub("DEMO-024", p, todo,    "Activity Log timeline UI",                 TaskPriority.LOW,    dev4, pm, epic2);

        t("DEMO-025", p, s3, inProg, TaskStatus.IN_PROGRESS, "File Upload & Attachment (AWS S3)",
                TaskType.TASK, TaskPriority.MEDIUM, dev2, pm, 5,
                LocalDate.now().minusDays(5), LocalDate.now().plusDays(2), bd(6.0),
                "Upload file đính kèm lên S3, giới hạn 25MB, hỗ trợ image preview inline.", 3);

        t("DEMO-026", p, s3, review, TaskStatus.IN_PROGRESS, "Worklog — Time Tracking API",
                TaskType.TASK, TaskPriority.LOW, dev2, pm, 3,
                LocalDate.now().minusDays(7), LocalDate.now().minusDays(1), bd(4.0),
                "API log thời gian làm việc cho từng task, tổng hợp theo sprint/user.", 4);

        t("DEMO-027", p, s3, testing, TaskStatus.IN_PROGRESS, "Sprint Velocity Chart API",
                TaskType.TASK, TaskPriority.LOW, dev1, pm, 3,
                LocalDate.now().minusDays(8), LocalDate.now().minusDays(2), bd(3.0),
                "API trả về dữ liệu velocity theo từng sprint để vẽ burndown chart.", 5);

        t("DEMO-028", p, s3, todo, TaskStatus.TODO, "Fix: Task position lệch khi drag cross-column",
                TaskType.BUG, TaskPriority.CRITICAL, dev1, qa, 3,
                LocalDate.now().minusDays(2), LocalDate.now().plusDays(1), bd(2.0),
                "Drag task từ 'In Review' sang 'Done' → position không recalculate đúng, gây sort sai.", 6);

        t("DEMO-029", p, s3, inProg, TaskStatus.IN_PROGRESS, "Fix: Refresh token không invalidate sau logout",
                TaskType.BUG, TaskPriority.HIGH, dev3, qa, 2,
                LocalDate.now().minusDays(3), LocalDate.now(), bd(1.5),
                "Sau logout, refresh token cũ vẫn dùng được để lấy access token mới. Security issue!", 7);

        t("DEMO-030", p, s3, todo, TaskStatus.TODO, "Perf: N+1 query trong Task list API",
                TaskType.BUG, TaskPriority.HIGH, dev3, pm, 5,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(3), bd(3.0),
                "TaskRepository trigger N+1 khi load assignee/reporter. Cần @EntityGraph hoặc JOIN FETCH.", 8);

        // ── Sprint 4 — PLANNED ──
        t("DEMO-031", p, s4, todo, TaskStatus.TODO, "Export Tasks — CSV & PDF",
                TaskType.TASK, TaskPriority.MEDIUM, dev2, pm, 5,
                null, LocalDate.now().plusDays(10), bd(6.0),
                "Export danh sách task dưới dạng CSV và PDF với filter tùy chỉnh.", 1);

        t("DEMO-032", p, s4, todo, TaskStatus.TODO, "Webhook Integration",
                TaskType.TASK, TaskPriority.LOW, dev3, pm, 8,
                null, LocalDate.now().plusDays(12), bd(8.0),
                "Notify external services khi task thay đổi trạng thái qua webhook.", 2);

        // ── Backlog (no sprint) ──
        t("DEMO-033", p, null, todo, TaskStatus.TODO, "Custom Field — Backlog",
                TaskType.STORY, TaskPriority.LOW, null, pm, 13,
                null, null, null,
                "PM tạo custom field cho project: text, number, date, select.", 3);
    }

    // ─── SEED 7: ECOM tasks (~20 tasks) ──────────────────────────────────────

    @Transactional
    void seedEcomTasks() {
        if (taskRepository.findByTaskCode("ECOM-001").isPresent()) return;

        Project p  = projectRepository.findByProjectKey("ECOM").orElseThrow();
        User pm    = u("pm@tasksphere.local");
        User dev1  = u("dev1@tasksphere.local");
        User dev3  = u("dev3@tasksphere.local");
        User dev4  = u("dev4@tasksphere.local");
        User qa    = u("qa@tasksphere.local");
        User ba    = u("ba@tasksphere.local");

        var sprints = sprintRepository.findByProjectOrderByStartDateAsc(p);
        Sprint s1 = sprints.get(0); // COMPLETED
        Sprint s2 = sprints.get(1); // ACTIVE
        Sprint s3 = sprints.get(2); // PLANNED

        var cols = projectStatusColumnRepository.findByProjectOrderBySortOrderAsc(p);
        ProjectStatusColumn todo    = cols.get(0);
        ProjectStatusColumn inProg  = cols.get(1);
        ProjectStatusColumn review  = cols.get(2);
        ProjectStatusColumn testing = cols.get(3);
        ProjectStatusColumn done    = cols.get(4);

        // ── Sprint 1 — DONE ──
        t("ECOM-001", p, s1, done, TaskStatus.DONE, "Phân tích yêu cầu & viết BRD",
                TaskType.STORY, TaskPriority.HIGH, ba, pm, 8,
                LocalDate.now().minusDays(42), LocalDate.now().minusDays(36), bd(16.0),
                "Làm việc với stakeholders, document user stories và acceptance criteria.", 1);

        t("ECOM-002", p, s1, done, TaskStatus.DONE, "Thiết kế DB — Products & Categories",
                TaskType.TASK, TaskPriority.HIGH, ba, pm, 5,
                LocalDate.now().minusDays(40), LocalDate.now().minusDays(34), bd(8.0),
                "ERD cho Product, Category, ProductVariant, ProductImage với full-text search.", 2);

        t("ECOM-003", p, s1, done, TaskStatus.DONE, "Product Catalog REST API",
                TaskType.TASK, TaskPriority.HIGH, dev3, pm, 8,
                LocalDate.now().minusDays(38), LocalDate.now().minusDays(30), bd(12.0),
                "CRUD sản phẩm, phân loại, tìm kiếm, filter, sort và pagination.", 3);

        t("ECOM-004", p, s1, done, TaskStatus.DONE, "Product Listing UI (Next.js)",
                TaskType.TASK, TaskPriority.HIGH, dev4, pm, 8,
                LocalDate.now().minusDays(36), LocalDate.now().minusDays(30), bd(10.0),
                "Trang danh sách sản phẩm với filter sidebar, grid/list view, infinite scroll.", 4);

        t("ECOM-005", p, s1, done, TaskStatus.DONE, "Product Detail Page",
                TaskType.TASK, TaskPriority.MEDIUM, dev4, pm, 5,
                LocalDate.now().minusDays(34), LocalDate.now().minusDays(30), bd(6.0),
                "Trang chi tiết sản phẩm: image gallery, variants selector, description.", 5);

        t("ECOM-006", p, s1, done, TaskStatus.DONE, "User Authentication — Frontend",
                TaskType.TASK, TaskPriority.HIGH, dev4, pm, 3,
                LocalDate.now().minusDays(35), LocalDate.now().minusDays(32), bd(4.0),
                "Login/signup forms, JWT handling, protected routes, remember me.", 6);

        // ── Sprint 2 — ACTIVE ──
        Task cartEpic = taskRepository.save(task("ECOM-007", p, s2, inProg, TaskStatus.IN_PROGRESS,
                "Epic: Shopping Cart & Checkout Flow",
                TaskType.EPIC, TaskPriority.HIGH, null, pm, 34,
                LocalDate.now().minusDays(14), LocalDate.now().plusDays(7), null,
                "Luồng từ thêm giỏ → checkout → thanh toán → order confirmation.", 1));
        sub("ECOM-008", p, done,    "Cart Service — Persist in Redis",         TaskPriority.HIGH,   dev3, pm, cartEpic);
        sub("ECOM-009", p, done,    "Cart UI — Mini cart & Cart page",         TaskPriority.HIGH,   dev4, pm, cartEpic);
        sub("ECOM-010", p, inProg,  "Checkout Form — Address & Shipping",      TaskPriority.HIGH,   dev4, pm, cartEpic);
        sub("ECOM-011", p, inProg,  "VNPay Payment Gateway Integration",       TaskPriority.HIGH,   dev3, pm, cartEpic);
        sub("ECOM-012", p, todo,    "Order Confirmation Email",                TaskPriority.MEDIUM, dev3, pm, cartEpic);
        sub("ECOM-013", p, todo,    "E2E Test — Checkout Flow",                TaskPriority.HIGH,   qa,  pm, cartEpic);

        t("ECOM-014", p, s2, review, TaskStatus.IN_PROGRESS, "Product Search — Elasticsearch",
                TaskType.TASK, TaskPriority.HIGH, dev3, pm, 8,
                LocalDate.now().minusDays(10), LocalDate.now().plusDays(4), bd(12.0),
                "Elasticsearch full-text search sản phẩm, autocomplete, faceted filter.", 2);

        t("ECOM-015", p, s2, testing, TaskStatus.IN_PROGRESS, "Discount & Coupon System",
                TaskType.TASK, TaskPriority.MEDIUM, dev1, pm, 5,
                LocalDate.now().minusDays(8), LocalDate.now().plusDays(2), bd(6.0),
                "Mã giảm giá: percent/fixed, min order, expiry date, usage limit.", 3);

        t("ECOM-016", p, s2, todo, TaskStatus.TODO, "Fix: Giá sản phẩm tính VAT 2 lần",
                TaskType.BUG, TaskPriority.CRITICAL, dev3, qa, 2,
                LocalDate.now().minusDays(3), LocalDate.now().plusDays(1), bd(2.0),
                "ProductDTO apply VAT ở service layer, frontend formatter lại apply lần nữa.", 4);

        t("ECOM-017", p, s2, inProg, TaskStatus.IN_PROGRESS, "Admin Dashboard — Order Management",
                TaskType.STORY, TaskPriority.HIGH, dev4, pm, 8,
                LocalDate.now().minusDays(6), LocalDate.now().plusDays(5), bd(10.0),
                "Màn hình quản lý đơn hàng cho admin: view, filter, update status, export.", 5);

        // ── Sprint 3 — PLANNED ──
        t("ECOM-018", p, s3, todo, TaskStatus.TODO, "Inventory Management API",
                TaskType.STORY, TaskPriority.MEDIUM, dev1, pm, 8,
                null, LocalDate.now().plusDays(15), bd(10.0),
                "Quản lý tồn kho: update số lượng, cảnh báo hết hàng, nhập kho.", 1);

        t("ECOM-019", p, s3, todo, TaskStatus.TODO, "Product Reviews & Ratings",
                TaskType.TASK, TaskPriority.LOW, dev4, pm, 5,
                null, LocalDate.now().plusDays(18), bd(6.0),
                "Rating 1-5 sao, viết review, like/dislike, moderation queue.", 2);

        // ── Backlog ──
        t("ECOM-020", p, null, todo, TaskStatus.TODO, "Multi-vendor Support — Backlog",
                TaskType.STORY, TaskPriority.LOW, null, pm, 21,
                null, null, null,
                "Cho phép nhiều seller đăng ký và bán hàng trên platform.", 3);
    }

    // ─── SEED 8: MOBILE tasks (~11 tasks) ────────────────────────────────────

    @Transactional
    void seedMobileTasks() {
        if (taskRepository.findByTaskCode("MOBILE-001").isPresent()) return;

        Project p     = projectRepository.findByProjectKey("MOBILE").orElseThrow();
        User pm       = u("pm@tasksphere.local");
        User dev2     = u("dev2@tasksphere.local");
        User dev4     = u("dev4@tasksphere.local");
        User qa       = u("qa@tasksphere.local");
        User designer = u("designer@tasksphere.local");

        var sprints = sprintRepository.findByProjectOrderByStartDateAsc(p);
        Sprint s1 = sprints.get(0); // ACTIVE
        Sprint s2 = sprints.get(1); // PLANNED

        var cols = projectStatusColumnRepository.findByProjectOrderBySortOrderAsc(p);
        ProjectStatusColumn todo    = cols.get(0);
        ProjectStatusColumn inProg  = cols.get(1);
        ProjectStatusColumn done    = cols.get(4);

        // ── Sprint 1 — ACTIVE ──
        t("MOBILE-001", p, s1, done, TaskStatus.DONE, "React Native Setup & Navigation",
                TaskType.TASK, TaskPriority.HIGH, dev4, pm, 3,
                LocalDate.now().minusDays(7), LocalDate.now().minusDays(4), bd(4.0),
                "Init Expo project, React Navigation v6, tab + stack navigator, theme provider.", 1);

        t("MOBILE-002", p, s1, done, TaskStatus.DONE, "Design System — Component Library",
                TaskType.STORY, TaskPriority.HIGH, designer, pm, 8,
                LocalDate.now().minusDays(7), LocalDate.now().minusDays(3), bd(12.0),
                "Component library: Button, Input, Card, Badge, Avatar, Modal theo Figma spec.", 2);

        t("MOBILE-003", p, s1, inProg, TaskStatus.IN_PROGRESS, "Authentication Screens (Login/OTP)",
                TaskType.TASK, TaskPriority.HIGH, dev4, pm, 5,
                LocalDate.now().minusDays(5), LocalDate.now().plusDays(2), bd(6.0),
                "Màn hình đăng nhập, đăng ký, OTP, forgot password với animation.", 3);

        t("MOBILE-004", p, s1, inProg, TaskStatus.IN_PROGRESS, "Biometric Auth (FaceID/TouchID)",
                TaskType.TASK, TaskPriority.MEDIUM, dev2, pm, 3,
                LocalDate.now().minusDays(3), LocalDate.now().plusDays(4), bd(4.0),
                "expo-local-authentication để unlock app bằng biometrics.", 4);

        t("MOBILE-005", p, s1, todo, TaskStatus.TODO, "Push Notification — FCM & APNs",
                TaskType.TASK, TaskPriority.HIGH, dev2, pm, 5,
                LocalDate.now().minusDays(1), LocalDate.now().plusDays(6), bd(6.0),
                "Cấu hình Firebase Cloud Messaging và Apple Push Notification Service.", 5);

        t("MOBILE-006", p, s1, todo, TaskStatus.TODO, "Offline Mode — AsyncStorage & Sync",
                TaskType.STORY, TaskPriority.MEDIUM, dev2, pm, 13,
                null, LocalDate.now().plusDays(7), bd(16.0),
                "Cache task data locally, sync khi có network, conflict resolution strategy.", 6);

        t("MOBILE-007", p, s1, todo, TaskStatus.TODO, "Fix: Keyboard overlap trên Android",
                TaskType.BUG, TaskPriority.HIGH, dev4, qa, 2,
                LocalDate.now(), LocalDate.now().plusDays(2), bd(1.5),
                "KeyboardAvoidingView chưa hoạt động đúng trên Android — che khuất input field.", 7);

        // ── Sprint 2 — PLANNED ──
        t("MOBILE-008", p, s2, todo, TaskStatus.TODO, "Dashboard Screen — Project Overview",
                TaskType.TASK, TaskPriority.HIGH, dev4, pm, 5,
                null, LocalDate.now().plusDays(14), bd(6.0),
                "Home: projects, my tasks, upcoming deadlines, recent activity.", 1);

        t("MOBILE-009", p, s2, todo, TaskStatus.TODO, "Kanban Board — Touch Gestures",
                TaskType.STORY, TaskPriority.HIGH, dev4, pm, 13,
                null, LocalDate.now().plusDays(18), bd(16.0),
                "Kanban touch-optimized: swipe di chuyển task, long-press reorder.", 2);

        t("MOBILE-010", p, s2, todo, TaskStatus.TODO, "Task Detail Screen — Inline Edit",
                TaskType.TASK, TaskPriority.MEDIUM, dev2, pm, 8,
                null, LocalDate.now().plusDays(20), bd(10.0),
                "Chi tiết task: xem/sửa inline, checklist, comment, attachment.", 3);

        // ── Backlog ──
        t("MOBILE-011", p, null, todo, TaskStatus.TODO, "Dark Mode Support",
                TaskType.TASK, TaskPriority.LOW, designer, pm, 5,
                null, null, null,
                "useColorScheme hook, persist preference, tất cả screen hỗ trợ dark theme.", 4);
    }

    // ─── SEED 9: Versions ─────────────────────────────────────────────────────

    @Transactional
    void seedProjectVersions() {
        Project demo = projectRepository.findByProjectKey("DEMO").orElse(null);
        Project ecom = projectRepository.findByProjectKey("ECOM").orElse(null);
        if (demo == null || ecom == null) return;

        ProjectVersion demoV10 = ensureVersion(demo, "v1.0.0", VersionStatus.IN_PROGRESS,
                LocalDate.now().plusDays(20), "Phát hành bản core features cho nội bộ.");
        ProjectVersion demoV11 = ensureVersion(demo, "v1.1.0", VersionStatus.PLANNING,
                LocalDate.now().plusDays(45), "Mở rộng automation và webhook integration.");
        ProjectVersion ecomV09 = ensureVersion(ecom, "v0.9.0-beta", VersionStatus.IN_PROGRESS,
                LocalDate.now().plusDays(14), "Beta checkout + payment.");
        ProjectVersion ecomV10 = ensureVersion(ecom, "v1.0.0", VersionStatus.PLANNING,
                LocalDate.now().plusDays(35), "Production-ready e-commerce release.");

        assignVersionIfMissing("DEMO-025", demoV10);
        assignVersionIfMissing("DEMO-027", demoV10);
        assignVersionIfMissing("DEMO-031", demoV11);
        assignVersionIfMissing("DEMO-032", demoV11);
        assignVersionIfMissing("ECOM-011", ecomV09);
        assignVersionIfMissing("ECOM-014", ecomV09);
        assignVersionIfMissing("ECOM-018", ecomV10);
    }

    private ProjectVersion ensureVersion(Project project, String name, VersionStatus status,
                                         LocalDate releaseDate, String description) {
        return projectVersionRepository.findByProject_IdAndName(project.getId(), name)
                .map(existing -> {
                    if (existing.getDeletedAt() != null) {
                        existing.setDeletedAt(null);
                    }
                    existing.setStatus(status);
                    existing.setReleaseDate(releaseDate);
                    existing.setDescription(description);
                    return projectVersionRepository.save(existing);
                })
                .orElseGet(() -> projectVersionRepository.save(ProjectVersion.builder()
                        .project(project)
                        .name(name)
                        .status(status)
                        .releaseDate(releaseDate)
                        .description(description)
                        .build()));
    }

    private void assignVersionIfMissing(String taskCode, ProjectVersion version) {
        Task task = taskRepository.findByTaskCode(taskCode).orElse(null);
        if (task == null || task.getProjectVersion() != null) return;
        task.setProjectVersion(version);
        taskRepository.save(task);
    }

    // ─── SEED 10: Task dependencies ───────────────────────────────────────────

    @Transactional
    void seedTaskDependencies() {
        addDependency("DEMO-011", "DEMO-013", DependencyType.BLOCKS, "pm@tasksphere.local");
        addDependency("DEMO-013", "DEMO-011", DependencyType.BLOCKED_BY, "pm@tasksphere.local");

        addDependency("DEMO-025", "DEMO-031", DependencyType.BLOCKS, "pm@tasksphere.local");
        addDependency("DEMO-031", "DEMO-025", DependencyType.BLOCKED_BY, "pm@tasksphere.local");

        addDependency("ECOM-011", "ECOM-013", DependencyType.BLOCKS, "pm@tasksphere.local");
        addDependency("ECOM-013", "ECOM-011", DependencyType.BLOCKED_BY, "pm@tasksphere.local");

        addDependency("MOBILE-003", "MOBILE-004", DependencyType.RELATES_TO, "pm@tasksphere.local");
        addDependency("MOBILE-004", "MOBILE-003", DependencyType.RELATES_TO, "pm@tasksphere.local");
    }

    private void addDependency(String blockingCode, String blockedCode, DependencyType type, String creatorEmail) {
        Task blocking = taskRepository.findByTaskCode(blockingCode).orElse(null);
        Task blocked = taskRepository.findByTaskCode(blockedCode).orElse(null);
        User creator = u(creatorEmail);
        if (blocking == null || blocked == null) return;
        if (taskDependencyRepository.existsByBlockingTaskIdAndBlockedTaskId(blocking.getId(), blocked.getId())) return;
        taskDependencyRepository.save(TaskDependency.builder()
                .blockingTask(blocking)
                .blockedTask(blocked)
                .linkType(type)
                .createdBy(creator)
                .build());
    }

    // ─── SEED 11: Custom fields + values ──────────────────────────────────────

    @Transactional
    void seedCustomFieldsAndValues() {
        Project demo = projectRepository.findByProjectKey("DEMO").orElse(null);
        Project ecom = projectRepository.findByProjectKey("ECOM").orElse(null);
        if (demo == null || ecom == null) return;

        CustomField impact = ensureCustomField(demo, "Business Impact", CustomFieldType.SELECT, true, false, 1,
                "[\"LOW\",\"MEDIUM\",\"HIGH\",\"CRITICAL\"]");
        CustomField effort = ensureCustomField(demo, "Estimated Effort (days)", CustomFieldType.NUMBER, false, false, 2,
                null);
        CustomField customerVisible = ensureCustomField(demo, "Customer Visible", CustomFieldType.BOOLEAN, false, false, 3,
                null);
        CustomField releaseNote = ensureCustomField(demo, "Release Note", CustomFieldType.TEXT, false, false, 4,
                null);

        CustomField channel = ensureCustomField(ecom, "Sales Channel", CustomFieldType.SELECT, false, false, 1,
                "[\"WEB\",\"MOBILE\",\"MARKETPLACE\"]");
        CustomField campaign = ensureCustomField(ecom, "Campaign URL", CustomFieldType.URL, false, false, 2,
                null);

        upsertCustomValue("DEMO-028", impact, "CRITICAL", null, null, null);
        upsertCustomValue("DEMO-028", effort, null, bd(1.0), null, null);
        upsertCustomValue("DEMO-028", customerVisible, null, null, null, true);
        upsertCustomValue("DEMO-028", releaseNote, "Fix drag/drop position race condition for Kanban board.", null, null, null);

        upsertCustomValue("DEMO-031", impact, "MEDIUM", null, null, null);
        upsertCustomValue("DEMO-031", effort, null, bd(5.0), null, null);

        upsertCustomValue("ECOM-011", channel, "WEB", null, null, null);
        upsertCustomValue("ECOM-011", campaign, "https://sandbox.vnpay.vn/demo", null, null, null);
    }

    private CustomField ensureCustomField(Project project, String name, CustomFieldType type, boolean required,
                                          boolean hidden, int order, String options) {
        if (customFieldRepository.existsByProjectIdAndNameAndDeletedAtIsNull(project.getId(), name)) {
            return customFieldRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(project.getId())
                    .stream()
                    .filter(cf -> name.equals(cf.getName()))
                    .findFirst()
                    .orElseThrow();
        }
        return customFieldRepository.save(CustomField.builder()
                .project(project)
                .name(name)
                .fieldType(type)
                .isRequired(required)
                .isHidden(hidden)
                .sortOrder(order)
                .options(options)
                .build());
    }

    private void upsertCustomValue(String taskCode, CustomField field,
                                   String text, BigDecimal number, LocalDate date, Boolean bool) {
        Task task = taskRepository.findByTaskCode(taskCode).orElse(null);
        if (task == null) return;
        if (customFieldValueRepository.findByTaskIdAndCustomFieldId(task.getId(), field.getId()).isPresent()) return;
        customFieldValueRepository.save(CustomFieldValue.builder()
                .task(task)
                .customField(field)
                .textValue(text)
                .numberValue(number)
                .dateValue(date)
                .booleanValue(bool)
                .build());
    }

    // ─── SEED 12: Saved filters ───────────────────────────────────────────────

    @Transactional
    void seedSavedFilters() {
        Project demo = projectRepository.findByProjectKey("DEMO").orElse(null);
        Project ecom = projectRepository.findByProjectKey("ECOM").orElse(null);
        User pm = u("pm@tasksphere.local");
        User qa = u("qa@tasksphere.local");
        if (demo == null || ecom == null) return;

        if (savedFilterRepository.findByProjectIdAndCreatedByIdOrderByCreatedAtDesc(demo.getId(), pm.getId()).isEmpty()) {
            savedFilterRepository.save(SavedFilter.builder()
                    .project(demo)
                    .createdBy(pm)
                    .name("Critical Bugs - Current Sprint")
                    .isPublic(true)
                    .filterCriteria("{\"sprint\":\"ACTIVE\",\"type\":[\"BUG\"],\"priority\":[\"CRITICAL\",\"HIGH\"],\"status\":[\"TODO\",\"IN_PROGRESS\"]}")
                    .build());
            savedFilterRepository.save(SavedFilter.builder()
                    .project(demo)
                    .createdBy(pm)
                    .name("Backlog Refinement Queue")
                    .isPublic(false)
                    .filterCriteria("{\"sprint\":\"BACKLOG\",\"sort\":\"priority_desc\",\"assignee\":\"unassigned\"}")
                    .build());
        }

        if (savedFilterRepository.findByProjectIdAndCreatedByIdOrderByCreatedAtDesc(ecom.getId(), qa.getId()).isEmpty()) {
            savedFilterRepository.save(SavedFilter.builder()
                    .project(ecom)
                    .createdBy(qa)
                    .name("QA - Needs Regression")
                    .isPublic(true)
                    .filterCriteria("{\"status\":[\"IN_REVIEW\",\"TESTING\"],\"labels\":[\"regression\"],\"assignee\":[\"qa@tasksphere.local\"]}")
                    .build());
        }
    }

    // ─── SEED 13: Recurring configs ───────────────────────────────────────────

    @Transactional
    void seedRecurringTaskConfigs() {
        Task demo = taskRepository.findByTaskCode("DEMO-033").orElse(null);
        Task mobile = taskRepository.findByTaskCode("MOBILE-011").orElse(null);
        if (demo != null && !recurringTaskConfigRepository.existsByTaskId(demo.getId())) {
            demo.setRecurring(true);
            taskRepository.save(demo);
            recurringTaskConfigRepository.save(RecurringTaskConfig.builder()
                    .task(demo)
                    .frequency(RecurringFrequency.WEEKLY)
                    .recurrenceInterval(1)
                    .dayOfWeek("MONDAY")
                    .startDate(LocalDate.now().minusDays(7))
                    .endDate(LocalDate.now().plusMonths(3))
                    .maxOccurrences(24)
                    .nextRunAt(Instant.now().plusSeconds(2 * 24 * 3600))
                    .status(RecurrenceStatus.PAUSED)
                    .frequencyConfig("{\"timezone\":\"Asia/Ho_Chi_Minh\"}")
                    .build());
        }
        if (mobile != null && !recurringTaskConfigRepository.existsByTaskId(mobile.getId())) {
            mobile.setRecurring(true);
            taskRepository.save(mobile);
            recurringTaskConfigRepository.save(RecurringTaskConfig.builder()
                    .task(mobile)
                    .frequency(RecurringFrequency.MONTHLY)
                    .recurrenceInterval(1)
                    .dayOfMonth(1)
                    .startDate(LocalDate.now())
                    .maxOccurrences(12)
                    .nextRunAt(Instant.now().plusSeconds(4 * 24 * 3600))
                    .status(RecurrenceStatus.ACTIVE)
                    .frequencyConfig("{\"carry_over_checklist\":true}")
                    .build());
        }
    }

    // ─── SEED 14: Project invites ─────────────────────────────────────────────

    @Transactional
    void seedProjectInvites() {
        Project ecom = projectRepository.findByProjectKey("ECOM").orElse(null);
        User pm = u("pm@tasksphere.local");
        User dev2 = u("dev2@tasksphere.local");
        if (ecom == null) return;

        inviteIfMissing("seed-ecom-pending-viewer", ecom, pm, "new.viewer@tasksphere.local",
                ProjectRole.VIEWER, InviteStatus.PENDING, Instant.now().plusSeconds(5 * 24 * 3600), null, null);
        inviteIfMissing("seed-ecom-accepted-dev2", ecom, pm, dev2.getEmail(),
                ProjectRole.MEMBER, InviteStatus.ACCEPTED, Instant.now().plusSeconds(7 * 24 * 3600), Instant.now().minusSeconds(2 * 24 * 3600), dev2);
        inviteIfMissing("seed-ecom-expired-qa", ecom, pm, "expired.qa@tasksphere.local",
                ProjectRole.MEMBER, InviteStatus.EXPIRED, Instant.now().minusSeconds(2 * 24 * 3600), null, null);
        inviteIfMissing("seed-ecom-revoked-ba", ecom, pm, "revoked.ba@tasksphere.local",
                ProjectRole.MEMBER, InviteStatus.REVOKED, Instant.now().plusSeconds(2 * 24 * 3600), null, null);
    }

    private void inviteIfMissing(String token, Project project, User inviter, String inviteeEmail,
                                 ProjectRole role, InviteStatus status, Instant expiresAt,
                                 Instant acceptedAt, User inviteeUser) {
        if (projectInviteRepository.findByToken(token).isPresent()) return;
        projectInviteRepository.save(ProjectInvite.builder()
                .project(project)
                .invitedBy(inviter)
                .inviteeEmail(inviteeEmail)
                .inviteeUser(inviteeUser)
                .token(token)
                .projectRole(role)
                .status(status)
                .expiresAt(expiresAt)
                .acceptedAt(acceptedAt)
                .build());
    }

    // ─── SEED 15: Comments mentions ───────────────────────────────────────────

    @Transactional
    void seedCommentMentions() {
        Task t002 = taskRepository.findByTaskCode("DEMO-002").orElse(null);
        Task t028 = taskRepository.findByTaskCode("DEMO-028").orElse(null);
        if (t002 == null || t028 == null) return;

        commentRepository.findByTaskAndParentCommentIsNullOrderByCreatedAtAsc(t002)
                .stream()
                .findFirst()
                .ifPresent(comment -> mentionIfMissing(comment, "pm@tasksphere.local"));

        commentRepository.findByTaskAndParentCommentIsNullOrderByCreatedAtAsc(t028)
                .stream()
                .findFirst()
                .ifPresent(comment -> {
                    mentionIfMissing(comment, "dev1@tasksphere.local");
                    mentionIfMissing(comment, "qa@tasksphere.local");
                });
    }

    private void mentionIfMissing(Comment comment, String mentionedEmail) {
        User mentioned = u(mentionedEmail);
        boolean exists = commentMentionRepository.findByCommentId(comment.getId())
                .stream()
                .anyMatch(m -> m.getMentionedUser().getId().equals(mentioned.getId()));
        if (exists) return;
        commentMentionRepository.save(CommentMention.builder()
                .comment(comment)
                .mentionedUser(mentioned)
                .build());
    }

    // ─── SEED 16: Notifications ───────────────────────────────────────────────

    @Transactional
    void seedNotifications() {
        boolean seeded = notificationRepository.findAll().stream()
                .anyMatch(n -> n.getTitle() != null && n.getTitle().startsWith("[SEED]"));
        if (seeded) return;

        User pm = u("pm@tasksphere.local");
        User dev1 = u("dev1@tasksphere.local");
        User qa = u("qa@tasksphere.local");
        Task t028 = taskRepository.findByTaskCode("DEMO-028").orElse(null);
        Task t011 = taskRepository.findByTaskCode("DEMO-011").orElse(null);
        Project demo = projectRepository.findByProjectKey("DEMO").orElse(null);

        if (t028 != null) {
            notificationRepository.save(Notification.builder()
                    .recipient(dev1)
                    .type(NotificationType.TASK_ASSIGNED)
                    .title("[SEED] Bạn được assign task CRITICAL")
                    .body("Task DEMO-028 vừa được giao cho bạn.")
                    .entityType(EntityType.TASK.name())
                    .entityId(t028.getId())
                    .build());
        }
        if (t011 != null) {
            notificationRepository.save(Notification.builder()
                    .recipient(qa)
                    .type(NotificationType.TASK_COMMENTED)
                    .title("[SEED] Có bình luận mới")
                    .body("Task DEMO-011 có bình luận mới từ PM.")
                    .entityType(EntityType.TASK.name())
                    .entityId(t011.getId())
                    .isRead(true)
                    .readAt(Instant.now().minusSeconds(3600))
                    .build());
        }
        if (demo != null) {
            notificationRepository.save(Notification.builder()
                    .recipient(pm)
                    .type(NotificationType.SYSTEM_ANNOUNCEMENT)
                    .title("[SEED] System announcement")
                    .body("Môi trường DEV đã seed đầy đủ dữ liệu để QA regression.")
                    .entityType(EntityType.PROJECT.name())
                    .entityId(demo.getId())
                    .build());
        }
    }

    // ─── SEED 17: Attachments + upload jobs ───────────────────────────────────

    @Transactional
    void seedAttachments() {
        Task t025 = taskRepository.findByTaskCode("DEMO-025").orElse(null);
        Task t011 = taskRepository.findByTaskCode("ECOM-011").orElse(null);
        User dev2 = u("dev2@tasksphere.local");
        User dev3 = u("dev3@tasksphere.local");

        if (t025 != null && attachmentRepository.countByTaskIdAndCommentIsNull(t025.getId()) == 0) {
            attachmentRepository.save(Attachment.builder()
                    .task(t025)
                    .uploadedBy(dev2)
                    .originalFilename("aws-s3-upload-flow.png")
                    .storedFilename("seed-aws-s3-upload-flow.png")
                    .s3Key("seed/demo/DEMO-025/aws-s3-upload-flow.png")
                    .fileSize(184_320L)
                    .contentType("image/png")
                    .attachmentType(AttachmentType.IMAGE)
                    .previewUrl("https://cdn.tasksphere.local/seed/demo/DEMO-025/aws-s3-upload-flow.png")
                    .build());
        }

        if (t011 != null && attachmentRepository.countByTaskIdAndCommentIsNull(t011.getId()) == 0) {
            attachmentRepository.save(Attachment.builder()
                    .task(t011)
                    .uploadedBy(dev3)
                    .originalFilename("vnpay-integration-spec.pdf")
                    .storedFilename("seed-vnpay-integration-spec.pdf")
                    .s3Key("seed/ecom/ECOM-011/vnpay-integration-spec.pdf")
                    .fileSize(562_000L)
                    .contentType("application/pdf")
                    .attachmentType(AttachmentType.DOCUMENT)
                    .previewUrl("https://cdn.tasksphere.local/seed/ecom/ECOM-011/vnpay-integration-spec.pdf")
                    .build());
        }
    }

    @Transactional
    void seedUploadJobs() {
        if (uploadJobRepository.findAll().stream().anyMatch(j -> "seed-evidence.zip".equals(j.getOriginalFileName()))) {
            return;
        }
        Task t028 = taskRepository.findByTaskCode("DEMO-028").orElse(null);
        Task t025 = taskRepository.findByTaskCode("DEMO-025").orElse(null);
        User qa = u("qa@tasksphere.local");
        User dev2 = u("dev2@tasksphere.local");
        if (t028 != null) {
            uploadJobRepository.save(UploadJob.builder()
                    .task(t028)
                    .uploadedBy(qa)
                    .originalFileName("seed-evidence.zip")
                    .fileSize(2_450_000L)
                    .mimeType("application/zip")
                    .tempStorageKey("tmp/seed/seed-evidence.zip")
                    .status(UploadJobStatus.FAILED)
                    .errorMessage("Virus scan timeout in development environment")
                    .completedAt(Instant.now().minusSeconds(7200))
                    .build());
        }
        if (t025 != null) {
            uploadJobRepository.save(UploadJob.builder()
                    .task(t025)
                    .uploadedBy(dev2)
                    .originalFileName("api-contract-v2.docx")
                    .fileSize(128_300L)
                    .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .tempStorageKey("tmp/seed/api-contract-v2.docx")
                    .status(UploadJobStatus.DONE)
                    .completedAt(Instant.now().minusSeconds(3600))
                    .build());
        }
    }

    // ─── SEED 18: Webhooks + delivery logs ────────────────────────────────────

    @Transactional
    void seedWebhooks() {
        Project demo = projectRepository.findByProjectKey("DEMO").orElse(null);
        if (demo == null) return;
        User admin = u("admin@tasksphere.local");

        Webhook webhook = webhookRepository.findByProjectIdAndDeletedAtIsNull(demo.getId())
                .stream()
                .findFirst()
                .orElseGet(() -> webhookRepository.save(Webhook.builder()
                        .project(demo)
                        .createdBy(admin)
                        .name("Seed Discord Webhook")
                        .url("https://example.com/webhook/tasksphere-demo")
                        .secret("seed-demo-webhook-secret")
                        .events("[\"TASK_CREATED\",\"TASK_STATUS_CHANGED\",\"COMMENT_CREATED\"]")
                        .isActive(true)
                        .lastTriggeredAt(Instant.now().minusSeconds(600))
                        .failureCount(0)
                        .build()));

        if (webhookDeliveryLogRepository.findByWebhookIdOrderByDeliveredAtDesc(webhook.getId()).isEmpty()) {
            webhookDeliveryLogRepository.save(WebhookDeliveryLog.builder()
                    .webhook(webhook)
                    .eventType("task.status_changed")
                    .requestBody("{\"taskCode\":\"DEMO-029\",\"oldStatus\":\"TODO\",\"newStatus\":\"IN_PROGRESS\"}")
                    .responseStatus(200)
                    .responseBody("{\"ok\":true}")
                    .success(true)
                    .attemptNumber(1)
                    .build());
            webhookDeliveryLogRepository.save(WebhookDeliveryLog.builder()
                    .webhook(webhook)
                    .eventType("task.created")
                    .requestBody("{\"taskCode\":\"DEMO-030\"}")
                    .responseStatus(500)
                    .responseBody("{\"error\":\"internal\"}")
                    .success(false)
                    .attemptNumber(2)
                    .build());
        }
    }

    // ─── SEED 19: Export jobs ─────────────────────────────────────────────────

    @Transactional
    void seedExportJobs() {
        boolean seeded = exportJobRepository.findAll().stream()
                .anyMatch(j -> "exports/seed/demo-tasks.xlsx".equals(j.getStorageKey()));
        if (seeded) return;

        Project demo = projectRepository.findByProjectKey("DEMO").orElse(null);
        Project ecom = projectRepository.findByProjectKey("ECOM").orElse(null);
        User pm = u("pm@tasksphere.local");
        if (demo == null || ecom == null) return;

        Sprint demoActive = sprintRepository.findByProjectOrderByStartDateAsc(demo).stream()
                .filter(s -> s.getStatus() == SprintStatus.ACTIVE)
                .findFirst().orElse(null);

        exportJobRepository.save(ExportJob.builder()
                .project(demo)
                .requestedBy(pm)
                .format(ExportFormat.EXCEL)
                .scope(ExportScope.ALL)
                .status(ExportJobStatus.DONE)
                .storageKey("exports/seed/demo-tasks.xlsx")
                .downloadUrl("https://cdn.tasksphere.local/exports/seed/demo-tasks.xlsx")
                .rowCount(33)
                .expiresAt(Instant.now().plusSeconds(3 * 24 * 3600))
                .completedAt(Instant.now().minusSeconds(1800))
                .build());

        exportJobRepository.save(ExportJob.builder()
                .project(ecom)
                .requestedBy(pm)
                .format(ExportFormat.PDF)
                .scope(ExportScope.FILTERED)
                .status(ExportJobStatus.FAILED)
                .errorMessage("Template rendering failed: missing font file")
                .completedAt(Instant.now().minusSeconds(2400))
                .build());

        exportJobRepository.save(ExportJob.builder()
                .project(demo)
                .requestedBy(pm)
                .format(ExportFormat.EXCEL)
                .scope(ExportScope.SPRINT)
                .sprintId(demoActive != null ? demoActive.getId() : null)
                .status(ExportJobStatus.EXPIRED)
                .storageKey("exports/seed/demo-active-sprint.xlsx")
                .downloadUrl("https://cdn.tasksphere.local/exports/seed/demo-active-sprint.xlsx")
                .rowCount(12)
                .expiresAt(Instant.now().minusSeconds(600))
                .completedAt(Instant.now().minusSeconds(7200))
                .build());
    }

    // Helper — save and return task
    private void t(String code, Project p, Sprint sprint, ProjectStatusColumn col,
                   TaskStatus status, String title, TaskType type, TaskPriority priority,
                   User assignee, User reporter, int pts,
                   LocalDate startDate, LocalDate dueDate, BigDecimal estHours,
                   String description, int position) {
        taskRepository.save(task(code, p, sprint, col, status, title, type, priority,
                assignee, reporter, pts, startDate, dueDate, estHours, description, position));
    }

    private Task task(String code, Project p, Sprint sprint, ProjectStatusColumn col,
                      TaskStatus status, String title, TaskType type, TaskPriority priority,
                      User assignee, User reporter, int pts,
                      LocalDate startDate, LocalDate dueDate, BigDecimal estHours,
                      String description, int position) {
        return Task.builder()
                .taskCode(code).project(p).sprint(sprint)
                .statusColumn(col).taskStatus(status).title(title)
                .type(type).priority(priority).assignee(assignee).reporter(reporter)
                .storyPoints(pts).startDate(startDate).dueDate(dueDate)
                .estimatedHours(estHours).description(description)
                .taskPosition(position).build();
    }

    private void sub(String code, Project p, ProjectStatusColumn col,
                     String title, TaskPriority priority,
                     User assignee, User reporter, Task parent) {
        TaskStatus status = col.getMappedStatus() != null ? col.getMappedStatus() : TaskStatus.TODO;
        taskRepository.save(Task.builder()
                .taskCode(code).project(p).parentTask(parent)
                .statusColumn(col).taskStatus(status)
                .title(title).type(TaskType.SUB_TASK)
                .priority(priority).assignee(assignee).reporter(reporter).build());
    }

    // ─── SEED 9: Comments ─────────────────────────────────────────────────────

    @Transactional
    void seedComments() {
        Task t002 = taskRepository.findByTaskCode("DEMO-002").orElse(null);
        if (t002 == null) return;
        if (!commentRepository.findByTaskAndParentCommentIsNullOrderByCreatedAtAsc(t002).isEmpty()) return;

        User admin = u("admin@tasksphere.local");
        User pm    = u("pm@tasksphere.local");
        User dev1  = u("dev1@tasksphere.local");
        User dev3  = u("dev3@tasksphere.local");
        User dev4  = u("dev4@tasksphere.local");
        User qa    = u("qa@tasksphere.local");
        User ba    = u("ba@tasksphere.local");

        // DEMO-002: Thiết kế ERD
        Comment c1 = commentRepository.save(Comment.builder().task(t002).author(ba)
                .content("<p>Đã hoàn thành bản ERD v1 — <strong>28 tables</strong> với đầy đủ relationships. @pm anh review và confirm trước khi team bắt đầu implement nhé!</p>").build());
        commentRepository.save(Comment.builder().task(t002).author(pm)
                .content("<p>Đã review, tổng thể ổn. Bổ sung thêm quan hệ giữa <code>Task</code> và <code>CustomField</code> — mình comment trực tiếp trên Figma.</p>")
                .parentComment(c1).build());
        commentRepository.save(Comment.builder().task(t002).author(ba)
                .content("<p>Đã update ERD v2 với CustomFieldValue table. Anh xem lại giúp mình!</p>")
                .parentComment(c1).build());
        commentRepository.save(Comment.builder().task(t002).author(pm)
                .content("<p>ERD v2 approved ✓. Team có thể bắt đầu implement được rồi.</p>")
                .parentComment(c1).build());

        // DEMO-011: Task CRUD
        Task t011 = taskRepository.findByTaskCode("DEMO-011").orElse(null);
        if (t011 != null) {
            Comment c2 = commentRepository.save(Comment.builder().task(t011).author(pm)
                    .content("<p>Lưu ý implement <strong>Optimistic Locking</strong> dùng <code>@Version</code> để tránh conflict khi nhiều user cùng edit task cùng lúc.</p>").build());
            commentRepository.save(Comment.builder().task(t011).author(dev1)
                    .content("<p>Đã handle. Conflict sẽ throw <code>OptimisticLockException</code>, frontend nhận 409 Conflict. Có retry logic ở client.</p>")
                    .parentComment(c2).build());
            commentRepository.save(Comment.builder().task(t011).author(qa)
                    .content("<p>Test concurrent update đã pass ✓. Thêm test case cho 3+ users cùng update để chắc chắn hơn.</p>").build());
        }

        // DEMO-028: Bug task position
        Task t028 = taskRepository.findByTaskCode("DEMO-028").orElse(null);
        if (t028 != null) {
            commentRepository.save(Comment.builder().task(t028).author(qa)
                    .content("<p><strong>Steps to reproduce:</strong><br/>" +
                             "1. Drag task từ column 'In Review' (position 3)<br/>" +
                             "2. Drop vào column 'Done'<br/>" +
                             "3. Position được set = 0 thay vì max+1<br/>" +
                             "<em>Repro rate: 100%</em></p>").build());
            Comment c3 = commentRepository.save(Comment.builder().task(t028).author(dev1)
                    .content("<p><strong>Root cause:</strong> <code>KanbanService.moveTask()</code> query MAX position trước khi insert nhưng không lock row → race condition khi nhiều user drag cùng lúc.</p>").build());
            commentRepository.save(Comment.builder().task(t028).author(pm)
                    .content("<p>Priority <strong>CRITICAL</strong> — fix trước sprint review. @dev1 ETA?</p>")
                    .parentComment(c3).build());
            commentRepository.save(Comment.builder().task(t028).author(dev1)
                    .content("<p>~2 tiếng. Sẽ dùng pessimistic lock (SELECT ... FOR UPDATE) cho query MAX position.</p>")
                    .parentComment(c3).build());
        }

        // DEMO-029: Refresh token bug
        Task t029 = taskRepository.findByTaskCode("DEMO-029").orElse(null);
        if (t029 != null) {
            commentRepository.save(Comment.builder().task(t029).author(dev3)
                    .content("<p><strong>Fix plan:</strong> Logout → set <code>RefreshToken.revokedAt = now()</code> + add token vào Redis blacklist (TTL = expire time).</p>").build());
            commentRepository.save(Comment.builder().task(t029).author(admin)
                    .content("<p>Good approach! Nhớ handle edge case khi Redis down — fallback về DB check để tránh allow revoked token.</p>").build());
        }

        // ECOM-016: Bug giá VAT
        Task e016 = taskRepository.findByTaskCode("ECOM-016").orElse(null);
        if (e016 != null) {
            commentRepository.save(Comment.builder().task(e016).author(qa)
                    .content("<p><strong>Bug confirmed:</strong> Giá 100k → hiển thị 110k (đúng) nhưng cart tính 121k (110k + 10% VAT lần 2). Ảnh hưởng <em>tất cả</em> sản phẩm có VAT.</p>").build());
            commentRepository.save(Comment.builder().task(e016).author(dev3)
                    .content("<p><strong>Found it:</strong> <code>ProductDTO.getEffectivePrice()</code> apply VAT 2 lần — một lần ở service layer, một lần ở frontend <code>priceFormatter</code>.</p>").build());
        }

        // MOBILE-007: Android keyboard bug
        Task m007 = taskRepository.findByTaskCode("MOBILE-007").orElse(null);
        if (m007 != null) {
            commentRepository.save(Comment.builder().task(m007).author(qa)
                    .content("<p>Repro: Android 13, Samsung Galaxy S23. Bấm vào input cuối form → keyboard hiện → che khuất input, không tự scroll lên.</p>").build());
            commentRepository.save(Comment.builder().task(m007).author(dev4)
                    .content("<p>Cần wrap bằng <code>KeyboardAwareScrollView</code> từ thư viện <code>react-native-keyboard-aware-scroll-view</code> thay vì dùng native <code>KeyboardAvoidingView</code>.</p>").build());
        }
    }

    // ─── SEED 10: Checklists ──────────────────────────────────────────────────

    @Transactional
    void seedChecklists() {
        // DEMO-005: JWT Auth
        Task t005 = taskRepository.findByTaskCode("DEMO-005").orElse(null);
        if (t005 != null && checklistItemRepository.findByTaskOrderBySortOrderAsc(t005).isEmpty()) {
            checklist(t005,
                new CK("Cài đặt Spring Security & jjwt dependency",         true),
                new CK("Implement JwtUtils — generate & validate token",     true),
                new CK("Tạo JwtFilter (OncePerRequestFilter)",               true),
                new CK("Config SecurityFilterChain — public/protected routes",true),
                new CK("Implement RefreshToken entity & repository",          true),
                new CK("Test login endpoint với Postman",                    true),
                new CK("Test refresh token endpoint",                        true),
                new CK("Viết unit test cho JwtUtils",                        false)
            );
        }

        // DEMO-011: Task CRUD
        Task t011 = taskRepository.findByTaskCode("DEMO-011").orElse(null);
        if (t011 != null && checklistItemRepository.findByTaskOrderBySortOrderAsc(t011).isEmpty()) {
            checklist(t011,
                new CK("Tạo Task entity với đầy đủ fields",                  true),
                new CK("Implement TaskRepository với custom queries",         true),
                new CK("CRUD endpoints: POST/GET/PUT/DELETE /api/tasks",     true),
                new CK("Sub-task & Epic hierarchy (parentTask)",             true),
                new CK("Optimistic locking với @Version",                    true),
                new CK("Dynamic filtering dùng JPA Specification",           true),
                new CK("Phân quyền: MEMBER tạo được, VIEWER chỉ đọc",      true),
                new CK("Integration tests cho task CRUD",                    false),
                new CK("Update Swagger docs",                                false)
            );
        }

        // DEMO-025: S3 Upload
        Task t025 = taskRepository.findByTaskCode("DEMO-025").orElse(null);
        if (t025 != null && checklistItemRepository.findByTaskOrderBySortOrderAsc(t025).isEmpty()) {
            checklist(t025,
                new CK("Config AWS S3 SDK dependency",                       true),
                new CK("Tạo S3Config với bucket name từ env",               true),
                new CK("Implement S3Service.upload() & delete()",           true),
                new CK("POST /api/attachments endpoint",                     false),
                new CK("Validate file type & size (max 25MB)",              false),
                new CK("Image thumbnail generation",                         false),
                new CK("Presigned URL cho private files",                    false)
            );
        }

        // ECOM-011: VNPay
        Task e011 = taskRepository.findByTaskCode("ECOM-011").orElse(null);
        if (e011 != null && checklistItemRepository.findByTaskOrderBySortOrderAsc(e011).isEmpty()) {
            checklist(e011,
                new CK("Đọc tài liệu VNPay API v2",                        true),
                new CK("Config VNPay sandbox credentials",                   true),
                new CK("Implement vnpay_payment_url generator",             true),
                new CK("Implement IPN handler (callback endpoint)",         false),
                new CK("Handle các mã lỗi VNPay phổ biến",                false),
                new CK("Test với thẻ sandbox",                              false),
                new CK("Test refund flow",                                   false)
            );
        }

        // MOBILE-003: Auth screens
        Task m003 = taskRepository.findByTaskCode("MOBILE-003").orElse(null);
        if (m003 != null && checklistItemRepository.findByTaskOrderBySortOrderAsc(m003).isEmpty()) {
            checklist(m003,
                new CK("Login screen UI theo Figma",                        true),
                new CK("Signup screen UI",                                   true),
                new CK("Form validation với react-hook-form + zod",        true),
                new CK("JWT storage dùng expo-secure-store",               false),
                new CK("OTP screen với auto-focus input",                   false),
                new CK("Forgot password flow",                              false),
                new CK("Test trên iOS simulator",                           false),
                new CK("Test trên Android emulator",                        false)
            );
        }
    }

    private record CK(String title, boolean done) {}

    private void checklist(Task task, CK... items) {
        for (int i = 0; i < items.length; i++) {
            checklistItemRepository.save(ChecklistItem.builder()
                    .task(task).title(items[i].title())
                    .isCompleted(items[i].done())
                    .sortOrder(i + 1).build());
        }
    }

    // ─── SEED 11: Worklogs ────────────────────────────────────────────────────

    @Transactional
    void seedWorklogs() {
        Task t005 = taskRepository.findByTaskCode("DEMO-005").orElse(null);
        if (t005 == null) return;
        if (!worklogRepository.findByTaskId(t005.getId()).isEmpty()) return;

        User dev1 = u("dev1@tasksphere.local");
        User dev2 = u("dev2@tasksphere.local");
        User dev3 = u("dev3@tasksphere.local");
        User dev4 = u("dev4@tasksphere.local");
        User qa   = u("qa@tasksphere.local");

        // DEMO-005: JWT Auth
        wl(t005, dev3, LocalDate.now().minusDays(50), 7200,  "Implement JWT generate/validate logic");
        wl(t005, dev3, LocalDate.now().minusDays(49), 10800, "Setup JwtFilter, integrate SecurityChain");
        wl(t005, dev3, LocalDate.now().minusDays(48), 5400,  "Implement refresh token & test");

        // DEMO-011: Task CRUD
        Task t011 = taskRepository.findByTaskCode("DEMO-011").orElse(null);
        if (t011 != null) {
            wl(t011, dev1, LocalDate.now().minusDays(38), 14400, "Tạo Task entity, repository, basic CRUD");
            wl(t011, dev1, LocalDate.now().minusDays(37), 10800, "Implement sub-task, epic hierarchy");
            wl(t011, dev1, LocalDate.now().minusDays(36), 7200,  "Optimistic locking & conflict handling");
            wl(t011, dev1, LocalDate.now().minusDays(35), 7200,  "JPA Specification dynamic filtering");
            wl(t011, qa,   LocalDate.now().minusDays(34), 3600,  "Test task CRUD endpoints");
        }

        // DEMO-013: Kanban Move API
        Task t013 = taskRepository.findByTaskCode("DEMO-013").orElse(null);
        if (t013 != null) {
            wl(t013, dev4, LocalDate.now().minusDays(36), 14400, "Design Kanban API contract");
            wl(t013, dev4, LocalDate.now().minusDays(35), 10800, "Implement moveTask với position recalculation");
            wl(t013, dev4, LocalDate.now().minusDays(34), 7200,  "Frontend drag-and-drop integration");
        }

        // DEMO-025: S3 Upload
        Task t025 = taskRepository.findByTaskCode("DEMO-025").orElse(null);
        if (t025 != null) {
            wl(t025, dev2, LocalDate.now().minusDays(5), 7200, "Setup S3Config, implement S3Service");
            wl(t025, dev2, LocalDate.now().minusDays(4), 3600, "Attachment upload endpoint");
        }

        // DEMO-015: UI/UX Design
        Task t015 = taskRepository.findByTaskCode("DEMO-015").orElse(null);
        if (t015 != null) {
            User designer = u("designer@tasksphere.local");
            wl(t015, designer, LocalDate.now().minusDays(42), 14400, "Research & mood board");
            wl(t015, designer, LocalDate.now().minusDays(41), 21600, "Wireframe Dashboard & Kanban");
            wl(t015, designer, LocalDate.now().minusDays(40), 21600, "Wireframe Task Detail & Sprint board");
            wl(t015, designer, LocalDate.now().minusDays(38), 14400, "Review feedback & revision");
            wl(t015, designer, LocalDate.now().minusDays(36), 10800, "Final handoff Figma");
        }

        // ECOM-014: Elasticsearch
        Task e014 = taskRepository.findByTaskCode("ECOM-014").orElse(null);
        if (e014 != null) {
            wl(e014, dev3, LocalDate.now().minusDays(10), 14400, "Setup Elasticsearch, index product mapping");
            wl(e014, dev3, LocalDate.now().minusDays(9),  10800, "Implement search với fuzzy matching");
            wl(e014, dev3, LocalDate.now().minusDays(8),  7200,  "Autocomplete & faceted filter");
        }
    }

    private void wl(Task task, User user, LocalDate date, int seconds, String desc) {
        worklogRepository.save(Worklog.builder()
                .task(task).user(user)
                .logDate(date).timeSpentSeconds(seconds)
                .description(desc).build());
    }

    // ─── SEED 12: Activity logs ───────────────────────────────────────────────

    @Transactional
    void seedActivityLogs() {
        Project demo = projectRepository.findByProjectKey("DEMO").orElse(null);
        if (demo == null) return;
        if (activityLogRepository.findByProjectId(demo.getId(), Pageable.ofSize(1)).hasContent()) return;

        User admin = u("admin@tasksphere.local");
        User pm    = u("pm@tasksphere.local");
        User dev1  = u("dev1@tasksphere.local");
        User dev3  = u("dev3@tasksphere.local");
        User qa    = u("qa@tasksphere.local");
        User ba    = u("ba@tasksphere.local");

        UUID demoId = demo.getId();
        Task t001   = taskRepository.findByTaskCode("DEMO-001").orElseThrow();
        Task t005   = taskRepository.findByTaskCode("DEMO-005").orElseThrow();
        Task t011   = taskRepository.findByTaskCode("DEMO-011").orElseThrow();
        Task t028   = taskRepository.findByTaskCode("DEMO-028").orElseThrow();
        Task t029   = taskRepository.findByTaskCode("DEMO-029").orElseThrow();

        log(admin, EntityType.PROJECT, demoId,         ActionType.CREATED,        null, "{\"name\":\"TaskSphere Platform\",\"key\":\"DEMO\"}", demoId);
        log(ba,    EntityType.TASK,    t001.getId(),   ActionType.CREATED,        null, "{\"taskCode\":\"DEMO-001\"}", demoId);
        log(dev3,  EntityType.TASK,    t005.getId(),   ActionType.STATUS_CHANGED, "{\"status\":\"TODO\"}",        "{\"status\":\"IN_PROGRESS\"}", demoId);
        log(dev3,  EntityType.TASK,    t005.getId(),   ActionType.STATUS_CHANGED, "{\"status\":\"IN_PROGRESS\"}", "{\"status\":\"DONE\"}",        demoId);
        log(dev1,  EntityType.TASK,    t011.getId(),   ActionType.ASSIGNED,       null, "{\"assignee\":\"Trần Lập Trình\",\"taskCode\":\"DEMO-011\"}", demoId);
        log(pm,    EntityType.TASK,    t011.getId(),   ActionType.UPDATED,        "{\"storyPoints\":8}", "{\"storyPoints\":13}", demoId);
        log(dev1,  EntityType.TASK,    t011.getId(),   ActionType.STATUS_CHANGED, "{\"status\":\"TODO\"}",        "{\"status\":\"IN_PROGRESS\"}", demoId);
        log(dev1,  EntityType.TASK,    t011.getId(),   ActionType.STATUS_CHANGED, "{\"status\":\"IN_PROGRESS\"}", "{\"status\":\"DONE\"}",        demoId);
        log(qa,    EntityType.TASK,    t028.getId(),   ActionType.CREATED,        null, "{\"taskCode\":\"DEMO-028\",\"type\":\"BUG\",\"priority\":\"CRITICAL\"}", demoId);
        log(dev1,  EntityType.TASK,    t028.getId(),   ActionType.STATUS_CHANGED, "{\"status\":\"TODO\"}",        "{\"status\":\"IN_PROGRESS\"}", demoId);
        log(qa,    EntityType.TASK,    t029.getId(),   ActionType.CREATED,        null, "{\"taskCode\":\"DEMO-029\",\"type\":\"BUG\",\"priority\":\"HIGH\"}", demoId);
        log(dev3,  EntityType.TASK,    t029.getId(),   ActionType.STATUS_CHANGED, "{\"status\":\"TODO\"}",        "{\"status\":\"IN_PROGRESS\"}", demoId);

        // ECOM project logs
        Project ecom = projectRepository.findByProjectKey("ECOM").orElse(null);
        if (ecom != null && !activityLogRepository.findByProjectId(ecom.getId(), Pageable.ofSize(1)).hasContent()) {
            UUID ecomId = ecom.getId();
            Task e016   = taskRepository.findByTaskCode("ECOM-016").orElse(null);
            log(pm,  EntityType.PROJECT, ecomId,       ActionType.CREATED, null, "{\"name\":\"E-Commerce Platform\",\"key\":\"ECOM\"}", ecomId);
            if (e016 != null) {
                log(qa, EntityType.TASK, e016.getId(), ActionType.CREATED, null, "{\"taskCode\":\"ECOM-016\",\"type\":\"BUG\",\"priority\":\"CRITICAL\"}", ecomId);
            }
        }
    }

    private void log(User actor, EntityType entityType, UUID entityId,
                     ActionType action, String oldValues, String newValues, UUID projectId) {
        activityLogRepository.save(ActivityLog.builder()
                .actor(actor).entityType(entityType).entityId(entityId)
                .action(action).oldValues(oldValues).newValues(newValues)
                .projectId(projectId).build());
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private User u(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
