package com.zone.tasksphere.exception;
import com.zone.tasksphere.utils.MessagesUtils;
import lombok.Setter;

@Setter
public class BadRequestException extends RuntimeException {

    private String message;

    public BadRequestException(String errorCode, Object... var2) {
        this.message = MessagesUtils.getMessage(errorCode, var2);
    }

    @Override
    public String getMessage() {
        return message;
    }

}
