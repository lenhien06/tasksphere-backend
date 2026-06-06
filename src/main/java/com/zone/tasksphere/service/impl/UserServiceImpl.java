package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.CreateUserRequest;
import com.zone.tasksphere.dto.request.NotifPrefsRequest;
import com.zone.tasksphere.dto.request.UpdateProfileRequest;
import com.zone.tasksphere.dto.response.NotificationPreferencesResponse;
import com.zone.tasksphere.dto.response.PageResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.entity.Role;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.NotificationType;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.ConflictException;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.RoleRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.repository.WorklogRepository;
import com.zone.tasksphere.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TaskRepository taskRepository;
    private final WorklogRepository worklogRepository;
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
        if (request.getTimezone() != null && !request.getTimezone().isBlank()) {
            user.setTimezone(validateTimezone(request.getTimezone()));
        }
        user.setWeekdaysOnly(request.isWeekdaysOnly());
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
    public NotificationPreferencesResponse getNotificationPreferences(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return toNotificationPreferencesResponse(user);
    }

    @Override
    @Transactional
    public NotificationPreferencesResponse updateNotificationPreferences(UUID userId, NotifPrefsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (request.getEmailDailyDigest() != null) {
            user.setEmailDailyDigest(request.getEmailDailyDigest());
        }
        if (request.getWeekdaysOnly() != null) {
            user.setWeekdaysOnly(request.getWeekdaysOnly());
        }
        if (request.getTimezone() != null && !request.getTimezone().isBlank()) {
            user.setTimezone(validateTimezone(request.getTimezone()));
        }

        userRepository.save(user);
        return toNotificationPreferencesResponse(user);
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

    private NotificationPreferencesResponse toNotificationPreferencesResponse(User user) {
        return NotificationPreferencesResponse.builder()
                .emailDailyDigest(user.isEmailDailyDigest())
                .weekdaysOnly(user.isWeekdaysOnly())
                .timezone(user.getTimezone() != null && !user.getTimezone().isBlank() ? user.getTimezone() : "Asia/Ho_Chi_Minh")
                .typePreferences(defaultTypePreferences())
                .build();
    }

    private Map<String, Boolean> defaultTypePreferences() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        for (NotificationType type : NotificationType.values()) {
            defaults.put(type.name(), type.isSendEmail());
        }
        return defaults;
    }

    private String validateTimezone(String timezone) {
        try {
            return ZoneId.of(timezone).getId();
        } catch (Exception ex) {
            throw new BadRequestException("Timezone không hợp lệ");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportUserPerformanceCsv(UUID userId) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NotFoundException("User not found"));

            List<com.zone.tasksphere.entity.Task> tasks = taskRepository.findByAssigneeIdAndDeletedAtIsNull(userId);
            List<com.zone.tasksphere.entity.Worklog> worklogs = worklogRepository.findByUserIdAndDeletedAtIsNullOrderByLogDateDesc(userId);

            long totalTasks = tasks.size();
            long completedTasks = tasks.stream()
                    .filter(t -> t.getTaskStatus() != null && com.zone.tasksphere.entity.enums.TaskStatus.DONE.equals(t.getTaskStatus()))
                    .count();
            long totalSecondsLogged = worklogs.stream()
                    .mapToLong(com.zone.tasksphere.entity.Worklog::getTimeSpentSeconds)
                    .sum();
            double totalHoursLogged = totalSecondsLogged / 3600.0;

            StringBuilder csvBuilder = new StringBuilder();
            // Add UTF-8 BOM
            csvBuilder.append('\ufeff');

            // 1. User Summary Section
            csvBuilder.append("--- THÔNG TIN NHÂN SỰ ---\n");
            csvBuilder.append("Họ và tên,Email,Tổng số công việc,Công việc hoàn thành,Tổng giờ làm việc\n");
            csvBuilder.append(escapeCsv(user.getFullName())).append(",")
                      .append(escapeCsv(user.getEmail())).append(",")
                      .append(totalTasks).append(",")
                      .append(completedTasks).append(",")
                      .append(String.format(java.util.Locale.US, "%.2f", totalHoursLogged)).append("\n\n");

            // 2. Task Performance Section
            csvBuilder.append("--- DANH SÁCH CÔNG VIỆC ĐƯỢC GIAO ---\n");
            csvBuilder.append("Mã CV,Tên công việc,Dự án,Trạng thái,Độ ưu tiên,Thời gian dự kiến (Giờ),Thời gian thực tế (Giờ)\n");
            for (com.zone.tasksphere.entity.Task task : tasks) {
                String estHours = task.getEstimatedHours() != null ? task.getEstimatedHours().toString() : "0";
                String actHours = task.getActualHours() != null ? task.getActualHours().toString() : "0";
                csvBuilder.append(escapeCsv(task.getTaskCode())).append(",")
                          .append(escapeCsv(task.getTitle())).append(",")
                          .append(escapeCsv(task.getProject() != null ? task.getProject().getName() : "")).append(",")
                          .append(task.getTaskStatus() != null ? task.getTaskStatus().name() : "").append(",")
                          .append(task.getPriority() != null ? task.getPriority().name() : "").append(",")
                          .append(estHours).append(",")
                          .append(actHours).append("\n");
            }
            csvBuilder.append("\n");

            // 3. Worklog Section
            csvBuilder.append("--- CHI TIẾT GIỜ LÀM VIỆC ---\n");
            csvBuilder.append("Ngày ghi nhận,Mã CV,Dự án,Thời gian (Giờ),Ghi chú\n");
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (com.zone.tasksphere.entity.Worklog worklog : worklogs) {
                double hours = worklog.getTimeSpentSeconds() / 3600.0;
                String dateStr = worklog.getLogDate() != null ? worklog.getLogDate().format(dateFormatter) : "";
                com.zone.tasksphere.entity.Task t = worklog.getTask();
                String projName = (t != null && t.getProject() != null) ? t.getProject().getName() : "";
                
                csvBuilder.append(dateStr).append(",")
                          .append(escapeCsv(t != null ? t.getTaskCode() : "")).append(",")
                          .append(escapeCsv(projName)).append(",")
                          .append(String.format(java.util.Locale.US, "%.2f", hours)).append(",")
                          .append(escapeCsv(worklog.getDescription())).append("\n");
            }

            return csvBuilder.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                java.nio.file.Files.writeString(
                    java.nio.file.Paths.get("export-error.log"), 
                    e.getMessage() + "\n" + java.util.Arrays.toString(e.getStackTrace())
                );
            } catch (Exception ignored) {}
            throw e;
        }
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
