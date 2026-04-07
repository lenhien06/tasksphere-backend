ALTER TABLE project_invites
    ADD COLUMN skill_tags JSON NULL
    COMMENT 'Skill override được PM nhập khi gửi invite; nếu null thì fallback về skill profile sau khi user accept';
