package com.zone.tasksphere.entity.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WorkspaceRole {
    OWNER("Owner", 3),
    ADMIN("Admin", 2),
    MEMBER("Member", 1);

    private final String displayName;
    private final int permissionLevel;

    public boolean canManageWorkspace() {
        return this.permissionLevel >= 2;
    }

    public boolean canInviteMembers() {
        return this.permissionLevel >= 2;
    }

    @JsonCreator
    public static WorkspaceRole fromString(String value) {
        if (value == null) return null;
        try {
            return WorkspaceRole.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @JsonValue
    public String toValue() {
        return name();
    }
}
