package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.UpdateUserProfileRequest;
import com.zone.tasksphere.dto.response.UserProfileResponse;
import com.zone.tasksphere.entity.ProjectMember;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        List<ProjectMember> memberships = projectMemberRepository.findByUserIdWithProject(userId);

        List<UserProfileResponse.ParticipatedProjectDto> projects = memberships.stream()
                .map(pm -> UserProfileResponse.ParticipatedProjectDto.builder()
                        .id(pm.getProject().getId().toString())
                        .name(pm.getProject().getName())
                        .description(pm.getProject().getDescription())
                        .visibility(pm.getProject().getVisibility() != null
                                ? pm.getProject().getVisibility().name().toLowerCase()
                                : "private")
                        .role(pm.getProjectRole() != null ? pm.getProjectRole().name() : null)
                        .build())
                .toList();

        return UserProfileResponse.builder()
                .id(user.getId().toString())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .jobTitle(user.getJobTitle())
                .bio(user.getBio())
                .skillTags(user.getSkillTags() != null ? user.getSkillTags() : Collections.emptyList())
                .workCapacityHours(user.getWorkCapacityHours() != null ? user.getWorkCapacityHours() : 40)
                .participatedProjects(projects)
                .build();
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateUserProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));

        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getJobTitle() != null) {
            user.setJobTitle(request.getJobTitle().trim().isEmpty() ? null : request.getJobTitle().trim());
        }
        if (request.getBio() != null) {
            user.setBio(request.getBio().trim().isEmpty() ? null : request.getBio().trim());
        }
        if (request.getWorkCapacityHours() != null) {
            user.setWorkCapacityHours(request.getWorkCapacityHours());
        }

        userRepository.save(user);
        return getProfile(userId);
    }
}
