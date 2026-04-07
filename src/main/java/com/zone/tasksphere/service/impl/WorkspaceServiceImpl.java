package com.zone.tasksphere.service.impl;

import com.zone.tasksphere.dto.request.CreateWorkspaceRequest;
import com.zone.tasksphere.dto.request.UpdateMemberSkillsRequest;
import com.zone.tasksphere.dto.request.UpdateWorkspaceRequest;
import com.zone.tasksphere.dto.request.WorkspaceInviteMemberRequest;
import com.zone.tasksphere.dto.response.UserProfileResponse;
import com.zone.tasksphere.dto.response.WorkspaceInviteListResponse;
import com.zone.tasksphere.dto.response.WorkspaceInviteResponse;
import com.zone.tasksphere.dto.response.WorkspaceMemberResponse;
import com.zone.tasksphere.dto.response.WorkspaceResponse;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.Workspace;
import com.zone.tasksphere.entity.WorkspaceInvite;
import com.zone.tasksphere.entity.WorkspaceMember;
import com.zone.tasksphere.entity.WorkspaceMemberId;
import com.zone.tasksphere.entity.enums.EntityType;
import com.zone.tasksphere.entity.enums.InviteStatus;
import com.zone.tasksphere.entity.enums.NotificationType;
import com.zone.tasksphere.entity.enums.WorkspaceRole;
import com.zone.tasksphere.entity.enums.WorkspaceType;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.ConflictException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.repository.WorkspaceInviteRepository;
import com.zone.tasksphere.repository.WorkspaceMemberRepository;
import com.zone.tasksphere.repository.WorkspaceRepository;
import com.zone.tasksphere.service.EmailService;
import com.zone.tasksphere.service.NotificationService;
import com.zone.tasksphere.service.UserProfileService;
import com.zone.tasksphere.service.WebSocketService;
import com.zone.tasksphere.service.WorkspaceService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceServiceImpl implements WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceInviteRepository workspaceInviteRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final UserProfileService userProfileService;
    private final WebSocketService webSocketService;

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
    public WorkspaceInviteResponse inviteMember(UUID workspaceId,
                                                WorkspaceInviteMemberRequest request,
                                                UUID inviterId) {
        Workspace ws = getWorkspaceOrThrow(workspaceId);
        requireAdminOrOwner(ws, inviterId);

        // Only ADMIN/MEMBER roles can be assigned via invite (not OWNER)
        if (WorkspaceRole.OWNER.equals(request.getRole())) {
            throw new BadRequestException("Không thể mời thành viên với vai trò OWNER");
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();
        List<String> sanitizedSkills = sanitizeSkillTags(request.getSkillTags());

        User inviter = userRepository.findById(inviterId)
                .orElseThrow(() -> new NotFoundException("Người mời không tồn tại"));
        String inviterName = inviter.getFullName() != null && !inviter.getFullName().isBlank()
                ? inviter.getFullName()
                : inviter.getEmail();

        Optional<User> userOpt = userRepository.findByEmail(normalizedEmail);
        if (userOpt.isPresent()) {
            User invitee = userOpt.get();
            UUID inviteeId = invitee.getId();

            if (workspaceMemberRepository.existsByIdWorkspaceIdAndIdUserId(workspaceId, inviteeId)) {
                ConflictException ex = new ConflictException();
                ex.setMessage("Người dùng đã là thành viên của workspace này");
                throw ex;
            }

            workspaceInviteRepository.findByWorkspaceIdAndInviteeEmailAndStatus(workspaceId, normalizedEmail, InviteStatus.PENDING)
                    .ifPresent(invite -> {
                        invite.setStatus(InviteStatus.REVOKED);
                        workspaceInviteRepository.save(invite);
                    });

            WorkspaceMember member = WorkspaceMember.builder()
                    .id(new WorkspaceMemberId(workspaceId, inviteeId))
                    .workspace(ws)
                    .user(invitee)
                    .role(request.getRole())
                    .skillTags(sanitizedSkills.isEmpty() ? null : sanitizedSkills)
                    .invitedBy(inviterId)
                    .joinedAt(Instant.now())
                    .build();

            workspaceMemberRepository.save(member);
            notificationService.createNotification(
                    invitee,
                    NotificationType.PROJECT_INVITED,
                    "Bạn đã được thêm vào workspace",
                    "Bạn đã được thêm vào workspace " + ws.getName() + " với vai trò " + request.getRole().name(),
                    EntityType.WORKSPACE.name(),
                    ws.getId()
            );
            emailService.sendWorkspaceInviteEmail(
                    invitee.getEmail(),
                    ws.getName(),
                    inviterName,
                    request.getRole().name(),
                    true,
                    ws.getSlug(),
                    null
            );
            afterCommit(() -> publishWorkspaceMemberEvent(ws.getId(), "workspace.member_added", Map.of(
                    "userId", invitee.getId(),
                    "email", invitee.getEmail()
            )));

            log.info("User {} added to workspace {} as {}", inviteeId, workspaceId, request.getRole());
            return WorkspaceInviteResponse.builder()
                    .email(invitee.getEmail())
                    .role(request.getRole())
                    .existingUser(true)
                    .addedToWorkspace(true)
                    .status("active")
                    .build();
        }

        workspaceInviteRepository.findByWorkspaceIdAndInviteeEmailAndStatus(workspaceId, normalizedEmail, InviteStatus.PENDING)
                .ifPresent(invite -> {
                    invite.setStatus(InviteStatus.REVOKED);
                    workspaceInviteRepository.save(invite);
                });

        WorkspaceInvite invite = workspaceInviteRepository.save(WorkspaceInvite.builder()
                .workspace(ws)
                .invitedBy(inviter)
                .inviteeEmail(normalizedEmail)
                .token(UUID.randomUUID().toString())
                .workspaceRole(request.getRole())
                .status(InviteStatus.PENDING)
                .expiresAt(Instant.now().plus(72, ChronoUnit.HOURS))
                .skillTags(sanitizedSkills.isEmpty() ? null : sanitizedSkills)
                .build());

        emailService.sendWorkspaceInviteEmail(
                normalizedEmail,
                ws.getName(),
                inviterName,
                request.getRole().name(),
                false,
                ws.getSlug(),
                invite.getToken()
        );
        afterCommit(() -> publishWorkspaceMemberEvent(ws.getId(), "workspace.invite_created", Map.of(
                "inviteId", invite.getId(),
                "email", invite.getInviteeEmail()
        )));
        log.info("Invitation email sent to external address {} for workspace {}", normalizedEmail, workspaceId);
        return WorkspaceInviteResponse.builder()
                .email(normalizedEmail)
                .role(request.getRole())
                .existingUser(false)
                .addedToWorkspace(false)
                .status("pending")
                .build();
    }

    @Override
    @Transactional
    public Page<WorkspaceInviteListResponse> getInvitesByStatus(UUID workspaceId, UUID actorId, InviteStatus status, Pageable pageable) {
        requireAdminOrOwner(getWorkspaceOrThrow(workspaceId), actorId);

        if (status == InviteStatus.PENDING) {
            workspaceInviteRepository.markExpiredInvites(workspaceId, Instant.now(), InviteStatus.PENDING, InviteStatus.EXPIRED);
        }

        return workspaceInviteRepository.findByWorkspaceIdAndStatus(workspaceId, status, pageable)
                .map(invite -> WorkspaceInviteListResponse.builder()
                        .id(invite.getId())
                        .email(invite.getInviteeEmail())
                        .role(invite.getWorkspaceRole())
                        .status(invite.getStatus())
                        .inviterName(invite.getInvitedBy().getFullName())
                        .invitedAt(invite.getCreatedAt())
                        .expiresAt(invite.getExpiresAt())
                        .daysLeft(invite.getStatus() == InviteStatus.PENDING
                                ? Math.max(0, ChronoUnit.DAYS.between(Instant.now(), invite.getExpiresAt()))
                                : null)
                        .build());
    }

    @Override
    @Transactional
    public void revokeInvite(UUID workspaceId, UUID inviteId, UUID actorId) {
        requireAdminOrOwner(getWorkspaceOrThrow(workspaceId), actorId);

        WorkspaceInvite invite = workspaceInviteRepository.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lời mời workspace"));

        if (!invite.getWorkspace().getId().equals(workspaceId)) {
            throw new BadRequestException("Lời mời không thuộc workspace này");
        }
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể thu hồi lời mời đang chờ");
        }

        invite.setStatus(InviteStatus.REVOKED);
        workspaceInviteRepository.save(invite);
        afterCommit(() -> publishWorkspaceMemberEvent(workspaceId, "workspace.invite_updated", Map.of(
                "inviteId", inviteId,
                "status", InviteStatus.REVOKED.name()
        )));
    }

    @Override
    @Transactional
    public void resendInvite(UUID workspaceId, UUID inviteId, UUID actorId) {
        Workspace ws = getWorkspaceOrThrow(workspaceId);
        requireAdminOrOwner(ws, actorId);

        WorkspaceInvite invite = workspaceInviteRepository.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lời mời workspace"));

        if (!invite.getWorkspace().getId().equals(workspaceId)) {
            throw new BadRequestException("Lời mời không thuộc workspace này");
        }
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể gửi lại lời mời đang chờ");
        }

        invite.setToken(UUID.randomUUID().toString());
        invite.setExpiresAt(Instant.now().plus(72, ChronoUnit.HOURS));
        workspaceInviteRepository.save(invite);

        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Người mời không tồn tại"));
        String inviterName = actor.getFullName() != null && !actor.getFullName().isBlank()
                ? actor.getFullName()
                : actor.getEmail();

        emailService.sendWorkspaceInviteEmail(
                invite.getInviteeEmail(),
                ws.getName(),
                inviterName,
                invite.getWorkspaceRole().name(),
                false,
                ws.getSlug(),
                invite.getToken()
        );
        afterCommit(() -> publishWorkspaceMemberEvent(workspaceId, "workspace.invite_updated", Map.of(
                "inviteId", inviteId,
                "status", InviteStatus.PENDING.name()
        )));
    }

    @Override
    @Transactional
    public void acceptInviteAfterSignup(String token, User newUser) {
        WorkspaceInvite invite = workspaceInviteRepository.findByToken(token).orElse(null);
        if (invite == null || invite.getStatus() != InviteStatus.PENDING) {
            return;
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            invite.setStatus(InviteStatus.EXPIRED);
            workspaceInviteRepository.save(invite);
            return;
        }
        if (!newUser.getEmail().equalsIgnoreCase(invite.getInviteeEmail())) {
            return;
        }
        if (workspaceMemberRepository.existsByIdWorkspaceIdAndIdUserId(invite.getWorkspace().getId(), newUser.getId())) {
            invite.setStatus(InviteStatus.ACCEPTED);
            invite.setAcceptedAt(Instant.now());
            invite.setInviteeUser(newUser);
            workspaceInviteRepository.save(invite);
            return;
        }

        WorkspaceMember member = WorkspaceMember.builder()
                .id(new WorkspaceMemberId(invite.getWorkspace().getId(), newUser.getId()))
                .workspace(invite.getWorkspace())
                .user(newUser)
                .role(invite.getWorkspaceRole())
                .skillTags(invite.getSkillTags())
                .invitedBy(invite.getInvitedBy().getId())
                .joinedAt(Instant.now())
                .build();
        workspaceMemberRepository.save(member);

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setAcceptedAt(Instant.now());
        invite.setInviteeUser(newUser);
        workspaceInviteRepository.save(invite);

        afterCommit(() -> publishWorkspaceMemberEvent(invite.getWorkspace().getId(), "workspace.member_added", Map.of(
                "userId", newUser.getId(),
                "email", newUser.getEmail()
        )));
    }

    @Override
    public List<WorkspaceMemberResponse> getMembers(UUID workspaceId) {
        getWorkspaceOrThrow(workspaceId);
        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);
        Map<UUID, Integer> openTaskCounts = loadWorkspaceOpenTaskCounts(workspaceId, members);
        return members.stream()
                .map(m -> toMemberResponse(m, m.getUser(), openTaskCounts.getOrDefault(m.getUser().getId(), 0)))
                .collect(Collectors.toList());
    }

    @Override
    public UserProfileResponse getMemberProfile(UUID workspaceId, UUID targetUserId, UUID requesterId) {
        getWorkspaceOrThrow(workspaceId);
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, requesterId)
                .orElseThrow(() -> new Forbidden("Bạn không phải thành viên của workspace này"));
        workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Thành viên không tồn tại trong workspace"));
        return userProfileService.getProfile(targetUserId);
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
        afterCommit(() -> publishWorkspaceMemberEvent(workspaceId, "workspace.member_skills_updated", Map.of(
                "userId", userId
        )));

        int exactActiveTaskCount = (int) taskRepository.countAssignedOpenTasksInWorkspace(workspaceId, userId);
        return toMemberResponse(member, member.getUser(), exactActiveTaskCount);
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
        afterCommit(() -> publishWorkspaceMemberEvent(workspaceId, "workspace.member_removed", Map.of(
                "userId", userId
        )));
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

    private WorkspaceMemberResponse toMemberResponse(WorkspaceMember member, User user, int activeTaskCount) {
        List<String> effectiveSkills = member.getSkillTags() != null && !member.getSkillTags().isEmpty()
                ? member.getSkillTags()
                : (user.getSkillTags() != null ? user.getSkillTags() : Collections.emptyList());
        return WorkspaceMemberResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .role(member.getRole())
                .skillTags(effectiveSkills)
                .activeTaskCount(activeTaskCount)
                .avgStoryPoints(member.getAvgStoryPoints())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private Map<UUID, Integer> loadWorkspaceOpenTaskCounts(UUID workspaceId, List<WorkspaceMember> members) {
        if (members.isEmpty()) {
            return Map.of();
        }
        List<UUID> userIds = members.stream()
                .map(member -> member.getUser().getId())
                .toList();
        Map<UUID, Integer> counts = new HashMap<>();
        taskRepository.countAssignedOpenTasksByWorkspaceAndUsers(workspaceId, userIds)
                .forEach(row -> counts.put((UUID) row[0], ((Long) row[1]).intValue()));
        return counts;
    }

    private List<String> sanitizeSkillTags(List<String> skillTags) {
        if (skillTags == null) {
            return Collections.emptyList();
        }
        return skillTags.stream()
                .map(tag -> tag == null ? "" : tag.trim())
                .filter(tag -> !tag.isBlank())
                .distinct()
                .limit(20)
                .toList();
    }

    private void publishWorkspaceMemberEvent(UUID workspaceId, String eventType, Map<String, Object> payload) {
        webSocketService.sendToWorkspace(workspaceId.toString(), eventType, payload);
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }
}
