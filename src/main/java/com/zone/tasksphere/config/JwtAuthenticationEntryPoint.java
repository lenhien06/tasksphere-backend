package com.zone.tasksphere.config;

import com.zone.tasksphere.exception.ErrorDetail;
import com.zone.tasksphere.utils.CookieUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final CookieUtils cookieUtils;

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException, ServletException {
    
    // Clear cookies on unauthorized access
    cookieUtils.deleteAuthCookies(response);

    ErrorDetail errorVM = new ErrorDetail(HttpStatus.UNAUTHORIZED.toString(), "Invalid JWT Token",
        authException.getMessage());
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write(objectMapper.writeValueAsString(errorVM));

  }
}
