package com.zone.tasksphere.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Collections;
import java.util.Map;

/**
 * Lỗi API có mã ổn định + HTTP status, dùng cho Member/Invite và các luồng cần FE parse.
 */
@Getter
public class StructuredApiException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;
    private final Map<String, Object> meta;

    public StructuredApiException(HttpStatus status, String errorCode, String message) {
        this(status, errorCode, message, null);
    }

    public StructuredApiException(HttpStatus status, String errorCode, String message, Map<String, Object> meta) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.meta = meta == null || meta.isEmpty() ? null : Collections.unmodifiableMap(meta);
    }
}
