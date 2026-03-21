package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.CreateUserRequest;
import com.zone.tasksphere.dto.request.NotifPrefsRequest;
import com.zone.tasksphere.dto.request.UpdateProfileRequest;
import com.zone.tasksphere.dto.response.PageResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.entity.enums.UserStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {
    PageResponse<UserDetail> listUsers(String q, UserStatus status, Long roleId, Pageable pageable);
    UserDetail createUser(CreateUserRequest request);
    UserDetail updateMyProfile(UUID userId, UpdateProfileRequest request);
    void lockUser(UUID userId);
    void unlockUser(UUID userId);
    void updateNotificationPreferences(UUID userId, NotifPrefsRequest request);
}
