package com.zone.tasksphere.security;

import com.zone.tasksphere.dto.response.RoleDto;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = null;
        
        // 1. Thử tìm theo UUID trước (Dành cho JWT Filter)
        try {
            java.util.UUID uuid = java.util.UUID.fromString(identifier);
            user = userRepository.findById(uuid).orElse(null);
        } catch (IllegalArgumentException ignored) {
            // Không phải định dạng UUID, bỏ qua
        }

        // 2. Nếu không phải UUID hoặc không tìm thấy, thử tìm theo Email (Dành cho Login)
        if (user == null) {
            user = userRepository.findByEmail(identifier)
                    .orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại: " + identifier));
        }

        Set<GrantedAuthority> authorities = buildAuthorities(user);

        UserDetail userDetailDto = UserDetail.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .systemRole(user.getSystemRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .role(user.getRole() != null ? new RoleDto(user.getRole()) : null)
                .build();

        boolean enabled = (user.getStatus() == UserStatus.ACTIVE);
        boolean accountNonLocked = (user.getStatus() != UserStatus.SUSPENDED);

        // Luôn trả về Email làm username chính cho Spring Security
        return new CustomUserDetail(user.getEmail(), user.getPasswordHash() != null ? user.getPasswordHash() : "",
                enabled, accountNonLocked, userDetailDto, authorities);
    }

    private Set<GrantedAuthority> buildAuthorities(User user) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // System-level role from JWT enum
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getSystemRole().name()));

        // RBAC role permissions from Role entity
        if (user.getRole() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getSlug()));
            if (user.getRole().getPermissions() != null) {
                user.getRole().getPermissions().forEach(permission ->
                        authorities.add(new SimpleGrantedAuthority(permission.getCode())));
            }
        }
        return authorities;
    }
}
