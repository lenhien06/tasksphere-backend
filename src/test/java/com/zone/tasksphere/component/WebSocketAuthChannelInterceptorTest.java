package com.zone.tasksphere.component;

import com.zone.tasksphere.dto.response.RoleDto;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.security.CustomUserDetail;
import com.zone.tasksphere.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthChannelInterceptor(jwtUtils, userDetailsService, projectMemberRepository);
    }

    @Test
    void connect_withoutToken_rejectsConnection() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("missing token");
    }

    @Test
    void connect_withValidToken_setsUserIdPrincipal() {
        UUID userId = UUID.randomUUID();
        String token = "valid-token";
        String email = "user@tasksphere.io.vn";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer " + token);
        accessor.setLeaveMutable(true);

        when(jwtUtils.validateToken(token)).thenReturn(true);
        when(jwtUtils.extractUsername(token)).thenReturn(email);
        when(userDetailsService.loadUserByUsername(email)).thenReturn(buildUserDetails(userId, email));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(message, null);

        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo(userId.toString());
    }

    @Test
    void subscribe_toAnotherUsersDestination_rejects() {
        UUID currentUserId = UUID.randomUUID();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/" + UUID.randomUUID() + "/queue/notifications");
        accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            currentUserId.toString(), null, List.of()
        ));
        accessor.setLeaveMutable(true);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("another user's destination");
    }

    @Test
    void subscribe_toProjectTopic_requiresMembership() {
        UUID currentUserId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/project/" + projectId);
        accessor.setUser(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            currentUserId.toString(), null, List.of()
        ));
        accessor.setLeaveMutable(true);

        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUserId)).thenReturn(false);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, null))
            .isInstanceOf(MessagingException.class)
            .hasMessageContaining("not a project member");
    }

    private CustomUserDetail buildUserDetails(UUID userId, String email) {
        UserDetail userDetail = UserDetail.builder()
            .id(userId)
            .email(email)
            .fullName("User Test")
            .systemRole(SystemRole.USER)
            .status(UserStatus.ACTIVE)
            .role(new RoleDto())
            .build();

        return new CustomUserDetail(
            email,
            "hashed",
            true,
            true,
            userDetail,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
    }
}
