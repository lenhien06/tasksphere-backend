package com.zone.tasksphere.exception;

import com.zone.tasksphere.utils.MessagesUtils;

public class Forbidden extends RuntimeException {

  private String message;

  public Forbidden(String errorCode, Object... var2) {
    this.message = MessagesUtils.getMessage(errorCode, var2);
  }

  public Forbidden() {
    this.message = MessagesUtils.getMessage("FORBIDDEN");
  }

  @Override
  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}

