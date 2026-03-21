package com.zone.tasksphere.exception;
import com.zone.tasksphere.utils.MessagesUtils;
import lombok.Setter;

@Setter
public class ConflictException extends RuntimeException {

    private String message;

    public ConflictException(String errorCode, Object... var2) {
        this.message = MessagesUtils.getMessage(errorCode, var2);
    }

    /** Constructor dùng trực tiếp message (không qua i18n key) */
    public ConflictException() {}

    @Override
    public String getMessage() {
        return message;
    }

}