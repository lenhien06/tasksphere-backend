# FE Activity API

Tai lieu dac ta cho FE tab `Hoat dong` trong Task Detail.

## 1) Auth va response wrapper

- Auth: `Authorization: Bearer <access_token>`
- Tat ca response theo wrapper:

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

## 2) Endpoint chinh cho Task Detail

### `GET /api/v1/projects/{projectId}/tasks/{taskId}/activity`

Lay timeline hoat dong theo task, backend da filter san:
- `entityType = TASK` cua chinh `taskId`
- `entityType = COMMENT` thuoc task do
- `entityType = ATTACHMENT` thuoc task do

Query params:
- `page` (optional, default `0`)
- `size` (optional, default `20`)
- `sort` (optional; backend hien tai luon sap xep moi nhat truoc)

Response `data` la `PageResponse<TaskActivityItemResponse>`:

```json
{
  "content": [
    {
      "id": "a4e6b9fe-0a7f-4cc1-8f0d-8e7e3f0f20a1",
      "action": "STATUS_CHANGED",
      "entityType": "TASK",
      "entityId": "43053f24-2c26-4751-9cea-bf0ff6317a31",
      "actor": {
        "id": "d11b0120-9cb3-4f4f-a8c0-6d9fdf31c2d2",
        "fullName": "Nhien Le",
        "avatarUrl": null
      },
      "oldValue": "{\"status\":\"TODO\"}",
      "newValue": "{\"status\":\"IN_PROGRESS\"}",
      "createdAt": "2026-03-24T10:00:00Z"
    }
  ],
  "totalElements": 10,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  "first": true,
  "last": true,
  "empty": false
}
```

## 3) Endpoint project activity (dashboard/admin)

### `GET /api/v1/projects/{projectId}/activities`

Query filter ho tro:
- `actorId` (UUID)
- `type` (`PROJECT|TASK|SPRINT|COMMENT|ATTACHMENT|MEMBER|USER`)
- `action` (ActionType)
- `from`, `to` (ISO datetime)
- `page`, `size`, `sort`

Dung cho man hinh overview; khong toi uu cho tab Activity trong Task Detail.

## 4) Danh sach action FE can map

- `TASK_CREATED`
- `STATUS_CHANGED`
- `ASSIGNEE_CHANGED`
- `PRIORITY_CHANGED`
- `UPDATED`
- `COMMENT_ADDED`
- `COMMENT_DELETED`
- `ATTACHMENT_UPLOADED`
- `ATTACHMENT_DELETED`
- `SUBTASK_CREATED`
- `SUBTASK_DELETED`
- `SPRINT_CHANGED`
- `POSITION_CHANGED` (keo-tha task trong board)

Goi y text hien thi:
- `TASK_CREATED` -> created the Work item
- `STATUS_CHANGED` -> changed the Status: {old} -> {new}
- `ASSIGNEE_CHANGED` -> changed the Assignee: {old} -> {new}
- `PRIORITY_CHANGED` -> changed the Priority: {old} -> {new}
- `UPDATED` -> updated the task
- `COMMENT_ADDED` -> added a Comment
- `COMMENT_DELETED` -> deleted a Comment
- `ATTACHMENT_UPLOADED` -> uploaded an attachment: {fileName}
- `ATTACHMENT_DELETED` -> deleted an attachment: {fileName}
- `SUBTASK_CREATED` -> created sub-task: {title}
- `SUBTASK_DELETED` -> deleted sub-task: {title}
- `SPRINT_CHANGED` -> changed the Sprint: {old} -> {new}
- `POSITION_CHANGED` -> moved task in board

## 5) Integration note cho FE

- **Khong double prefix**: khong goi `/api/api/v1/...`.
- Neu axios `baseURL = http://localhost:8080/api`, path phai la `/v1/projects/...`.
- Du lieu list nam o `response.data.content` (vi co wrapper `ApiResponse`).
- `oldValue/newValue` la string, co the la JSON string -> parse bang `try/catch`.
- Neu timeline rong, check Network tab de chac chan FE da goi endpoint activity.
