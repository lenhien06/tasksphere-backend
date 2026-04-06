-- ============================================================
-- V3__workspace.sql — Workspace module (Phase 2)
-- NOTE: Hibernate ddl-auto=update handles schema automatically.
--       This file is kept for documentation / manual migration.
-- ============================================================

-- 1. Bảng workspaces
CREATE TABLE IF NOT EXISTS workspaces (
    id              CHAR(36)        NOT NULL DEFAULT (UUID()),
    name            VARCHAR(255)    NOT NULL,
    slug            VARCHAR(50)     NOT NULL UNIQUE
        COMMENT 'URL-friendly, 2-50 ký tự, chỉ a-z/0-9/dấu gạch ngang',
    description     TEXT            DEFAULT NULL,
    avatar_url      VARCHAR(500)    DEFAULT NULL,
    owner_id        CHAR(36)        NOT NULL,
    plan            ENUM('FREE','PRO','ENTERPRISE') NOT NULL DEFAULT 'FREE',
    created_at      DATETIME(3)     NOT NULL DEFAULT NOW(3),
    updated_at      DATETIME(3)     NOT NULL DEFAULT NOW(3) ON UPDATE NOW(3),
    deleted_at      DATETIME(3)     DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_ws_owner (owner_id),
    INDEX idx_ws_slug  (slug),
    FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Bảng workspace_members
CREATE TABLE IF NOT EXISTS workspace_members (
    workspace_id        CHAR(36)        NOT NULL,
    user_id             CHAR(36)        NOT NULL,
    role                ENUM('OWNER','ADMIN','MEMBER') NOT NULL DEFAULT 'MEMBER',
    skill_tags          JSON            DEFAULT NULL
        COMMENT '["React","TypeScript"] — dùng cho AI scoring',
    active_task_count   SMALLINT        NOT NULL DEFAULT 0,
    avg_story_points    DECIMAL(4,1)    DEFAULT NULL,
    joined_at           DATETIME(3)     NOT NULL DEFAULT NOW(3),
    invited_by          CHAR(36)        DEFAULT NULL,
    PRIMARY KEY (workspace_id, user_id),
    INDEX idx_wm_user (user_id),
    FOREIGN KEY (workspace_id) REFERENCES workspaces(id),
    FOREIGN KEY (user_id)      REFERENCES users(id),
    FOREIGN KEY (invited_by)   REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Thêm workspace_id vào projects (nullable — backward compatible)
ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS workspace_id CHAR(36) DEFAULT NULL
        COMMENT 'NULL = standalone project; có giá trị = thuộc Workspace',
    ADD INDEX IF NOT EXISTS idx_proj_workspace (workspace_id),
    ADD CONSTRAINT fk_proj_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces(id);
