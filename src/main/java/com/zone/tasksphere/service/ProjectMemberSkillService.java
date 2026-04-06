package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.UpdateMemberSkillsRequest;
import com.zone.tasksphere.dto.response.MemberSkillResponse;
import com.zone.tasksphere.entity.ProjectMember;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.repository.ProjectMemberRepository;
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

        // Save to project_members.skill_tags (highest priority for AI scoring)
        // Does NOT modify users.skill_tags (global profile)
        pm.setSkillTags(sanitized);
        projectMemberRepository.save(pm);

        return toResponse(pm);
    }

    private MemberSkillResponse toResponse(ProjectMember pm) {
        User u = pm.getUser();
        // Return effective skill: project-scoped first, then user profile
        List<String> effectiveSkills = (pm.getSkillTags() != null && !pm.getSkillTags().isEmpty())
                ? pm.getSkillTags()
                : (u.getSkillTags() != null ? u.getSkillTags() : Collections.emptyList());
        return MemberSkillResponse.builder()
                .userId(u.getId().toString())
                .fullName(u.getFullName())
                .avatarUrl(u.getAvatarUrl())
                .role(pm.getProjectRole() != null ? pm.getProjectRole().name() : null)
                .skillTags(effectiveSkills)
                .activeTaskCount(pm.getActiveTaskCount())
                .avgStoryPoints(pm.getAvgStoryPoints())
                .workCapacityHours(u.getWorkCapacityHours() != null ? u.getWorkCapacityHours() : 40)
                .build();
    }
}
