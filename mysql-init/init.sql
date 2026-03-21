-- ============================================================
-- Phase 4 Migration: project_versions + tasks.version_id
-- ============================================================

CREATE TABLE IF NOT EXISTS project_versions (
    id           CHAR(36)     NOT NULL PRIMARY KEY,
    project_id   CHAR(36)     NOT NULL,
    name         VARCHAR(100) NOT NULL
                              CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    description  VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    status       ENUM('PLANNING','IN_PROGRESS','RELEASED') NOT NULL DEFAULT 'PLANNING',
    release_date DATE,
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3),
    deleted_at   DATETIME(3),
    CONSTRAINT fk_pv_project  FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT uk_version_project_name UNIQUE KEY (project_id, name)
);

-- Add version_id column to tasks if not exists
ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS version_id CHAR(36) NULL,
    ADD CONSTRAINT fk_task_version FOREIGN KEY (version_id) REFERENCES project_versions(id);

-- ============================================================
-- Phase 6 Migration
-- ============================================================

-- P6-BE-02: Recurring Task — add status, frequency_config, parent_recurring_task_id
ALTER TABLE recurring_task_configs
    ADD COLUMN IF NOT EXISTS status ENUM('ACTIVE','PAUSED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN IF NOT EXISTS frequency_config TEXT;

ALTER TABLE tasks
    ADD COLUMN IF NOT EXISTS parent_recurring_task_id CHAR(36) NULL;

-- P6-BE-03: Webhook Delivery Logs
CREATE TABLE IF NOT EXISTS webhook_delivery_logs (
    id              CHAR(36)     NOT NULL PRIMARY KEY,
    webhook_id      CHAR(36)     NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    request_body    TEXT,
    response_status INT,
    response_body   TEXT,
    success         BOOLEAN      NOT NULL DEFAULT FALSE,
    attempt_number  INT          NOT NULL DEFAULT 1,
    delivered_at    DATETIME(3)  NOT NULL,
    INDEX idx_wdl_webhook   (webhook_id),
    INDEX idx_wdl_delivered (delivered_at),
    CONSTRAINT fk_wdl_webhook FOREIGN KEY (webhook_id) REFERENCES webhooks(id)
);
