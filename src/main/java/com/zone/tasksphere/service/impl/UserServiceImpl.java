package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.CreateUserRequest;
import com.zone.tasksphere.dto.request.NotifPrefsRequest;
import com.zone.tasksphere.dto.request.UpdateProfileRequest;
import com.zone.tasksphere.dto.response.PageResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.entity.Role;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.ConflictException;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.RoleRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResponse<UserDetail> listUsers(String q, UserStatus status, Long roleId, Pageable pageable) {
        Page<User> users = userRepository.findAllWithFilter(q, roleId, status, pageable);
        return PageResponse.<UserDetail>builder()
                .content(users.getContent().stream().map(this::mapToUserDetail).collect(Collectors.toList()))
                .number(users.getNumber())
                .size(users.getSize())
                .totalElements(users.getTotalElements())
                .totalPages(users.getTotalPages())
                .last(users.isLast())
                .build();
    }

    @Override
    @Transactional
    public UserDetail createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already exists");
        }

        Role role = null;
        if (request.getRoleId() != null) {
            role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new NotFoundException("Role not found"));
        } else {
            role = roleRepository.findBySlug("USER")
                    .orElseThrow(() -> new BadRequestException("Default role not found"));
        }

        User user = User.builder()
                .fullName(request.getFullName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.ACTIVE)
                .systemRole(SystemRole.USER)
                .role(role)
                .build();

        return mapToUserDetail(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserDetail updateMyProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        // Additional settings
        user.setEmailDailyDigest(request.isEmailDailyDigest());

        return mapToUserDetail(userRepository.save(user));
    }

    @Override
    @Transactional
    public void lockUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setStatus(UserStatus.SUSPENDED);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void unlockUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void updateNotificationPreferences(UUID userId, NotifPrefsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        user.setEmailDailyDigest(request.isEmailDailyDigest());
        // Save other preferences if needed
        userRepository.save(user);
    }

    private UserDetail mapToUserDetail(User user) {
        return UserDetail.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus())
                .systemRole(user.getSystemRole())
                .role(user.getRole() != null ? new com.zone.tasksphere.dto.response.RoleDto(user.getRole()) : null)
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
