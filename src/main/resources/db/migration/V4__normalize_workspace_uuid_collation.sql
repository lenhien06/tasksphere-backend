-- ============================================================
-- V4__normalize_workspace_uuid_collation.sql
-- Fix MySQL collation mismatch on CHAR(36) UUID columns used by
-- workspace/user joins, especially:
--   workspace_members.user_id = users.id
-- ============================================================

-- Drop workspace-related foreign keys first so UUID columns can be altered safely.
SET @schema_name = DATABASE();

SET @drop_fk_workspaces_owner = (
    SELECT IFNULL(
        (
            SELECT CONCAT('ALTER TABLE workspaces DROP FOREIGN KEY `', CONSTRAINT_NAME, '`')
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'workspaces'
              AND COLUMN_NAME = 'owner_id'
              AND REFERENCED_TABLE_NAME = 'users'
            LIMIT 1
        ),
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_fk_workspaces_owner;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_fk_workspace_members_workspace = (
    SELECT IFNULL(
        (
            SELECT CONCAT('ALTER TABLE workspace_members DROP FOREIGN KEY `', CONSTRAINT_NAME, '`')
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'workspace_members'
              AND COLUMN_NAME = 'workspace_id'
              AND REFERENCED_TABLE_NAME = 'workspaces'
            LIMIT 1
        ),
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_fk_workspace_members_workspace;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_fk_workspace_members_user = (
    SELECT IFNULL(
        (
            SELECT CONCAT('ALTER TABLE workspace_members DROP FOREIGN KEY `', CONSTRAINT_NAME, '`')
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'workspace_members'
              AND COLUMN_NAME = 'user_id'
              AND REFERENCED_TABLE_NAME = 'users'
            LIMIT 1
        ),
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_fk_workspace_members_user;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_fk_workspace_members_invited_by = (
    SELECT IFNULL(
        (
            SELECT CONCAT('ALTER TABLE workspace_members DROP FOREIGN KEY `', CONSTRAINT_NAME, '`')
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'workspace_members'
              AND COLUMN_NAME = 'invited_by'
              AND REFERENCED_TABLE_NAME = 'users'
            LIMIT 1
        ),
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_fk_workspace_members_invited_by;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_fk_projects_workspace = (
    SELECT IFNULL(
        (
            SELECT CONCAT('ALTER TABLE projects DROP FOREIGN KEY `', CONSTRAINT_NAME, '`')
            FROM information_schema.KEY_COLUMN_USAGE
            WHERE TABLE_SCHEMA = @schema_name
              AND TABLE_NAME = 'projects'
              AND COLUMN_NAME = 'workspace_id'
              AND REFERENCED_TABLE_NAME = 'workspaces'
            LIMIT 1
        ),
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_fk_projects_workspace;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Normalize workspace-related tables and UUID columns to utf8mb4_unicode_ci.
ALTER TABLE users
    MODIFY COLUMN id CHAR(36)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

ALTER TABLE workspaces
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN id CHAR(36)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    MODIFY COLUMN owner_id CHAR(36)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

ALTER TABLE workspace_members
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN workspace_id CHAR(36)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    MODIFY COLUMN user_id CHAR(36)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    MODIFY COLUMN invited_by CHAR(36)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

ALTER TABLE projects
    MODIFY COLUMN workspace_id CHAR(36)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL;

-- Recreate foreign keys with explicit names.
ALTER TABLE workspaces
    ADD CONSTRAINT fk_workspaces_owner
        FOREIGN KEY (owner_id) REFERENCES users(id);

ALTER TABLE workspace_members
    ADD CONSTRAINT fk_workspace_members_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id),
    ADD CONSTRAINT fk_workspace_members_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    ADD CONSTRAINT fk_workspace_members_invited_by
        FOREIGN KEY (invited_by) REFERENCES users(id);

ALTER TABLE projects
    ADD CONSTRAINT fk_projects_workspace
        FOREIGN KEY (workspace_id) REFERENCES workspaces(id);
