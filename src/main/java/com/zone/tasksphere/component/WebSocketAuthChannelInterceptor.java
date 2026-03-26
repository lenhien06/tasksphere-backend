package com.zone.tasksphere.component;

import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.security.CustomUserDetail;
import com.zone.tasksphere.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern USER_DEST_WITH_ID = Pattern.compile("^/user/([^/]+)/.*$");

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final ProjectMemberRepository projectMemberRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnect(accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            validateSubscription(accessor);
        }
        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        if (accessor.getUser() != null && StringUtils.hasText(accessor.getUser().getName())) {
            return;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new MessagingException("Unauthorized: missing token");
        }

        String token = authHeader.substring(7);
        try {
            if (!jwtUtils.validateToken(token)) {
                throw new MessagingException("Unauthorized: invalid token");
            }

            String email = jwtUtils.extractUsername(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            if (!(userDetails instanceof CustomUserDetail customUserDetail)) {
                throw new MessagingException("Unauthorized: invalid principal");
            }

            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                    customUserDetail.getUserDetail().getId().toString(),
                    null,
                    userDetails.getAuthorities()
                );
            auth.setDetails(customUserDetail.getUserDetail());
            accessor.setUser(auth);
            log.debug("[WS] Authenticated STOMP CONNECT userId={}", customUserDetail.getUserDetail().getId());
        } catch (MessagingException ex) {
            throw ex;
        } catch (Exception e) {
            log.warn("[WS] Invalid token on CONNECT: {}", e.getMessage());
            throw new MessagingException("Unauthorized: invalid token");
        }
    }

    private void validateSubscription(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null || !StringUtils.hasText(accessor.getUser().getName())) {
            throw new MessagingException("Unauthorized: missing authenticated user");
        }

        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            throw new MessagingException("Forbidden: missing destination");
        }

        String currentUserId = accessor.getUser().getName();

        if (destination.startsWith("/user/")) {
            Matcher matcher = USER_DEST_WITH_ID.matcher(destination);
            if (matcher.matches()) {
                String destinationUser = matcher.group(1);
                if (!"queue".equals(destinationUser) && !currentUserId.equals(destinationUser)) {
                    throw new MessagingException("Forbidden: cannot subscribe to another user's destination");
                }
            }
            return;
        }

        if (destination.startsWith("/topic/project/")) {
            String projectIdRaw = destination.substring("/topic/project/".length());
            try {
                UUID projectId = UUID.fromString(projectIdRaw);
                UUID userId = UUID.fromString(currentUserId);
                if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
                    throw new MessagingException("Forbidden: not a project member");
                }
            } catch (IllegalArgumentException ex) {
                throw new MessagingException("Forbidden: invalid destination");
            }
        }
    }
}
