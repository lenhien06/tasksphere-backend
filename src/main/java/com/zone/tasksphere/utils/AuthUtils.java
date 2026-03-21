package com.zone.tasksphere.utils;

import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.security.CustomUserDetail;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class AuthUtils {

  public static UserDetail getUserDetail() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null) {
      return null;
    }
    if (!(authentication.getPrincipal() instanceof CustomUserDetail customUserDetail)) {
      return null;
    }
    return customUserDetail.getUserDetail();
  }
}
