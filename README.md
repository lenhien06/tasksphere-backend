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

---

## License

MIT