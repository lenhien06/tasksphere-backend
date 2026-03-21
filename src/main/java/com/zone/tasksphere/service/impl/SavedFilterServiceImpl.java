package com.zone.tasksphere.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.dto.request.CreateSavedFilterRequest;
import com.zone.tasksphere.dto.response.SavedFilterResponse;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.SavedFilter;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.exception.BusinessRuleException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.SavedFilterRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.service.SavedFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SavedFilterServiceImpl implements SavedFilterService {

    private static final int MAX_FILTERS_PER_PROJECT = 10;

    private final SavedFilterRepository savedFilterRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // ════════════════════════════════════════
    // POST /api/v1/projects/{projectId}/saved-filters
    // ════════════════════════════════════════
    @Override
    public SavedFilterResponse createFilter(UUID projectId, CreateSavedFilterRequest request, UUID currentUserId) {
        validateMembership(projectId, currentUserId);
        Project project = getProject(projectId);
        User user = getUser(currentUserId);

        // Check giới hạn 10 filter/project/user
        long count = savedFilterRepository.countByProjectIdAndCreatedById(projectId, currentUserId);
        if (count >= MAX_FILTERS_PER_PROJECT) {
            throw new BusinessRuleException("Tối đa " + MAX_FILTERS_PER_PROJECT + " bộ lọc mỗi dự án");
        }

        // Serialize filterCriteria → JSON string
        String criteriaJson;
        try {
            criteriaJson = objectMapper.writeValueAsString(request.getFilterCriteria());
        } catch (Exception e) {
            throw new BusinessRuleException("filterCriteria không hợp lệ");
        }

        SavedFilter filter = SavedFilter.builder()
                .project(project)
                .createdBy(user)
                .name(request.getName())
                .filterCriteria(criteriaJson)
                .isPublic(false)
                .build();

        filter = savedFilterRepository.save(filter);
        log.info("SavedFilter '{}' created by {} in project {}", filter.getName(), currentUserId, projectId);

        return toResponse(filter);
    }

    // ════════════════════════════════════════
    // GET /api/v1/projects/{projectId}/saved-filters
    // ════════════════════════════════════════
    @Override
    @Transactional(readOnly = true)
    public List<SavedFilterResponse> getFilters(UUID projectId, UUID currentUserId) {
        validateMembership(projectId, currentUserId);
        return savedFilterRepository
                .findByProjectIdAndCreatedByIdOrderByCreatedAtDesc(projectId, currentUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ════════════════════════════════════════
    // DELETE /api/v1/saved-filters/{filterId}
    // ════════════════════════════════════════
    @Override
    public void deleteFilter(UUID filterId, UUID currentUserId) {
        SavedFilter filter = savedFilterRepository.findById(filterId)
                .orElseThrow(() -> new NotFoundException("Bộ lọc không tồn tại: " + filterId));

        UUID projectId = filter.getProject().getId();
        boolean isOwner = filter.getCreatedBy().getId().equals(currentUserId);
        boolean isPM = memberRepository.findByProjectIdAndUserId(projectId, currentUserId)
                .map(m -> m.getProjectRole() == ProjectRole.PROJECT_MANAGER)
                .orElse(false);

        if (!isOwner && !isPM) {
            throw new Forbidden("Chỉ chủ sở hữu hoặc PM mới được xóa bộ lọc");
        }

        filter.setDeletedAt(Instant.now());
        savedFilterRepository.save(filter);
        log.info("SavedFilter {} deleted by {}", filterId, currentUserId);
    }

    // ════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════

    private void validateMembership(UUID projectId, UUID userId) {
        if (!memberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new Forbidden("Bạn không phải thành viên dự án này");
        }
    }

    private Project getProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project không tồn tại: " + projectId));
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User không tồn tại: " + userId));
    }

    @SuppressWarnings("unchecked")
    private SavedFilterResponse toResponse(SavedFilter filter) {
        Map<String, Object> criteria;
        try {
            criteria = objectMapper.readValue(filter.getFilterCriteria(),
                    new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize filterCriteria for filter {}: {}", filter.getId(), e.getMessage());
            criteria = Map.of();
        }

        return SavedFilterResponse.builder()
                .id(filter.getId())
                .name(filter.getName())
                .filterCriteria(criteria)
                .createdAt(filter.getCreatedAt())
                .build();
    }
}
