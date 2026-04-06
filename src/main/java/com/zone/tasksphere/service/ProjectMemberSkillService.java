package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.UpdateMemberSkillsRequest;
import com.zone.tasksphere.dto.response.MemberSkillResponse;
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
public class ProjectMemberSkillService {

    private final ProjectMemberRepository projectMemberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<MemberSkillResponse> getMemberSkills(UUID projectId) {
        return projectMemberRepository.findAllByProjectIdWithUser(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public MemberSkillResponse updateMemberSkills(UUID projectId, UUID targetUserId,
                                                   UpdateMemberSkillsRequest request) {
        // Validate member belongs to this project
        ProjectMember pm = projectMemberRepository.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User " + targetUserId + " is not a member of project " + projectId));

        // Sanitize tags
        List<String> sanitized = request.getSkillTags() == null
                ? Collections.emptyList()
                : request.getSkillTags().stream()
                        .map(String::trim)
                        .filter(t -> !t.isBlank())
                        .distinct()
                        .limit(20)
                        .toList();

        User user = pm.getUser();
        user.setSkillTags(sanitized);
        userRepository.save(user);

        return toResponse(pm);
    }

    private MemberSkillResponse toResponse(ProjectMember pm) {
        User u = pm.getUser();
        return MemberSkillResponse.builder()
                .userId(u.getId().toString())
                .fullName(u.getFullName())
                .avatarUrl(u.getAvatarUrl())
                .role(pm.getProjectRole() != null ? pm.getProjectRole().name() : null)
                .skillTags(u.getSkillTags() != null ? u.getSkillTags() : Collections.emptyList())
                .activeTaskCount(pm.getActiveTaskCount())
                .avgStoryPoints(pm.getAvgStoryPoints())
                .workCapacityHours(u.getWorkCapacityHours())
                .build();
    }
}
