package com.zone.tasksphere.exception;

import java.util.Collections;
import java.util.List;

public record ErrorDetail(String statusCode, String title, String detail, List<String> fieldErrors) {
    public ErrorDetail(String statusCode, String title, String detail) {
        this(statusCode, title, detail, Collections.emptyList());
    }
}

