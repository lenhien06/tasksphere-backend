package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.CreateWorkspaceRequest;
import com.zone.tasksphere.dto.request.UpdateMemberSkillsRequest;
import com.zone.tasksphere.dto.request.UpdateWorkspaceRequest;
import com.zone.tasksphere.dto.request.WorkspaceInviteMemberRequest;
import com.zone.tasksphere.dto.response.WorkspaceMemberResponse;
import com.zone.tasksphere.dto.response.WorkspaceResponse;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.Workspace;
import com.zone.tasksphere.entity.WorkspaceMember;
import com.zone.tasksphere.entity.WorkspaceMemberId;
import com.zone.tasksphere.entity.enums.WorkspaceRole;
import com.zone.tasksphere.entity.enums.WorkspaceType;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.ConflictException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.repository.WorkspaceMemberRepository;
import com.zone.tasksphere.repository.WorkspaceRepository;
import com.zone.tasksphere.service.WorkspaceService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    // ─────────────────────────────────────────────────────────────────────────────
    // Workspace CRUD
    // ─────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, UUID creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new NotFoundException("Người dùng không tồn tại"));

        String slug = resolveUniqueSlug(
                request.getSlug() != null ? request.getSlug() : generateSlug(request.getName())
        );

        Workspace workspace = Workspace.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .owner(creator)
                .type(WorkspaceType.ORGANIZATION)
                .build();

        workspace = workspaceRepository.save(workspace);

        // Creator becomes OWNER
        WorkspaceMember ownerMember = WorkspaceMember.builder()
                .id(new WorkspaceMemberId(workspace.getId(), creatorId))
                .workspace(workspace)
                .user(creator)
                .role(WorkspaceRole.OWNER)
                .joinedAt(Instant.now())
                .build();
        workspaceMemberRepository.save(ownerMember);

        return toResponse(workspace, WorkspaceRole.OWNER, 1, 0);
    }

    @Override
    public List<WorkspaceResponse> getMyWorkspaces(UUID userId) {
        ensurePersonalWorkspace(userId);

        List<Workspace> workspaces = workspaceRepository.findAllByMemberUserId(userId).stream()
                .sorted(Comparator
                        .comparing((Workspace ws) -> ws.getType() != WorkspaceType.PERSONAL)
                        .thenComparing(Workspace::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return workspaces.stream()
                .map(ws -> {
                    WorkspaceRole role = workspaceMemberRepository
                            .findByWorkspaceIdAndUserId(ws.getId(), userId)
                            .map(WorkspaceMember::getRole)
                            .orElse(null);
                    int memberCount = workspaceMemberRepository.countByWorkspaceId(ws.getId());
                    int projectCount = workspaceRepository.countProjectsByWorkspaceId(ws.getId());
                    return toResponse(ws, role, memberCount, projectCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    public WorkspaceResponse getBySlug(String slug, UUID currentUserId) {
        Workspace ws = workspaceRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Workspace không tồn tại"));

        WorkspaceRole role = currentUserId != null
                ? workspaceMemberRepository.findByWorkspaceIdAndUserId(ws.getId(), currentUserId)
                        .map(WorkspaceMember::getRole).orElse(null)
                : null;

        int memberCount = workspaceMemberRepository.countByWorkspaceId(ws.getId());
        int projectCount = workspaceRepository.countProjectsByWorkspaceId(ws.getId());
        return toResponse(ws, role, memberCount, projectCount);
    }

    @Override
    @Transactional
    public WorkspaceResponse updateWorkspace(UUID workspaceId, UpdateWorkspaceRequest request, UUID requesterId) {
        Workspace ws = getWorkspaceOrThrow(workspaceId);
        requireAdminOrOwner(ws, requesterId);

        ws.setName(request.getName());
        if (request.getDescription() != null) ws.setDescription(request.getDescription());
        if (request.getAvatarUrl() != null) ws.setAvatarUrl(request.getAvatarUrl());

        ws = workspaceRepository.save(ws);

        WorkspaceRole role = workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, requesterId)
                .map(WorkspaceMember::getRole).orElse(null);
        int memberCount = workspaceMemberRepository.countByWorkspaceId(workspaceId);
        int projectCount = workspaceRepository.countProjectsByWorkspaceId(workspaceId);
        return toResponse(ws, role, memberCount, projectCount);
    }

    @Override
    @Transactional
    public void deleteWorkspace(UUID workspaceId, UUID requesterId) {
        Workspace ws = getWorkspaceOrThrow(workspaceId);
        requireOwner(ws, requesterId);
        if (ws.getType() == WorkspaceType.PERSONAL) {
            throw new BadRequestException("Không thể xoá personal workspace mặc định");
        }

        // Detach all child projects FIRST (set workspace = NULL → standalone)
        projectRepository.detachFromWorkspace(workspaceId);

        // Soft delete workspace
        ws.setDeletedAt(Instant.now());
        workspaceRepository.save(ws);

        log.info("Workspace {} soft-deleted by {}, child projects moved to standalone", workspaceId, requesterId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Member management
    // ─────────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public WorkspaceMemberResponse inviteMember(UUID workspaceId,
                                                WorkspaceInviteMemberRequest request,
                                                UUID inviterId) {
        Workspace ws = getWorkspaceOrThrow(workspaceId);
        requireAdminOrOwner(ws, inviterId);

        // Only ADMIN/MEMBER roles can be assigned via invite (not OWNER)
        if (WorkspaceRole.OWNER.equals(request.getRole())) {
            throw new BadRequestException("Không thể mời thành viên với vai trò OWNER");
        }

        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            throw new NotFoundException("Không tìm thấy người dùng với email này");
        }

        User invitee = userOpt.get();
        UUID inviteeId = invitee.getId();

        if (workspaceMemberRepository.existsByIdWorkspaceIdAndIdUserId(workspaceId, inviteeId)) {
            ConflictException ex = new ConflictException();
            ex.setMessage("Người dùng đã là thành viên của workspace này");
            throw ex;
        }

        WorkspaceMember member = WorkspaceMember.builder()
                .id(new WorkspaceMemberId(workspaceId, inviteeId))
                .workspace(ws)
                .user(invitee)
                .role(request.getRole())
                .skillTags(request.getSkillTags())
                .invitedBy(inviterId)
                .joinedAt(Instant.now())
                .build();

        workspaceMemberRepository.save(member);
        log.info("User {} added to workspace {} as {}", inviteeId, workspaceId, request.getRole());

        return toMemberResponse(member, invitee);
    }

    @Override
    public List<WorkspaceMemberResponse> getMembers(UUID workspaceId) {
        getWorkspaceOrThrow(workspaceId);
        return workspaceMemberRepository.findByWorkspaceId(workspaceId).stream()
                .map(m -> toMemberResponse(m, m.getUser()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WorkspaceMemberResponse updateMemberSkills(UUID workspaceId, UUID userId,
                                                      UpdateMemberSkillsRequest request,
                                                      UUID requesterId) {
        getWorkspaceOrThrow(workspaceId);

        // Member can edit own skills; ADMIN/OWNER can edit any member's skills
        if (!userId.equals(requesterId)) {
            requireAdminOrOwner(getWorkspaceOrThrow(workspaceId), requesterId);
        }

        WorkspaceMember member = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new NotFoundException("Thành viên không tồn tại trong workspace"));

        member.setSkillTags(request.getSkillTags());
        workspaceMemberRepository.save(member);

        return toMemberResponse(member, member.getUser());
    }

    @Override
    @Transactional
    public void removeMember(UUID workspaceId, UUID userId, UUID requesterId) {
        Workspace ws = getWorkspaceOrThrow(workspaceId);

        WorkspaceMember target = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new NotFoundException("Thành viên không tồn tại trong workspace"));

        // OWNER cannot be removed
        if (WorkspaceRole.OWNER.equals(target.getRole())) {
            throw new BadRequestException("Không thể xoá OWNER khỏi workspace");
        }

        // Must be ADMIN/OWNER to remove others; members can remove themselves
        if (!userId.equals(requesterId)) {
            requireAdminOrOwner(ws, requesterId);
        }

        workspaceMemberRepository.delete(target);
        log.info("Member {} removed from workspace {} by {}", userId, workspaceId, requesterId);
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────────

    private Workspace getWorkspaceOrThrow(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace không tồn tại"));
    }

    private void requireOwner(Workspace ws, UUID requesterId) {
        if (!ws.getOwner().getId().equals(requesterId)) {
            throw new Forbidden("Chỉ OWNER mới được thực hiện thao tác này");
        }
    }

    private void requireAdminOrOwner(Workspace ws, UUID requesterId) {
        workspaceMemberRepository.findByWorkspaceIdAndUserId(ws.getId(), requesterId)
                .filter(m -> m.getRole().canManageWorkspace())
                .orElseThrow(() -> new Forbidden("Cần quyền ADMIN hoặc OWNER để thực hiện thao tác này"));
    }

    /** Auto-generate slug: lowercase name, spaces → hyphens, remove invalid chars. */
    String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    /** Ensure slug is unique; append -2, -3, ... if needed. */
    private String resolveUniqueSlug(String base) {
        if (!workspaceRepository.existsBySlug(base)) return base;
        int suffix = 2;
        while (workspaceRepository.existsBySlug(base + "-" + suffix)) suffix++;
        return base + "-" + suffix;
    }

    private WorkspaceResponse toResponse(Workspace ws, WorkspaceRole role, int memberCount, int projectCount) {
        return WorkspaceResponse.builder()
                .id(ws.getId())
                .name(ws.getName())
                .slug(ws.getSlug())
                .description(ws.getDescription())
                .avatarUrl(ws.getAvatarUrl())
                .plan(ws.getPlan())
                .type(ws.getType())
                .memberCount(memberCount)
                .projectCount(projectCount)
                .role(role)
                .ownerId(ws.getOwner().getId())
                .ownerName(ws.getOwner().getFullName())
                .createdAt(ws.getCreatedAt())
                .updatedAt(ws.getUpdatedAt())
                .build();
    }

    private Workspace ensurePersonalWorkspace(UUID userId) {
        return workspaceRepository.findByOwnerIdAndType(userId, WorkspaceType.PERSONAL)
                .orElseGet(() -> createPersonalWorkspace(userId));
    }

    private Workspace createPersonalWorkspace(UUID userId) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Người dùng không tồn tại"));

        String displayName = owner.getFullName() != null && !owner.getFullName().isBlank()
                ? owner.getFullName().trim()
                : (owner.getEmail() != null ? owner.getEmail().split("@")[0] : "Personal");

        Workspace workspace = Workspace.builder()
                .name(displayName + " Workspace")
                .slug(resolveUniqueSlug(generateSlug(displayName + " personal workspace")))
                .description("Workspace ca nhan mac dinh")
                .owner(owner)
                .type(WorkspaceType.PERSONAL)
                .build();

        workspace = workspaceRepository.save(workspace);

        WorkspaceMember ownerMember = WorkspaceMember.builder()
                .id(new WorkspaceMemberId(workspace.getId(), userId))
                .workspace(workspace)
                .user(owner)
                .role(WorkspaceRole.OWNER)
                .joinedAt(Instant.now())
                .build();
        workspaceMemberRepository.save(ownerMember);

        return workspace;
    }

    private WorkspaceMemberResponse toMemberResponse(WorkspaceMember member, User user) {
        return WorkspaceMemberResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(member.getRole())
                .skillTags(member.getSkillTags())
                .activeTaskCount(member.getActiveTaskCount())
                .avgStoryPoints(member.getAvgStoryPoints())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
