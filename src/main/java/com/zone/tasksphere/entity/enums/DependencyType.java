package com.zone.tasksphere.entity.enums;

/**
 * FR-33: Dependency types between tasks (stored per-perspective).
 * When a BLOCKS link is created, the inverse BLOCKED_BY is auto-created.
 * When DUPLICATES is created, IS_DUPLICATED_BY is auto-created.
 */
public enum DependencyType {
    /** Source BLOCKS target — target cannot start until source is DONE */
    BLOCKS,
    /** Source is BLOCKED_BY target — auto-created inverse of BLOCKS */
    BLOCKED_BY,
    /** Informational relation only — no blocking semantics */
    RELATES_TO,
    /** Source is a duplicate of target — auto-creates inverse IS_DUPLICATED_BY */
    DUPLICATES,
    /** Inverse of DUPLICATES — created automatically */
    IS_DUPLICATED_BY
}
