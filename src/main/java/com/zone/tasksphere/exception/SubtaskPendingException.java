package com.zone.tasksphere.exception;

import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * Thrown when a task cannot be moved to DONE because it has pending sub-tasks (BR-18).
 * Carries the list of pending subtasks so the API can return structured data.
 */
@Getter
public class SubtaskPendingException extends RuntimeException {

    private final List<Map<String, Object>> pendingSubtasks;

    public SubtaskPendingException(List<Map<String, Object>> pendingSubtasks) {
        super("Còn sub-task chưa hoàn thành");
        this.pendingSubtasks = pendingSubtasks;
    }
}
