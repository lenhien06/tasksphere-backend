# TaskSphere Backend — Hướng dẫn cho Team
docker compose up -d db redis minio clamav
```
### Bước 1 — Clone & cấu hình env
### Bước 2 — Khởi động Database & Redis

```bash
# Chạy chỉ db + redis (không build app)
docker compose up db redis -d

# Kiểm tra đang chạy
docker compose ps

# Xem log nếu có lỗi
docker compose logs db
docker compose logs redis
```

Sau lệnh này:
- MySQL chạy tại `localhost:3307`, database `task_sphere_db`
- Redis chạy tại `localhost:6379`

### Bước 3 — Chạy Spring Boot

**Cách 1 — Terminal (mvnw):**
```bash
./mvnw spring-boot:run
```
*Windows:*
```bash
mvnw.cmd spring-boot:run
```

**Cách 2 — IntelliJ IDEA:**
- Mở project → Run `TaskSphereApplication.java`
- Active profile mặc định là `dev`

**Cách 3 — Build JAR rồi chạy:**
```bash
./mvnw clean package -DskipTests
java -jar target/tasksphere-*.jar
```

App chạy tại: `http://localhost:8080`

---

## Tài khoản mặc định (tự động seed khi khởi động)

| Email | Mật khẩu | Role |
|---|---|---|
| `admin@tasksphere.local` | `Admin@123456` | ADMIN — **đổi mật khẩu ngay** |
| `pm@tasksphere.local` | `Demo@123456` | USER / Project PM |
| `dev1@tasksphere.local` | `Demo@123456` | USER / Member |
| `dev2@tasksphere.local` | `Demo@123456` | USER / Member |
| `viewer@tasksphere.local` | `Demo@123456` | USER / Viewer |

Demo project key: `DEMO` — gồm 10 tasks, 2 sprints, kanban columns.

---

## API Documentation

Swagger UI: `http://localhost:8080/swagger-ui.html`

### Auth endpoints

| Method | URL | Mô tả |
|---|---|---|
| POST | `/api/auth/signup` | Đăng ký tài khoản |
| POST | `/api/auth/signin` | Đăng nhập (email + password) |
| POST | `/api/auth/refresh` | Làm mới access token |
| POST | `/api/auth/logout` | Đăng xuất |
| GET  | `/api/auth/me` | Thông tin user hiện tại |

### Auth flow

```
1. Gửi OTP  → POST /api/auth/send-otp   { email, purpose: "REGISTER" }
2. Đăng ký  → POST /api/auth/signup     { fullName, email, password, confirmPassword, otp }
3. Response → { accessToken, refreshToken, expiresIn: 3600, tokenType: "Bearer", user }
4. Gọi API  → Header: Authorization: Bearer <accessToken>
5. Hết hạn  → POST /api/auth/refresh    { refreshToken }
```

---

## Cấu hình cần thiết

### Dev (application-dev.yml) — đã có default, không cần chỉnh
- MySQL: `localhost:3307` / `root` / `123456zoneteam`
- Redis: `localhost:6379`
- JWT secret: có sẵn (chỉ dùng cho dev)
- CORS: `localhost:3000`, `localhost:5173`, `localhost:8080`
- Turnstile: dùng test key (bypass captcha)

### Cần cấu hình thêm để dùng đầy đủ
| Tính năng | Biến env | Lấy ở đâu |
|---|---|---|
| Gửi email | `MAIL_USERNAME`, `MAIL_PASSWORD` | Gmail → App Passwords |
| Captcha | `TURNSTILE_SECRET_KEY` | Cloudflare Dashboard |
| File upload | `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | AWS Console |

Cách set env khi chạy local:
```bash
# Linux/Mac
export MAIL_USERNAME=your@gmail.com
export MAIL_PASSWORD=xxxx
./mvnw spring-boot:run

# Windows PowerShell
$env:MAIL_USERNAME="your@gmail.com"
$env:MAIL_PASSWORD="xxxx"
.\mvnw.cmd spring-boot:run
```

---

## Chạy toàn bộ bằng Docker (prod-like)

```bash
# Điền đầy đủ .env trước (xem .env.example)
# Bắt buộc: JWT_SECRET, DB_PASSWORD, MAIL_USERNAME, MAIL_PASSWORD

docker compose up -d

# Theo dõi log app
docker compose logs -f app
```

Dừng tất cả:
```bash
docker compose down

# Xóa cả data volumes (reset database)
docker compose down -v
```

---

## Database

- **ddl-auto = `update`** ở dev/prod mặc định → Hibernate tự tạo/cập nhật bảng khi start
- Đổi sang `validate` khi schema đã ổn định (production)
- Soft delete: entity có `deleted_at` dùng `@SQLRestriction("deleted_at IS NULL")`
- Primary key: **UUID** cho tất cả domain entities
- Optimistic locking: `@Version` trên `Task` entity

### Reset database

```bash
# Xóa toàn bộ data và tạo lại
docker compose down -v
docker compose up db redis -d
# Restart app → DataSeeder sẽ seed lại
```

---

## Cấu trúc JWT

```json
{
  "sub":    "user@email.com",
  "userId": "uuid-string",
  "role":   "USER",
  "status": "ACTIVE",
  "iat":    1700000000,
  "exp":    1700003600,
  "jti":    "unique-id"
}
```

- Access token TTL: **1 giờ**
- Refresh token TTL: **7 ngày**
- Logout → Access token bị blacklist trong Redis (key: `revoked_token:{token}`)

---

## Lưu ý quan trọng

- File `.env` đã có trong `.gitignore` — **không commit secrets**
- Commit `.env.example` thay thế khi thêm biến env mới
- DataSeeder chạy tự động mỗi lần start, **idempotent** (an toàn khi chạy nhiều lần)
- Profile `test` sẽ **không** chạy DataSeeder (`@Profile("!test")`)
- Xem Swagger để test API không cần Postman: `http://localhost:8080/swagger-ui.html`
