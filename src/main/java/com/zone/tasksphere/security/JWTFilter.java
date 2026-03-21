package com.zone.tasksphere.security;

import com.zone.tasksphere.utils.CookieUtils;
import com.zone.tasksphere.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private final JwtUtils jwtUtils;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
    String token = jwtUtils.extractBearerToken(bearerToken);

    // If token not found in header, try cookies
    if (token == null && request.getCookies() != null) {
      for (Cookie cookie : request.getCookies()) {
        if (CookieUtils.ACCESS_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
          token = cookie.getValue();
          break;
        }
      }
    }

    if (token != null && jwtUtils.validateToken(token)) {
      Authentication authentication = jwtUtils.setAuthentication(token);
      SecurityContextHolder.getContext().setAuthentication(authentication);
    }
    filterChain.doFilter(request, response);

  }
}

