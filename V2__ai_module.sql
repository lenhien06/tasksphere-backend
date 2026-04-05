-- =============================================================
-- V2__ai_module.sql  –  TaskSphere AI Module
-- Feature 1: AI Task Generation
-- Feature 2: AI Assignment Suggestions
-- =============================================================

-- ─────────────────────────────────────────────────────────────
-- Feature 1: skill requirements + AI origin flag on tasks
-- ─────────────────────────────────────────────────────────────
ALTER TABLE tasks
    ADD COLUMN skill_tags_required JSON         DEFAULT NULL    AFTER actual_hours,
    ADD COLUMN ai_generated        TINYINT(1)   NOT NULL DEFAULT 0 AFTER skill_tags_required;

-- ─────────────────────────────────────────────────────────────
-- Feature 2: user skill profile
-- ─────────────────────────────────────────────────────────────
ALTER TABLE users
    ADD COLUMN skill_tags JSON DEFAULT NULL AFTER avatar_url;

-- ─────────────────────────────────────────────────────────────
-- Feature 2: per-project workload + velocity tracking
-- ─────────────────────────────────────────────────────────────
ALTER TABLE project_members
    ADD COLUMN active_task_count SMALLINT     NOT NULL DEFAULT 0   AFTER invited_by,
    ADD COLUMN avg_story_points  DECIMAL(4,1)          DEFAULT NULL AFTER active_task_count;

-- ─────────────────────────────────────────────────────────────
-- Feature 2: AI assignment suggestions (audit trail – never deleted)
-- ─────────────────────────────────────────────────────────────
CREATE TABLE ai_task_assignments (
    id                CHAR(36)     NOT NULL DEFAULT (UUID()),
    task_id           CHAR(36)     NOT NULL,
    suggested_user_id CHAR(36)     NOT NULL,
    skill_score       DECIMAL(4,3) NOT NULL DEFAULT 0.000,
    workload_score    DECIMAL(4,3) NOT NULL DEFAULT 0.000,
    difficulty_score  DECIMAL(4,3) NOT NULL DEFAULT 0.000,
    total_score       DECIMAL(4,3) NOT NULL DEFAULT 0.000,
    reason_text       TEXT         NOT NULL,
    status            ENUM('PENDING','CONFIRMED','OVERRIDDEN') NOT NULL DEFAULT 'PENDING',
    confirmed_by      CHAR(36)                DEFAULT NULL,
    created_at        DATETIME(3)  NOT NULL   DEFAULT NOW(3),

    PRIMARY KEY (id),
    INDEX idx_ai_task_score (task_id, total_score DESC),

    CONSTRAINT fk_ai_assign_task
        FOREIGN KEY (task_id)           REFERENCES tasks(id),
    CONSTRAINT fk_ai_assign_suggested
        FOREIGN KEY (suggested_user_id) REFERENCES users(id),
    CONSTRAINT fk_ai_assign_confirmed
        FOREIGN KEY (confirmed_by)      REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
