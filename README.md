<div align="center">

# TaskSphere Backend

**Agile project management platform — REST API & real-time server**

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue?logo=mysql)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7-red?logo=redis)](https://redis.io/)
[![MinIO](https://img.shields.io/badge/MinIO-S3--compatible-orange?logo=minio)](https://min.io/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue?logo=docker)](https://docs.docker.com/compose/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[Live API](https://api.tasksphere.io.vn) · [Swagger UI](https://api.tasksphere.io.vn/swagger-ui.html) · [Frontend Repo](https://github.com/lenhien06/tasksphere-frontend)

</div>

---

## Overview

TaskSphere is a full-featured project management platform built for software teams following Agile/Scrum methodology. This repository contains the Spring Boot backend that powers the REST API, WebSocket real-time layer, AI features, and background job processing.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.5 |
| Database | MySQL 8.0 (JPA / Hibernate) |
| Cache | Redis 7 (Lettuce, connection pool) |
| Object Storage | MinIO (AWS S3-compatible SDK) |
| Real-time | WebSocket — STOMP over SockJS |
| Auth | JWT (HS512) + Google OAuth2 + Email OTP |
| AI | Google Gemini API + Groq (Llama 3.3) |
| Email | SendGrid (Thymeleaf templates) |
| Security | Bucket4j rate limiting · ClamAV virus scan · Jsoup XSS sanitizer · Apache Tika MIME detection |
| Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven 3.9 · multi-stage Docker image |

---

## Features

### Workspace & Project Management
- Multi-workspace support with member roles and permissions
- Projects with visibility settings (public / private), status, and version tracking
- Customisable Kanban columns per project
- Project invite flow with email notifications

### Task System
- Task types: **Story**, **Task**, **Bug**
- Priorities: **Critical**, **High**, **Medium**, **Low**
- Statuses: **To Do → In Progress → In Review → Testing → Done / Cancelled**
- Hierarchical tasks (parent / sub-tasks)
- Task dependencies (blocks / blocked-by)
- Checklists, rich-text descriptions, @mention comments
- Worklogs with time tracking
- File attachments (uploaded to MinIO, virus-scanned by ClamAV)
- Custom fields (text, number, date, dropdown, checkbox)
- Saved filters per project
- Recurring tasks (daily / weekly / monthly / yearly)
- Calendar view (tasks by due date)

### Sprint Management
- Full sprint lifecycle: create → start → complete → auto-close
- Backlog management with batch sprint assignment
- Burndown chart, burnup chart, velocity report, velocity forecast

### Reporting & Export
- Member performance report (tasks completed, story points, worklog hours)
- Project overview report
- Export to **PDF** (iText) and **Excel** (Apache POI)
- Async export jobs with download link

### AI Module
- **AI Task Generator** — describe a feature in natural language; Gemini generates a structured backlog (title, type, priority, story points, skill tags, acceptance criteria)
- **AI Project Planner** — multi-turn conversation that collects requirements and produces a complete sprint plan
- **Smart Assignment** — scoring engine ranks team members per task based on skill tags, workload, and past performance; Groq generates a natural-language explanation for each suggestion
- **Burnout Detector** — sliding-window algorithm analyses commit/task patterns and sends AI-generated alerts to Slack

### Developer Experience
- Real-time notifications via WebSocket (STOMP)
- Outgoing webhooks on task / project events (with delivery log and test endpoint)
- Activity log on every entity change
- Optimistic locking (ETag) on task updates
- Cloudflare Turnstile CAPTCHA integration
- SpringDoc Swagger UI at `/swagger-ui.html`
- Spring Boot Actuator health + Prometheus metrics at `/actuator`

---

## Project Structure

```
src/main/java/com/zone/tasksphere/
├── ai/                     # AI module (task generation, assignment, burnout)
│   ├── controller/
│   ├── service/            # AiService, WorkspaceAiService, ScoringEngine
│   └── entity/
├── component/              # Schedulers, WebSocket interceptors, migration runners
├── config/                 # Security, Redis, WebSocket, MinIO, CORS configs
├── controller/             # REST controllers (37 controllers)
├── dto/                    # Request / Response DTOs
├── entity/                 # JPA entities + enums
├── exception/              # Global exception handler
├── repository/             # Spring Data JPA repositories
├── service/                # Business logic interfaces + implementations
└── utils/                  # JWT, Auth, Filter, Cookie utilities
```

---

## Getting Started

### Prerequisites

- Docker 24+ and Docker Compose plugin
- Java 21 and Maven 3.9 (for local development without Docker)

### Environment Variables

Create `.env` in the project root (use `.env.example` as a template):

```env
GEMINI_API_KEY=your_google_gemini_api_key
SENDGRID_API_KEY=your_sendgrid_api_key
GOOGLE_CLIENT_ID=your_google_oauth_client_id
TURNSTILE_ENABLED=true
TURNSTILE_SECRET_KEY=your_cloudflare_turnstile_secret_key
GROQ_API_KEY=your_groq_api_key
GROQ_MODEL=llama-3.3-70b-versatile
```

### Run with Docker Compose

```bash
git clone https://github.com/lenhien06/tasksphere-backend.git
cd tasksphere-backend
cp .env.example .env   # fill in your keys
docker compose up -d --build
```

Services started:

| Container | Port |
|---|---|
| Spring Boot app | 8080 |
| MySQL 8.0 | 3308 |
| Redis 7 | 6380 |
| MinIO | 9000 (API) · 9001 (Console) |

Health check: `curl http://localhost:8080/actuator/health`

### Run Locally (without Docker)

```bash
# Start MySQL, Redis, MinIO via Docker
docker compose up -d db redis minio

# Run the Spring Boot application
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## API Documentation

Swagger UI is available at:

```
http://localhost:8080/swagger-ui.html
```

Production: [https://api.tasksphere.io.vn/swagger-ui.html](https://api.tasksphere.io.vn/swagger-ui.html)

### Key Endpoints

| Group | Base Path |
|---|---|
| Auth | `/api/auth/**` |
| Workspaces | `/api/workspaces/**` |
| Projects | `/api/projects/**` |
| Tasks | `/api/projects/{id}/tasks/**` |
| Sprints | `/api/projects/{id}/sprints/**` |
| Columns | `/api/projects/{id}/columns/**` |
| Comments | `/api/projects/{id}/tasks/{tid}/comments/**` |
| Attachments | `/api/projects/{id}/tasks/{tid}/attachments/**` |
| Reports | `/api/projects/{id}/reports/**` |
| Export | `/api/projects/{id}/export/**` |
| Webhooks | `/api/projects/{id}/webhooks/**` |
| AI | `/api/ai/**` · `/api/workspace-ai/**` |
| Notifications | `/api/notifications/**` |
| Dashboard | `/api/dashboard/**` |

---

## Configuration

Key files:

| File | Purpose |
|---|---|
| `src/main/resources/application.yml` | Shared defaults |
| `src/main/resources/application-dev.yml` | Local development overrides |
| `src/main/resources/application-prod.yml` | Production settings |
| `docker-compose.yml` | All services (DB, Redis, MinIO, app) |
| `Dockerfile` | Multi-stage build (Maven → JRE 21 Alpine) |

Production JVM flags: `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0` (respects Docker memory limits). Container memory limit: 700 MB.

---

## Deployment

See [`docs/SERVER_MIGRATION.md`](docs/SERVER_MIGRATION.md) for the full step-by-step guide to set up a fresh Ubuntu 22.04 server with Nginx, SSL (Let's Encrypt), and Docker.

Quick deploy on an existing server:

```bash
# Deploy backend only
bash /root/deploy-backend.sh

# Deploy both backend and frontend
bash /root/deploy.sh
```

The deploy script pulls the latest code from `main`, builds a new Docker image, runs a health check, and automatically rolls back to the previous image if the health check fails.

---

## License

[MIT](LICENSE)
