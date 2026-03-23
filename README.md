# TaskSphere Backend — Project & Task Management API

REST API cho hệ thống quản lý dự án và công việc theo mô hình Agile/Scrum.

**Frontend:** [tasksphere-frontend](https://github.com/lenhien06/tasksphere-frontend)

---

## Tính năng

- Xác thực JWT với refresh token rotation
- Quản lý dự án, thành viên, phân quyền RBAC
- Kanban Board: task, sprint, custom workflow
- Realtime thông báo qua WebSocket/STOMP
- Upload file với virus scan (ClamAV)
- Báo cáo: burndown chart, sprint velocity, member performance
- Cache Redis, activity log, soft delete toàn hệ thống

---

## Công nghệ

- **Framework:** Java 17, Spring Boot 3
- **Database:** MySQL 8.0
- **Cache:** Redis 7
- **Messaging:** WebSocket + STOMP (SockJS)
- **Storage:** S3-compatible (MinIO)
- **Auth:** JWT (access token 1h, refresh token 7 ngày)

---

## Chạy local

### Yêu cầu
- Java 17+
- Docker (MySQL + Redis)

### Bước 1 — Khởi động database

```bash
docker-compose up -d
```

### Bước 2 — Cấu hình môi trường

```bash
cp src/main/resources/application-example.properties \
   src/main/resources/application-local.properties
# Sửa DB_URL, DB_USER, DB_PASS, REDIS_HOST
```

### Bước 3 — Chạy

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# API: http://localhost:8080/api/v1
# Swagger: http://localhost:8080/swagger-ui.html
```

### Tài liệu cho FE (Member & Invite)

- **[docs/FE_MEMBER_INVITE_API.md](docs/FE_MEMBER_INVITE_API.md)** — đặc tả từng endpoint, body, response, mã lỗi, enum.

### Tài liệu cho FE (Task)

- **[docs/FE_TASK_API.md](docs/FE_TASK_API.md)** — CRUD task, Kanban, sub-task, checklist, comment, attachment, worklog, dependency, sprint/backlog, recurrence, version; custom field có bản tóm tắt + link chi tiết.

### Tài liệu cho FE (Comment / @mention / Rich text)

- **[docs/FE_COMMENT_API.md](docs/FE_COMMENT_API.md)** — autocomplete member search, format HTML mention, sanitize & thẻ rich text, notification.

### Tài liệu cho FE (Custom fields)

- **[docs/FE_CUSTOM_FIELD_API.md](docs/FE_CUSTOM_FIELD_API.md)** — định nghĩa field theo project, giá trị trên task, enum kiểu, validation, quyền, DELETE (HIDDEN vs DELETED).
- **[docs/DB_CUSTOM_FIELDS_VERIFY.md](docs/DB_CUSTOM_FIELDS_VERIFY.md)** — SQL kiểm tra `hasValues` đúng schema, dọn hàng value rỗng, unhide field.

### Quyết định PM (Task API)

- **[docs/PM_TASK_API_DECISIONS.md](docs/PM_TASK_API_DECISIONS.md)** — FEATURE vs SRS, story points, BR-14 (trạng thái đã áp dụng trên BE ghi trong file).

---

## License

MIT