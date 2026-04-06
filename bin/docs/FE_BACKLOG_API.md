# FE Backlog API

Tai lieu tong hop endpoint lien quan Backlog/Sprint cho FE.

## 1) Muc tieu

Backlog tab can:
- Lay danh sach task chua gan sprint
- Gan 1 task vao sprint / bo sprint (ve backlog)
- Gan nhieu task vao sprint (batch)
- Lay danh sach sprint de hien thi dropdown

## 2) Auth + wrapper

- Auth: `Authorization: Bearer <access_token>`
- Tat ca response su dung wrapper:

```json
{
  "data": {},
  "meta": {
    "error": null,
    "message": "Success",
    "version": "v1",
    "timestamp": "2026-03-24T00:00:00Z"
  }
}
```

---

## 3) Danh sach endpoint cho Backlog

### 3.1 Lay backlog tasks

`GET /api/v1/projects/{projectId}/backlog`

Query:
- `q` (optional): tim theo title/taskCode
- `priority` (optional): `CRITICAL|HIGH|MEDIUM|LOW`
- `assigneeId` (optional): UUID hoac `me`
- `type` (optional): `TASK|BUG|FEATURE|STORY|EPIC|SUB_TASK`
- `page` (default `0`)
- `size` (default `20`)
- `sort` (vd: `createdAt,desc`)

Response `data`: `PageResponse<TaskResponse>`

`TaskResponse` field quan trong cho FE backlog:
- `id`, `taskCode`, `title`
- `type`, `priority`, `taskStatus`
- `sprintId` (luon `null` trong backlog)
- `sprintName` (luon `null` trong backlog)
- `assignee`, `reporter`
- `dueDate`, `storyPoints`
- `commentsCount`, `attachmentsCount`

---

### 3.2 Lay danh sach sprint de gan task

`GET /api/v1/projects/{projectId}/sprints`

Response `data`: `SprintSummaryResponse[]`

FE dung endpoint nay de render dropdown:
- Chon sprint de assign task
- Loc sprint `PLANNED/ACTIVE` (khong cho chon `COMPLETED` o UI)

---

### 3.3 Gan 1 task vao sprint / bo sprint

`PATCH /api/v1/tasks/{taskId}/sprint`

Body:

```json
{
  "sprintId": "SPRINT_UUID_OR_NULL"
}
```

Rule:
- `sprintId = null` => chuyen task ve Backlog
- Sprint `COMPLETED` => loi `422`
- Task type `EPIC` + `sprintId != null` => loi `422` (BR-17)
- Permission: PM (theo service `requirePM`)

Response `data`: `TaskResponse`

---

### 3.4 Gan nhieu task vao sprint (batch)

`PATCH /api/v1/projects/{projectId}/tasks/batch-sprint`

Body:

```json
{
  "taskIds": [
    "TASK_UUID_1",
    "TASK_UUID_2"
  ],
  "sprintId": "SPRINT_UUID_OR_NULL"
}
```

Rule:
- `sprintId = null` => batch move ve Backlog
- `taskIds` bat buoc khong rong
- Sprint `COMPLETED` => loi `422`
- Permission: PM

Response `data`:

```json
{
  "updatedCount": 2,
  "failedIds": [],
  "message": "Da gan 2 task vao Sprint 12"
}
```

---

## 4) Endpoint lien quan can biet (khong phai backlog endpoint rieng)

### 4.1 Task list tong quan (kanban/list)

`GET /api/v1/projects/{projectId}/tasks`

Co the loc theo `sprintId` (UUID), nhung de lay backlog thi nen uu tien endpoint backlog rieng `.../backlog`.

> Luu y: docs Swagger cu co nhac `sprintId=backlog`, nhung param hien tai la UUID. FE khong nen gui string `backlog` vao endpoint nay.

---

## 5) Error codes FE can handle

- `400`: request body/query sai format
- `401`: chua dang nhap / token het han
- `403`: khong du quyen (thuong gap voi MEMBER/VIEWER khi gan sprint)
- `404`: task/sprint/project khong ton tai
- `422`: vi pham business rule
  - sprint da `COMPLETED`
  - EPIC khong duoc vao sprint (BR-17)

---

## 6) FE workflow de code nhanh

### A. Load Backlog tab
1. Goi `GET /projects/{projectId}/backlog?page=0&size=20`
2. Render table/card tu `data.content`
3. Dung `data.totalElements` cho badge "Backlog count"

### B. Assign 1 task
1. User chon sprint trong dropdown
2. Goi `PATCH /tasks/{taskId}/sprint` voi `sprintId`
3. Success:
   - remove task khoi danh sach backlog local
   - refetch backlog page hien tai
   - (optional) invalidate query board sprint

### C. Move back to backlog
1. Goi `PATCH /tasks/{taskId}/sprint` voi body `{ "sprintId": null }`
2. Success: task xuat hien lai trong backlog

### D. Batch assign
1. Gom `selectedTaskIds`
2. Goi `PATCH /projects/{projectId}/tasks/batch-sprint`
3. Success:
   - toast theo `data.message`
   - clear selection
   - refetch backlog

---

## 7) TypeScript goi y

```ts
export type AssignSprintRequest = {
  sprintId: string | null;
};

export type BatchAssignSprintRequest = {
  taskIds: string[];
  sprintId: string | null;
};

export type BatchSprintResponse = {
  updatedCount: number;
  failedIds: string[];
  message: string;
};
```

---

## 8) Quick test commands (manual)

```bash
# backlog
GET /api/v1/projects/{projectId}/backlog?page=0&size=20

# assign 1 task
PATCH /api/v1/tasks/{taskId}/sprint
{ "sprintId": "..." }

# move 1 task to backlog
PATCH /api/v1/tasks/{taskId}/sprint
{ "sprintId": null }

# batch assign
PATCH /api/v1/projects/{projectId}/tasks/batch-sprint
{ "taskIds": ["..."], "sprintId": "..." }
```
