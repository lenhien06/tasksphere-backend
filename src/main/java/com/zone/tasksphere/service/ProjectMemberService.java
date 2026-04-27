package com.zone.tasksphere.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.zone.tasksphere.dto.request.DirectMemberRequest;
import com.zone.tasksphere.dto.request.InviteMemberRequest;
import com.zone.tasksphere.dto.request.UpdateRoleRequest;
import com.zone.tasksphere.dto.response.InviteMemberResponse;
import com.zone.tasksphere.dto.response.MemberSearchResponse;
import com.zone.tasksphere.dto.response.ProjectInviteResponse;
import com.zone.tasksphere.dto.response.ProjectMemberResponse;
import com.zone.tasksphere.dto.response.UserProfileResponse;
import com.zone.tasksphere.entity.Project;
import com.zone.tasksphere.entity.ProjectInvite;
import com.zone.tasksphere.entity.ProjectMember;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.ActionType;
import com.zone.tasksphere.entity.enums.EntityType;
import com.zone.tasksphere.entity.enums.InviteStatus;
import com.zone.tasksphere.entity.enums.NotificationType;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.entity.enums.UserStatus;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.exception.StructuredApiException;
import com.zone.tasksphere.repository.ProjectInviteRepository;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.utils.SkillTaxonomy;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectMemberService {
    private static final long INVITE_EXPIRES_HOURS = 72L;

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectInviteRepository projectInviteRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final com.zone.tasksphere.repository.TaskRepository taskRepository;
    private final ReportService reportService;
    private final WebSocketService webSocketService;
    private final UserProfileService userProfileService;

    @Value("${app.membership.max-members-per-project:50}")
    private int maxMembersPerProject;

    @Value("${app.membership.subscription-plan:FREE}")
    private String subscriptionPlan;

    // =========================================================================
    // 1. LẤY DANH SÁCH THÀNH VIÊN
    // =========================================================================
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getProjectMembers(UUID projectId) {
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);

        return members.stream()
                .map(member -> ProjectMemberResponse.builder()
                        .id(member.getId())
                        .projectRole(member.getProjectRole())
                        .joinedAt(member.getJoinedAt())
                        .skillTags(resolveEffectiveSkillTags(member))
                        .activeTaskCount(member.getActiveTaskCount())
                        .avgStoryPoints(member.getAvgStoryPoints())
                        .workCapacityHours(member.getUser() != null && member.getUser().getWorkCapacityHours() != null
                                ? member.getUser().getWorkCapacityHours()
                                : 40)
                        .user(member.getUser() == null ? null
                                : ProjectMemberResponse.UserInfo.builder()
                                        .id(member.getUser().getId())
                                        .fullName(member.getUser().getFullName())
                                        .email(member.getUser().getEmail())
                                        .avatarUrl(member.getUser().getAvatarUrl())
                                        .build())
                        .build())
                .filter(member -> member.getUser() != null)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getMemberProfile(UUID projectId, UUID targetUserId, UUID requesterId) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Dự án không tồn tại"));

        projectMemberRepository.findByProjectIdAndUserId(projectId, requesterId)
                .orElseThrow(() -> new NotFoundException("Bạn không phải thành viên của dự án"));

        projectMemberRepository.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Thành viên không tồn tại trong dự án"));

        return userProfileService.getProfile(targetUserId);
    }

    /**
     * LUỒNG 1: Thêm trực tiếp người dùng đã có trong hệ thống
     */
    @Transactional
    public ProjectMemberResponse addMemberDirectly(UUID projectId, DirectMemberRequest request, UUID actorId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Dự án không tồn tại"));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("Người dùng không tồn tại"));

        if (!user.getStatus().equals(UserStatus.ACTIVE)) {
            throw new BadRequestException("Người dùng hiện đang không hoạt động (INACTIVE).");
        }

        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
            throw new StructuredApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER",
                    "Người dùng này đã là thành viên của dự án.");
        }

        // Logic giới hạn gói dịch vụ có thể thêm ở đây (BR-12)

        ProjectMember newMember = restoreOrCreateMember(project, user, request.getRole(), actorId, null);
        reportService.invalidateOverviewCache(projectId);
        scheduleMembershipRealtimeBroadcast(project, "project_member_added", Map.of(
                "memberUserId", user.getId(),
                "memberRole", request.getRole().name()));

        // Ghi log hoạt động
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) RequestContextHolder
                .currentRequestAttributes()).getRequest();
        activityLogService.logActivity(projectId, actorId, EntityType.MEMBER, newMember.getId(),
                ActionType.CREATED, null, request.getRole().name(), httpServletRequest);

        // Thông báo cho user
        notificationService.createNotification(user, NotificationType.PROJECT_INVITED,
                "Bạn đã được thêm vào dự án",
                "Bạn đã được thêm trực tiếp vào dự án " + project.getName() + " với vai trò "
                        + request.getRole().getDisplayName(),
                EntityType.PROJECT.name(), projectId);

        // Gửi email thông báo (token=null → email "đã được thêm", không cần accept)
        User actor = userRepository.findById(actorId).orElse(null);
        String inviterName = actor != null ? actor.getFullName() : "Quản lý dự án";
        emailService.sendProjectInviteEmail(
                user.getEmail(),
                project.getName(),
                inviterName,
                request.getRole().name(),
                null,
                projectId);

        return ProjectMemberResponse.builder()
                .id(newMember.getId())
                .projectRole(newMember.getProjectRole())
                .joinedAt(newMember.getJoinedAt())
                .skillTags(resolveEffectiveSkillTags(newMember))
                .activeTaskCount(newMember.getActiveTaskCount())
                .avgStoryPoints(newMember.getAvgStoryPoints())
                .workCapacityHours(user.getWorkCapacityHours() != null ? user.getWorkCapacityHours() : 40)
                .user(ProjectMemberResponse.UserInfo.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .build();
    }

    /**
     * LUỒNG 2: Mời thành viên (Chuẩn SaaS B2B)
     */
    @Transactional
    public InviteMemberResponse inviteMember(UUID projectId, InviteMemberRequest request, UUID actorId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Dự án không tồn tại"));

        User actor = requireInviteManager(projectId, actorId);
        List<String> sanitizedSkills = sanitizeSkillTags(request.getSkillTags());

        if (request.getRole() != ProjectRole.PROJECT_MANAGER
                && request.getRole() != ProjectRole.MEMBER
                && request.getRole() != ProjectRole.VIEWER) {
            throw new BadRequestException("Role mời qua email chỉ được là PROJECT_MANAGER, MEMBER hoặc VIEWER");
        }

        String email = request.getEmail().trim().toLowerCase();

        Optional<User> inviteeOpt = userRepository.findByEmail(email);
        if (inviteeOpt.isPresent()) {
            if (projectMemberRepository.existsByProjectIdAndUserId(projectId, inviteeOpt.get().getId())) {
                throw new StructuredApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER",
                        "Người dùng đã là thành viên của dự án");
            }
        }

        // Nếu đã có invite pending cùng email trong dự án thì revoke token cũ trước khi
        // tạo token mới.
        Optional<ProjectInvite> pendingInvite = projectInviteRepository
                .findByProjectIdAndInviteeEmailAndStatus(projectId, email, InviteStatus.PENDING);
        if (pendingInvite.isPresent()) {
            ProjectInvite oldInvite = pendingInvite.get();
            oldInvite.setStatus(InviteStatus.REVOKED);
            projectInviteRepository.save(oldInvite);
        }

        String token = UUID.randomUUID().toString();
        ProjectInvite invite = ProjectInvite.builder()
                .project(project)
                .invitedBy(actor)
                .inviteeEmail(email)
                .token(token)
                .projectRole(request.getRole())
                .status(InviteStatus.PENDING)
                .expiresAt(Instant.now().plus(INVITE_EXPIRES_HOURS, ChronoUnit.HOURS))
                .inviteeUser(inviteeOpt.orElse(null))
                .skillTags(sanitizedSkills.isEmpty() ? null : sanitizedSkills)
                .build();
        invite = projectInviteRepository.save(invite);

        // Có tài khoản sẵn: gửi notification nội bộ + email.
        if (inviteeOpt.isPresent()) {
            User invitee = inviteeOpt.get();
            notificationService.createNotification(invitee, NotificationType.PROJECT_INVITED,
                    "Bạn nhận được lời mời vào dự án",
                    "Bạn được mời vào dự án " + project.getName() + " với vai trò "
                            + request.getRole().getDisplayName(),
                    EntityType.PROJECT.name(), projectId);
        }

        emailService.sendProjectInviteEmail(
                email,
                project.getName(),
                actor.getFullName(),
                request.getRole().name(),
                token,
                projectId);

        logActivity(projectId, actorId, EntityType.PROJECT, invite.getId(), ActionType.MEMBER_INVITED, null,
                "email=" + email + ",role=" + request.getRole().name());
        scheduleMembershipRealtimeBroadcast(project, "project_invites_updated", Map.of(
                "inviteId", invite.getId(),
                "inviteEmail", email,
                "inviteStatus", invite.getStatus().name()));

        return InviteMemberResponse.builder()
                .email(email)
                .role(request.getRole())
                .status("pending")
                .isNewUser(inviteeOpt.isEmpty())
                .build();
    }

    private List<String> resolveEffectiveSkillTags(ProjectMember member) {
        if (member.getSkillTags() != null && !member.getSkillTags().isEmpty()) {
            return member.getSkillTags();
        }
        if (member.getUser() != null && member.getUser().getSkillTags() != null) {
            return member.getUser().getSkillTags();
        }
        return Collections.emptyList();
    }

    private ProjectMember restoreOrCreateMember(Project project, User user, ProjectRole role, UUID invitedBy,
            List<String> skillTags) {
        Optional<ProjectMember> existingMember = projectMemberRepository.findAnyByProjectIdAndUserId(project.getId(),
                user.getId());

        if (existingMember.isPresent()) {
            ProjectMember member = existingMember.get();
            member.setProjectRole(role);
            member.setJoinedAt(Instant.now());
            member.setInvitedBy(invitedBy);
            member.setSkillTags(skillTags);
            member.setDeletedAt(null);
            member.setActiveTaskCount(0);
            return projectMemberRepository.save(member);
        }

        ProjectMember newMember = ProjectMember.builder()
                .project(project)
                .user(user)
                .projectRole(role)
                .joinedAt(Instant.now())
                .invitedBy(invitedBy)
                .skillTags(skillTags)
                .build();
        return projectMemberRepository.save(newMember);
    }

    /**
     * Xác thực Token lời mời (có thể ghi EXPIRED khi đã quá hạn).
     */
    @Transactional
    public ProjectInvite verifyInviteToken(String token) {
        ProjectInvite invite = projectInviteRepository.findByToken(token)
                .orElseThrow(() -> new StructuredApiException(HttpStatus.NOT_FOUND, "TOKEN_NOT_FOUND",
                        "Đường dẫn lời mời không tồn tại hoặc không hợp lệ."));

        if (invite.getStatus() == InviteStatus.REVOKED) {
            throw new StructuredApiException(HttpStatus.GONE, "TOKEN_EXPIRED_OR_REVOKED", "Lời mời đã bị hủy");
        }

        boolean timeExpired = invite.getExpiresAt().isBefore(Instant.now());
        if (invite.getStatus() == InviteStatus.EXPIRED
                || (invite.getStatus() == InviteStatus.PENDING && timeExpired)) {
            if (invite.getStatus() == InviteStatus.PENDING) {
                invite.setStatus(InviteStatus.EXPIRED);
                projectInviteRepository.save(invite);
            }
            throw new StructuredApiException(HttpStatus.GONE, "TOKEN_EXPIRED_OR_REVOKED", "Lời mời đã hết hạn");
        }

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new StructuredApiException(HttpStatus.GONE, "TOKEN_EXPIRED_OR_REVOKED",
                    "Lời mời không còn hiệu lực.");
        }

        return invite;
    }

    /**
     * Tự động join project sau khi đăng ký nếu có token
     */
    @Transactional
    public void acceptInviteAfterSignup(String token, User newUser) {
        ProjectInvite invite = projectInviteRepository.findByToken(token).orElse(null);
        if (invite == null || invite.getStatus() != InviteStatus.PENDING) {
            return; // Token không hợp lệ hoặc hết hạn thì bỏ qua, không chặn việc đăng ký
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            invite.setStatus(InviteStatus.EXPIRED);
            projectInviteRepository.save(invite);
            return;
        }

        // Kiểm tra email khớp
        if (!newUser.getEmail().equalsIgnoreCase(invite.getInviteeEmail())) {
            return;
        }

        ProjectMember newMember = restoreOrCreateMember(
                invite.getProject(),
                newUser,
                invite.getProjectRole(),
                invite.getInvitedBy().getId(),
                invite.getSkillTags());
        reportService.invalidateOverviewCache(invite.getProject().getId());

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setAcceptedAt(Instant.now());
        invite.setInviteeUser(newUser);
        projectInviteRepository.save(invite);
        logActivity(invite.getProject().getId(), newUser.getId(), EntityType.MEMBER, newMember.getId(),
                ActionType.MEMBER_JOINED, null, invite.getProjectRole().name());
        publishInviteAcceptedNotification(invite, newUser);
        scheduleMembershipRealtimeBroadcast(invite.getProject(), "project_member_joined", Map.of(
                "memberUserId", newUser.getId(),
                "memberRole", invite.getProjectRole().name(),
                "inviteId", invite.getId()));

        log.info("User {} tự động gia nhập dự án {} sau khi đăng ký.", newUser.getEmail(),
                invite.getProject().getName());
    }

    /**
     * Lấy danh sách lời mời theo trạng thái (có phân trang).
     * Chỉ PROJECT_MANAGER của dự án mới được gọi.
     * Tự động mark EXPIRED trước khi query (chỉ khi lọc PENDING).
     */
    @Transactional
    public Page<ProjectInviteResponse> getInvitesByStatus(UUID projectId, UUID actorId, InviteStatus status,
            Pageable pageable) {
        requireInviteManager(projectId, actorId);

        // Auto-expire: cập nhật các invite PENDING đã quá hạn trước khi query
        if (status == InviteStatus.PENDING) {
            projectInviteRepository.markExpiredInvites(
                    projectId, Instant.now(), InviteStatus.PENDING, InviteStatus.EXPIRED);
        }

        return projectInviteRepository
                .findByProjectIdAndStatus(projectId, status, pageable)
                .map(inv -> {
                    Long daysLeft = null;
                    if (inv.getStatus() == InviteStatus.PENDING) {
                        daysLeft = Math.max(0, ChronoUnit.DAYS.between(Instant.now(), inv.getExpiresAt()));
                    }
                    return ProjectInviteResponse.builder()
                            .id(inv.getId())
                            .email(inv.getInviteeEmail())
                            .role(inv.getProjectRole())
                            .status(inv.getStatus())
                            .inviterName(inv.getInvitedBy().getFullName())
                            .invitedAt(inv.getCreatedAt())
                            .expiresAt(inv.getExpiresAt())
                            .daysLeft(daysLeft)
                            .build();
                });
    }

    @Transactional
    public void revokeInvite(UUID projectId, UUID inviteId, UUID actorId) {
        requireInviteManager(projectId, actorId);
        ProjectInvite invite = projectInviteRepository.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lời mời này."));

        if (!invite.getProject().getId().equals(projectId)) {
            throw new BadRequestException("Lời mời này không thuộc dự án.");
        }
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BadRequestException("Lời mời này không còn ở trạng thái chờ (PENDING).");
        }

        invite.setStatus(InviteStatus.REVOKED);
        projectInviteRepository.save(invite);
        logActivity(projectId, actorId, EntityType.PROJECT, inviteId, ActionType.UPDATED, "PENDING", "REVOKED");
        scheduleMembershipRealtimeBroadcast(invite.getProject(), "project_invites_updated", Map.of(
                "inviteId", inviteId,
                "inviteStatus", invite.getStatus().name()));
    }

    @Transactional
    public void resendInvite(UUID projectId, UUID inviteId, UUID actorId) {
        User actor = requireInviteManager(projectId, actorId);

        ProjectInvite invite = projectInviteRepository.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lời mời này."));

        if (!invite.getProject().getId().equals(projectId)) {
            throw new BadRequestException("Lời mời này không thuộc dự án.");
        }
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new StructuredApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVITE_NOT_RESENDABLE",
                    "Chỉ có thể gửi lại lời mời đang ở trạng thái PENDING");
        }

        // Reset token và gia hạn 72 giờ
        invite.setToken(UUID.randomUUID().toString());
        invite.setExpiresAt(Instant.now().plus(INVITE_EXPIRES_HOURS, ChronoUnit.HOURS));
        projectInviteRepository.save(invite);
        scheduleMembershipRealtimeBroadcast(invite.getProject(), "project_invites_updated", Map.of(
                "inviteId", invite.getId(),
                "inviteStatus", invite.getStatus().name()));

        emailService.sendProjectInviteEmail(
                invite.getInviteeEmail(),
                invite.getProject().getName(),
                actor.getFullName(),
                invite.getProjectRole().name(),
                invite.getToken(),
                invite.getProject().getId());
    }

    @Transactional(readOnly = true)
    public List<ProjectInviteResponse> getMyInvites(String userEmail) {
        return projectInviteRepository.findByInviteeEmailAndStatus(userEmail.toLowerCase(), InviteStatus.PENDING)
                .stream()
                .map(inv -> {
                    long daysLeft = Math.max(0, ChronoUnit.DAYS.between(Instant.now(), inv.getExpiresAt()));
                    return ProjectInviteResponse.builder()
                            .id(inv.getId())
                            .projectId(inv.getProject().getId())
                            .projectName(inv.getProject().getName())
                            .inviterName(inv.getInvitedBy().getFullName())
                            .role(inv.getProjectRole())
                            .status(inv.getStatus())
                            .invitedAt(inv.getCreatedAt())
                            .daysLeft(daysLeft)
                            .token(inv.getToken())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void declineInvite(String token, UUID currentUserId) {
        ProjectInvite invite = projectInviteRepository.findByToken(token)
                .orElseThrow(() -> new StructuredApiException(HttpStatus.NOT_FOUND, "TOKEN_NOT_FOUND",
                        "Đường dẫn lời mời không tồn tại hoặc không hợp lệ."));

        if (invite.getStatus() == InviteStatus.ACCEPTED) {
            throw new StructuredApiException(HttpStatus.CONFLICT, "ALREADY_ACCEPTED",
                    "Lời mời đã được chấp nhận trước đó");
        }

        if (invite.getStatus() == InviteStatus.REVOKED
                || invite.getStatus() == InviteStatus.EXPIRED
                || invite.getStatus() == InviteStatus.DECLINED) {
            throw new StructuredApiException(HttpStatus.GONE, "TOKEN_EXPIRED_OR_REVOKED",
                    "Lời mời không còn hiệu lực.");
        }

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new StructuredApiException(HttpStatus.GONE, "TOKEN_EXPIRED_OR_REVOKED",
                    "Lời mời không còn hiệu lực.");
        }

        if (invite.getExpiresAt().isBefore(Instant.now())) {
            invite.setStatus(InviteStatus.EXPIRED);
            projectInviteRepository.save(invite);
            throw new StructuredApiException(HttpStatus.GONE, "TOKEN_EXPIRED_OR_REVOKED", "Lời mời đã hết hạn");
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Tài khoản không tồn tại."));

        if (!currentUser.getEmail().equalsIgnoreCase(invite.getInviteeEmail())) {
            throw new StructuredApiException(HttpStatus.FORBIDDEN, "EMAIL_MISMATCH",
                    "Tài khoản đang đăng nhập không khớp với email được mời.");
        }

        invite.setStatus(InviteStatus.DECLINED);
        projectInviteRepository.save(invite);

        logActivity(invite.getProject().getId(), currentUserId, EntityType.PROJECT, invite.getId(),
                ActionType.INVITE_DECLINED, "PENDING", "DECLINED");
        scheduleMembershipRealtimeBroadcast(invite.getProject(), "project_invites_updated", Map.of(
                "inviteId", invite.getId(),
                "inviteStatus", invite.getStatus().name()));
    }

    // =========================================================================
    // 3. ĐỔI ROLE THÀNH VIÊN
    // =========================================================================
    @Transactional
    public void changeMemberRole(UUID projectId, UUID targetUserId, UpdateRoleRequest request, UUID actorId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Dự án không tồn tại"));

        ProjectMember targetMember = projectMemberRepository.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Thành viên không nằm trong dự án này"));

        if (project.getOwner() != null && project.getOwner().getId().equals(targetUserId)) {
            throw new BadRequestException("Không thể thay đổi vai trò của Chủ sở hữu (Owner) dự án.");
        }

        if (actorId.equals(targetUserId) && request.getRole() != ProjectRole.PROJECT_MANAGER) {
            throw new BadRequestException("Bạn không thể tự hạ quyền của chính mình.");
        }

        ProjectRole oldRole = targetMember.getProjectRole();
        targetMember.setProjectRole(request.getRole());
        projectMemberRepository.save(targetMember);
        scheduleMembershipRealtimeBroadcast(project, "project_member_role_updated", Map.of(
                "memberUserId", targetUserId,
                "oldRole", oldRole.name(),
                "newRole", request.getRole().name()));

        // activityLogService.log(actorId, "PROJECT_MEMBER", targetMember.getId(),
        // "CHANGED_ROLE", oldRole.name(), request.getRole().name());
    }

    // =========================================================================
    // 4. XÓA THÀNH VIÊN (Kick)
    // =========================================================================
    @Transactional
    public void removeMember(UUID projectId, UUID targetUserId, UUID actorId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Dự án không tồn tại"));

        ProjectMember targetMember = projectMemberRepository.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Thành viên không nằm trong dự án này"));

        if (project.getOwner() != null && project.getOwner().getId().equals(targetUserId)) {
            throw new StructuredApiException(HttpStatus.FORBIDDEN, "CANNOT_REMOVE_OWNER",
                    "Không thể xóa Chủ sở hữu (Owner) ra khỏi dự án.");
        }

        // Kiểm tra nếu đây là PROJECT_MANAGER duy nhất
        if (targetMember.getProjectRole() == ProjectRole.PROJECT_MANAGER) {
            long pmCount = projectMemberRepository.countByProjectIdAndProjectRole(projectId,
                    ProjectRole.PROJECT_MANAGER);
            if (pmCount <= 1) {
                throw new BadRequestException(
                        "Không thể xóa Project Manager duy nhất của dự án. Hãy chỉ định người khác trước.");
            }
        }

        // Soft delete
        targetMember.setDeletedAt(Instant.now());
        projectMemberRepository.save(targetMember);
        reportService.invalidateOverviewCache(projectId);
        scheduleMembershipRealtimeBroadcast(project, "project_member_removed", Map.of(
                "memberUserId", targetUserId));

        // FIX: FR-12 - Reassign về unassigned tất cả task của member này trong project
        taskRepository.unassignTasksByUserInProject(projectId, targetUserId);

        log.info("Member {} removed from project {}, tasks unassigned", targetUserId, projectId);
    }

    // =========================================================================
    // 5. TỰ RỜI DỰ ÁN (Leave)
    // =========================================================================
    @Transactional
    public void leaveMember(UUID projectId, UUID actorId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Dự án không tồn tại"));

        if (project.getOwner() != null && project.getOwner().getId().equals(actorId)) {
            throw new StructuredApiException(HttpStatus.FORBIDDEN, "OWNER_CANNOT_LEAVE",
                    "Owner không thể rời dự án. Hãy chuyển quyền sở hữu cho thành viên khác trước.");
        }

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, actorId)
                .orElseThrow(() -> new NotFoundException("Bạn không phải thành viên của dự án này."));

        // Kiểm tra nếu là PROJECT_MANAGER duy nhất
        if (member.getProjectRole() == ProjectRole.PROJECT_MANAGER) {
            long pmCount = projectMemberRepository.countByProjectIdAndProjectRole(projectId,
                    ProjectRole.PROJECT_MANAGER);
            if (pmCount <= 1) {
                throw new BadRequestException(
                        "Bạn là Project Manager duy nhất. Hãy chỉ định người khác trước khi rời dự án.");
            }
        }

        // Soft delete
        member.setDeletedAt(Instant.now());
        projectMemberRepository.save(member);
        reportService.invalidateOverviewCache(projectId);
        scheduleMembershipRealtimeBroadcast(project, "project_member_left", Map.of(
                "memberUserId", actorId));

        taskRepository.unassignTasksByUserInProject(projectId, actorId);
        logActivity(projectId, actorId, EntityType.MEMBER, member.getId(), ActionType.MEMBER_LEFT, null, null);
    }

    // =========================================================================
    // 6. TÌM KIẾM THÀNH VIÊN ĐỂ @MENTION
    // =========================================================================
    @Transactional(readOnly = true)
    public List<MemberSearchResponse> searchMembers(UUID projectId, String q, UUID currentUserId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));

        boolean isOwner = project.getOwner() != null && project.getOwner().getId().equals(currentUserId);
        if (!isOwner && !projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUserId)) {
            throw new com.zone.tasksphere.exception.Forbidden("Bạn không phải thành viên dự án này");
        }

        String keyword = q == null ? "" : q.trim().toLowerCase();

        List<MemberSearchResponse> members = projectMemberRepository.findByProjectId(projectId).stream()
                .filter(member -> member.getUser() != null)
                .filter(member -> matchesMemberSearch(member.getUser(), keyword))
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        Optional.ofNullable(a.getUser().getFullName()).orElse(""),
                        Optional.ofNullable(b.getUser().getFullName()).orElse("")
                ))
                .map(member -> MemberSearchResponse.builder()
                        .id(member.getUser().getId())
                        .fullName(member.getUser().getFullName())
                        .email(member.getUser().getEmail())
                        .avatarUrl(member.getUser().getAvatarUrl())
                        .projectRole(member.getProjectRole())
                        .build())
                .collect(Collectors.toCollection(ArrayList::new));

        User owner = project.getOwner();
        if (owner != null
                && members.stream().noneMatch(member -> owner.getId().equals(member.getId()))
                && matchesMemberSearch(owner, keyword)) {
            members.add(MemberSearchResponse.builder()
                    .id(owner.getId())
                    .fullName(owner.getFullName())
                    .email(owner.getEmail())
                    .avatarUrl(owner.getAvatarUrl())
                    .projectRole(ProjectRole.PROJECT_MANAGER)
                    .build());
        }

        return members.stream()
                .sorted((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                        Optional.ofNullable(a.getFullName()).orElse(""),
                        Optional.ofNullable(b.getFullName()).orElse("")
                ))
                .limit(10)
                .toList();
    }

    private boolean matchesMemberSearch(User user, String q) {
        if (q == null || q.isEmpty()) {
            return true;
        }

        String fullName = Optional.ofNullable(user.getFullName()).orElse("").toLowerCase();
        String email = Optional.ofNullable(user.getEmail()).orElse("").toLowerCase();
        return fullName.contains(q) || email.contains(q);
    }

    // =========================================================================
    // 5. CHẤP NHẬN LỜI MỜI (Dành cho người được mời click từ Email)
    // =========================================================================
    @Transactional
    public UUID acceptInvite(String token, UUID currentUserId) {
        ProjectInvite invite = projectInviteRepository.findByToken(token)
                .orElseThrow(() -> new StructuredApiException(HttpStatus.NOT_FOUND, "TOKEN_NOT_FOUND",
                        "Đường dẫn lời mời không tồn tại hoặc không hợp lệ."));

        if (invite.getStatus() == InviteStatus.REVOKED) {
            throw new StructuredApiException(HttpStatus.GONE, "TOKEN_EXPIRED_OR_REVOKED", "Lời mời đã bị hủy");
        }

        if (invite.getStatus() == InviteStatus.ACCEPTED) {
            throw new StructuredApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER",
                    "Lời mời đã được chấp nhận trước đó.");
        }

        boolean timeExpired = invite.getExpiresAt().isBefore(Instant.now());
        if (invite.getStatus() == InviteStatus.EXPIRED
                || (invite.getStatus() == InviteStatus.PENDING && timeExpired)) {
            if (invite.getStatus() == InviteStatus.PENDING) {
                invite.setStatus(InviteStatus.EXPIRED);
                projectInviteRepository.save(invite);
            }
            throw new StructuredApiException(HttpStatus.GONE, "TOKEN_EXPIRED_OR_REVOKED", "Lời mời đã hết hạn");
        }

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new StructuredApiException(HttpStatus.GONE, "TOKEN_EXPIRED_OR_REVOKED",
                    "Lời mời không còn hiệu lực.");
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Tài khoản không tồn tại."));

        if (!currentUser.getEmail().equalsIgnoreCase(invite.getInviteeEmail())) {
            throw new StructuredApiException(HttpStatus.BAD_REQUEST, "EMAIL_MISMATCH",
                    String.format(
                            "Tài khoản đang đăng nhập không khớp với email được mời. Vui lòng đăng nhập bằng %s.",
                            invite.getInviteeEmail()));
        }

        if (projectMemberRepository.existsByProjectIdAndUserId(invite.getProject().getId(), currentUserId)) {
            throw new StructuredApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER",
                    "Bạn đã là thành viên của dự án này rồi.");
        }

        ProjectMember newMember = restoreOrCreateMember(
                invite.getProject(),
                currentUser,
                invite.getProjectRole(),
                invite.getInvitedBy().getId(),
                invite.getSkillTags());
        reportService.invalidateOverviewCache(invite.getProject().getId());

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setAcceptedAt(Instant.now());
        invite.setInviteeUser(currentUser);
        projectInviteRepository.save(invite);
        logActivity(invite.getProject().getId(), currentUserId, EntityType.MEMBER, newMember.getId(),
                ActionType.MEMBER_JOINED, null, invite.getProjectRole().name());
        publishInviteAcceptedNotification(invite, currentUser);
        scheduleMembershipRealtimeBroadcast(invite.getProject(), "project_member_joined", Map.of(
                "memberUserId", currentUserId,
                "memberRole", invite.getProjectRole().name(),
                "inviteId", invite.getId()));

        return invite.getProject().getId();
    }

    private User requireInviteManager(UUID projectId, UUID actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("User thao tác không tồn tại"));

        if (actor.getSystemRole() == SystemRole.ADMIN) {
            return actor;
        }

        ProjectMember actorMember = projectMemberRepository.findByProjectIdAndUserId(projectId, actorId)
                .orElseThrow(() -> new StructuredApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Chỉ PM hoặc Admin mới có quyền thao tác lời mời."));
        if (actorMember.getProjectRole() != ProjectRole.PROJECT_MANAGER) {
            throw new StructuredApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Chỉ PM hoặc Admin mới có quyền thao tác lời mời.");
        }
        return actor;
    }

    private List<String> sanitizeSkillTags(List<String> skillTags) {
        if (skillTags == null) {
            return Collections.emptyList();
        }
        return SkillTaxonomy.canonicalizeSkillTags(skillTags).stream()
                .map(tag -> tag == null ? "" : tag.trim())
                .filter(tag -> !tag.isBlank())
                .distinct()
                .limit(20)
                .toList();
    }

    private void ensureMemberLimitNotExceeded(UUID projectId) {
        if (maxMembersPerProject <= 0) {
            return;
        }
        long activeMembers = projectMemberRepository.countByProjectId(projectId);
        long pendingInvites = projectInviteRepository.findByProjectIdAndStatus(projectId, InviteStatus.PENDING).size();
        if (activeMembers + pendingInvites >= maxMembersPerProject) {
            throw new StructuredApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "MEMBER_LIMIT_EXCEEDED",
                    "Đã đạt giới hạn thành viên theo gói dịch vụ hiện tại",
                    Map.of(
                            "currentCount", activeMembers + pendingInvites,
                            "limit", maxMembersPerProject,
                            "plan", subscriptionPlan));
        }
    }

    private void logActivity(UUID projectId, UUID actorId, EntityType entityType, UUID entityId,
            ActionType actionType, String oldValue, String newValue) {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                    .getRequest();
            activityLogService.logActivity(projectId, actorId, entityType, entityId, actionType, oldValue, newValue,
                    request);
        } catch (Exception e) {
            log.warn("Không thể ghi activity log {} cho entity {}: {}", actionType, entityId, e.getMessage());
        }
    }

    private void publishInviteAcceptedNotification(ProjectInvite invite, User joinedUser) {
        User inviter = invite.getInvitedBy();
        if (inviter == null || joinedUser == null || inviter.getId().equals(joinedUser.getId())) {
            return;
        }

        notificationService.createNotification(
                inviter,
                NotificationType.MEMBER_JOINED,
                "Thành viên đã tham gia dự án",
                String.format("%s đã chấp nhận lời mời và tham gia dự án %s với vai trò %s.",
                        joinedUser.getFullName(),
                        invite.getProject().getName(),
                        invite.getProjectRole().getDisplayName()),
                EntityType.PROJECT.name(),
                invite.getProject().getId());
    }

    private void scheduleMembershipRealtimeBroadcast(Project project, String eventType, Map<String, Object> extraData) {
        UUID projectId = project.getId();
        UUID workspaceId = project.getWorkspace() != null ? project.getWorkspace().getId() : null;

        Map<String, Object> payload = new HashMap<>();
        payload.put("projectId", projectId);
        if (workspaceId != null) {
            payload.put("workspaceId", workspaceId);
        }
        if (extraData != null && !extraData.isEmpty()) {
            payload.putAll(extraData);
        }

        Runnable publish = () -> {
            webSocketService.sendToProject(projectId.toString(), eventType, payload);
            webSocketService.sendToProject(projectId.toString(), "project_members_updated", payload);
            webSocketService.sendToProject(projectId.toString(), "project_invites_updated", payload);
            webSocketService.sendToProjects(eventType, payload);
            webSocketService.sendToProjects("project_members_updated", payload);
            if (workspaceId != null) {
                webSocketService.sendToWorkspace(workspaceId.toString(), eventType, payload);
            }
        };

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }

        publish.run();
    }
}
