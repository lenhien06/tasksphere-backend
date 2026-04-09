# TaskSphere — Project & Task Management Platform

<div align="center">

**Production API:** [https://api.tasksphere.io.vn](https://api.tasksphere.io.vn)  
**Swagger UI:** [https://api.tasksphere.io.vn/swagger-ui.html](https://api.tasksphere.io.vn/swagger-ui.html)  
**Frontend repo:** [tasksphere-frontend](https://github.com/lenhien06/tasksphere-frontend)

![Java](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=flat&logo=springboot)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat&logo=redis)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker)
![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub_Actions-2088FF?style=flat&logo=githubactions)
![License](https://img.shields.io/badge/license-MIT-green)

</div>

---

## Overview

TaskSphere is a project and task management platform built around the **Agile/Scrum** model, providing a comprehensive REST API for software teams. The system supports the entire project lifecycle — from sprint planning, task assignment, and real-time progress tracking to burndown and velocity reporting.

### Highlights

- **Production-grade security** — JWT with refresh token rotation, Redis blacklist, rate limiting (Bucket4j), and file upload virus scanning (ClamAV)
- **Real-time capabilities** — WebSocket/STOMP for notifications and instant Kanban board updates
- **Agile-native workflows** — Sprint management, customizable Kanban workflows, backlog handling, dependency tracking, and recurring tasks
- **Extensible design** — Project-level custom fields, outbound webhooks, and a system-wide activity log
- **Deployment-ready** — Dockerized with automated CI/CD to a VPS through GitHub Actions

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Client Layer                         │
│           Frontend (React)  /  Mobile  /  API Consumer   │
└────────────────────┬────────────────────────────────────┘
                     │ HTTPS / WSS
┌────────────────────▼────────────────────────────────────┐
│                  Spring Boot 3.5 (Java 21)               │
│                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  REST API    │  │  WebSocket   │  │  Scheduler    │  │
│  │ (39 ctrls)   │  │  /STOMP      │  │  (cron jobs)  │  │
│  └──────┬───────┘  └──────┬───────┘  └───────┬───────┘  │
│         │                 │                   │          │
│  ┌──────▼─────────────────▼───────────────────▼───────┐  │
│  │              Service / Domain Layer                  │  │
│  └──────┬──────────────────────────────────────────────┘  │
│         │                                                │
│  ┌──────▼──────┐  ┌──────────────┐  ┌────────────────┐  │
│  │  MySQL 8.0  │  │   Redis 7    │  │  MinIO (S3)    │  │
│  │  (JPA/HQL)  │  │  cache/BL    │  │  file storage  │  │
│  └─────────────┘  └──────────────┘  └────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## Features

### Authentication & Security
| Feature | Detail |
|---|---|
| JWT Authentication | Access token (1h) + refresh token (7 days), rotated on refresh |
| Token Blacklist | Logout revokes token instantly via Redis |
| RBAC | Role-based access control: ADMIN / PROJECT_MANAGER / MEMBER / VIEWER |
| Rate Limiting | Bucket4j — protects against brute-force attacks and API spam |
| Virus Scan | ClamAV scans every uploaded file before storage |
| XSS Protection | Jsoup sanitizes HTML comments, MIME-type detection via Apache Tika |
| OTP Flow | Email OTP for registration and password reset |

### Project & Task Management
| Feature | Detail |
|---|---|
| Multi-project | Multiple isolated projects, each with its own workflow |
| Kanban Board | Custom columns, drag-and-drop positioning, soft delete |
| Sprint Management | Create, start, and complete sprints, batch task assignment, burndown chart |
| Backlog | Manage tasks not yet assigned to a sprint, drag into sprint |
| Task Detail | Priority, label, assignee, due date, story points, estimate |
| Sub-tasks | Unlimited-depth task hierarchy |
| Checklist | Checklist items with reordering and progress tracking |
| Dependencies | `blocked-by` / `blocks` relationships between tasks |
| Recurrence | Create recurring tasks on daily, weekly, monthly, or custom schedules |
| Custom Fields | Text, number, date, select, multi-select — defined per project |
| Version / Release | Link tasks to release versions and track release progress |

### Collaboration
| Feature | Detail |
|---|---|
| Real-time Notifications | WebSocket push for assignments, comments, mentions, and due dates |
| Comments | Rich text, member `@mentions`, threaded replies |
| Activity Log | Full history of task and project changes |
| Member Invite | Invite by email (link + OTP), or add directly as admin |
| Worklog | Log working hours with sprint/member statistics |
| Daily Digest | Daily work summary email (cron job) |

### Reporting & Export
| Feature | Detail |
|---|---|
| Burndown Chart | Daily sprint burndown |
| Sprint Velocity | Compare velocity across sprints |
| Member Performance | Completed story points, task counts, and worklog |
| Export | Excel (Apache POI) and PDF (iText) sprint reports |

### Developer & Ops
| Feature | Detail |
|---|---|
| Swagger / OpenAPI | Full API documentation at `/swagger-ui.html` |
| Webhooks | Outbound webhook when a task changes status |
| Health Check | `/actuator/health` — monitoring integration |
| Soft Delete | System-wide `deleted_at` strategy to avoid data loss |
| Optimistic Locking | `@Version` on the Task entity to prevent race conditions |
| Docker Compose | Start the entire stack with a single command |
| CI/CD | GitHub Actions — build, validate, deploy to VPS over SSH, then health check |

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 21 |
| Framework | Spring Boot | 3.5.5 |
| Security | Spring Security + JJWT | 0.12.5 |
| Database | MySQL | 8.0 |
| ORM | Spring Data JPA / Hibernate | — |
| Cache | Redis (Lettuce pool) | 7 |
| Messaging | WebSocket + STOMP (SockJS) | — |
| File Storage | MinIO (S3-compatible) | — |
| Virus Scan | ClamAV (capybara client) | 2.1.2 |
| Rate Limiting | Bucket4j | 8.10.1 |
| API Docs | SpringDoc OpenAPI | 2.8.5 |
| Export | Apache POI + iText PDF | 5.2.5 / 5.5.13 |
| HTML Sanitizer | Jsoup | 1.17.2 |
| Build | Maven Wrapper | — |
| Container | Docker + Docker Compose | — |
| CI/CD | GitHub Actions | — |

---

## License

[MIT](LICENSE) © 2025 TaskSphere
