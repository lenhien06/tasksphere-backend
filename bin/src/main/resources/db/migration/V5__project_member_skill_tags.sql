-- Add skill_tags column to project_members for project-scoped skill override
-- Priority for AI scoring: project_members.skill_tags > workspace_members.skill_tags > users.skill_tags
ALTER TABLE project_members
    ADD COLUMN IF NOT EXISTS skill_tags JSON DEFAULT NULL
    COMMENT 'PM-specified skills for this member in this project — overrides workspace/profile skills for AI scoring';
