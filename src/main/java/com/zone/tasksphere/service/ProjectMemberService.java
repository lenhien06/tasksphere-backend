package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.DirectMemberRequest;
import com.zone.tasksphere.dto.request.InviteMemberRequest;
import com.zone.tasksphere.dto.request.UpdateRoleRequest;
import com.zone.tasksphere.dto.response.InviteMemberResponse;
import com.zone.tasksphere.dto.response.MemberSearchResponse;
import com.zone.tasksphere.dto.response.ProjectInviteResponse;
import com.zone.tasksphere.dto.response.ProjectMemberResponse;
import com.zone.tasksphere.entity.*;
import com.zone.tasksphere.entity.enums.*;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.ConflictException;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.*;
import com.zone.tasksphere.exception.Forbidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectMemberService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectInviteRepository projectInviteRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final ActivityLogService activityLogService;
    private final com.zone.tasksphere.repository.TaskRepository taskRepository;
    private final ReportService reportService;

    // =========================================================================
    // 1. LẤY DANH SÁCH THÀNH VIÊN
    // =========================================================================
    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getProjectMembers(UUID projectId) {
        List<ProjectMember> members = projectMemberRepository.findByProjectId(projectId);

        return members.stream().map(member -> ProjectMemberResponse.builder()
                .id(member.getId())
                .projectRole(member.getProjectRole())
                .joinedAt(member.getJoinedAt())
                .user(ProjectMemberResponse.UserInfo.builder()
                        .id(member.getUser().getId())
                        .fullName(member.getUser().getFullName())
                        .email(member.getUser().getEmail())
                        .avatarUrl(member.getUser().getAvatarUrl())
                        .build())
                .build()
        ).collect(Collectors.toList());
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
            throw new ConflictException("Người dùng này đã là thành viên của dự án.");
        }

        // Logic giới hạn gói dịch vụ có thể thêm ở đây (BR-12)

        ProjectMember newMember = ProjectMember.builder()
                .project(project)
                .user(user)
                .projectRole(request.getRole())
                .joinedAt(Instant.now())
                .invitedBy(actorId)
                .build();

        projectMemberRepository.save(newMember);
        reportService.invalidateOverviewCache(projectId);

        // Ghi log hoạt động
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        activityLogService.logActivity(projectId, actorId, EntityType.MEMBER, newMember.getId(), 
                ActionType.CREATED, null, request.getRole().name(), httpServletRequest);

        // Thông báo cho user
        notificationService.createNotification(user, NotificationType.PROJECT_INVITED, 
                "Bạn đã được thêm vào dự án", 
                "Bạn đã được thêm trực tiếp vào dự án " + project.getName() + " với vai trò " + request.getRole().getDisplayName(),
                EntityType.PROJECT.name(), projectId);

        return ProjectMemberResponse.builder()
                .id(newMember.getId())
                .projectRole(newMember.getProjectRole())
                .joinedAt(newMember.getJoinedAt())
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

        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("User thao tác không tồn tại"));

        String email = request.getEmail().trim().toLowerCase();

        // Bước 2: Kiểm tra trùng lặp (ACTIVE hoặc PENDING)
        // Kiểm tra ACTIVE (đã là thành viên)
        Optional<User> inviteeOpt = userRepository.findByEmail(email);
        if (inviteeOpt.isPresent()) {
            if (projectMemberRepository.existsByProjectIdAndUserId(projectId, inviteeOpt.get().getId())) {
                throw new BadRequestException("Email này đã là thành viên hoặc đang có lời mời chờ xác nhận.");
            }
        }

        // Kiểm tra PENDING (đã được mời nhưng chưa chấp nhận)
        if (projectInviteRepository.findByProjectIdAndInviteeEmailAndStatus(projectId, email, InviteStatus.PENDING).isPresent()) {
            throw new BadRequestException("Email này đã là thành viên hoặc đang có lời mời chờ xác nhận.");
        }

        // Bước 3: Rẽ nhánh Logic
        if (inviteeOpt.isPresent()) {
            // Trường hợp A: Email ĐÃ CÓ tài khoản
            User invitee = inviteeOpt.get();

            ProjectMember newMember = ProjectMember.builder()
                    .project(project)
                    .user(invitee)
                    .projectRole(request.getRole())
                    .joinedAt(Instant.now())
                    .invitedBy(actorId)
                    .build();

            projectMemberRepository.save(newMember);
            reportService.invalidateOverviewCache(projectId);

            // Ghi log hoạt động
            HttpServletRequest httpServletRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            activityLogService.logActivity(projectId, actorId, EntityType.MEMBER, newMember.getId(), 
                    ActionType.CREATED, null, request.getRole().name(), httpServletRequest);

            // Thông báo cho user
            notificationService.createNotification(invitee, NotificationType.PROJECT_INVITED, 
                    "Bạn đã được thêm vào dự án", 
                    "Bạn đã được thêm trực tiếp vào dự án " + project.getName() + " với vai trò " + request.getRole().getDisplayName(),
                    EntityType.PROJECT.name(), projectId);

            // Gửi email thông báo (token=null → link đến trang dự án)
            emailService.sendProjectInviteEmail(email, project.getName(), actor.getFullName(), null, projectId);

            return InviteMemberResponse.builder()
                    .email(email)
                    .role(request.getRole())
                    .status("active")
                    .isNewUser(false)
                    .build();

        } else {
            // Trường hợp B: Email CHƯA CÓ tài khoản
            String token = UUID.randomUUID().toString();
            ProjectInvite invite = ProjectInvite.builder()
                    .project(project)
                    .invitedBy(actor)
                    .inviteeEmail(email)
                    .token(token)
                    .projectRole(request.getRole())
                    .status(InviteStatus.PENDING)
                    .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS)) // Đặt 7 ngày theo spec mới
                    .build();

            projectInviteRepository.save(invite);

            // Bắn Email Lời Mời (token != null → link đến trang /invite?token=)
            emailService.sendProjectInviteEmail(email, project.getName(), actor.getFullName(), token, projectId);

            // Ghi log
            HttpServletRequest httpServletRequest = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            activityLogService.logActivity(projectId, actorId, EntityType.PROJECT, projectId, 
                    ActionType.UPDATED, "INVITED_MEMBER", email, httpServletRequest);

            return InviteMemberResponse.builder()
                    .email(email)
                    .role(request.getRole())
                    .status("pending")
                    .isNewUser(true)
                    .build();
        }
    }

    /**
     * Xác thực Token lời mời
     */
    @Transactional(readOnly = true)
    public ProjectInvite verifyInviteToken(String token) {
        ProjectInvite invite = projectInviteRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Đường dẫn lời mời không tồn tại hoặc không hợp lệ."));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BadRequestException("Lời mời này đã được xử lý hoặc bị thu hồi.");
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Lời mời này đã hết hạn.");
        }
        return invite;
    }

    /**
     * Tự động join project sau khi đăng ký nếu có token
     */
    @Transactional
    public void acceptInviteAfterSignup(String token, User newUser) {
        ProjectInvite invite = projectInviteRepository.findByToken(token).orElse(null);
        if (invite == null || invite.getStatus() != InviteStatus.PENDING || invite.getExpiresAt().isBefore(Instant.now())) {
            return; // Token không hợp lệ hoặc hết hạn thì bỏ qua, không chặn việc đăng ký
        }

        // Kiểm tra email khớp
        if (!newUser.getEmail().equalsIgnoreCase(invite.getInviteeEmail())) {
            return;
        }

        ProjectMember newMember = ProjectMember.builder()
                .project(invite.getProject())
                .user(newUser)
                .projectRole(invite.getProjectRole())
                .joinedAt(Instant.now())
                .invitedBy(invite.getInvitedBy().getId())
                .build();
        projectMemberRepository.save(newMember);
        reportService.invalidateOverviewCache(invite.getProject().getId());

        invite.setStatus(InviteStatus.ACCEPTED);
        invite.setAcceptedAt(Instant.now());
        invite.setInviteeUser(newUser);
        projectInviteRepository.save(invite);

        log.info("User {} tự động gia nhập dự án {} sau khi đăng ký.", newUser.getEmail(), invite.getProject().getName());
    }


    /**
     * Lấy danh sách lời mời theo trạng thái (có phân trang).
     * Chỉ PROJECT_MANAGER của dự án mới được gọi.
     * Tự động mark EXPIRED trước khi query (chỉ khi lọc PENDING).
     */
    @Transactional
    public Page<ProjectInviteResponse> getInvitesByStatus(UUID projectId, UUID actorId, InviteStatus status, Pageable pageable) {
        // Permission check
        ProjectMember actor = projectMemberRepository.findByProjectIdAndUserId(projectId, actorId)
                .orElseThrow(() -> new Forbidden("Bạn không có quyền xem danh sách lời mời của dự án này."));
        if (!actor.getProjectRole().canManageProject()) {
            throw new Forbidden("Bạn không có quyền xem danh sách lời mời của dự án này.");
        }

        // Auto-expire: cập nhật các invite PENDING đã quá hạn trước khi query
        if (status == InviteStatus.PENDING) {
            projectInviteRepository.markExpiredInvites(
                    projectId, Instant.now(), InviteStatus.PENDING, InviteStatus.EXPIRED
            );
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
    public void revokeInvite(UUID projectId, UUID inviteId) {
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
    }

    @Transactional
    public void resendInvite(UUID projectId, UUID inviteId, UUID actorId) {
        User actor = userRepository.findById(actorId)
                .orElseThrow(() -> new NotFoundException("User thao tác không tồn tại"));

        ProjectInvite invite = projectInviteRepository.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lời mời này."));

        if (!invite.getProject().getId().equals(projectId)) {
            throw new BadRequestException("Lời mời này không thuộc dự án.");
        }
        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể gửi lại lời mời đang ở trạng thái PENDING.");
        }

        // Reset token và gia hạn 7 ngày
        invite.setToken(UUID.randomUUID().toString());
        invite.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        projectInviteRepository.save(invite);

        emailService.sendProjectInviteEmail(
                invite.getInviteeEmail(),
                invite.getProject().getName(),
                actor.getFullName(),
                invite.getToken(),
                invite.getProject().getId()
        );
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
                .orElseThrow(() -> new NotFoundException("Đường dẫn lời mời không tồn tại hoặc không hợp lệ."));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BadRequestException("Lời mời này đã được xử lý hoặc bị thu hồi.");
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            invite.setStatus(InviteStatus.EXPIRED);
            projectInviteRepository.save(invite);
            throw new BadRequestException("Lời mời này đã hết hạn.");
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Tài khoản không tồn tại."));

        if (!currentUser.getEmail().equalsIgnoreCase(invite.getInviteeEmail())) {
            throw new BadRequestException("Email của bạn không khớp với email được mời.");
        }

        invite.setStatus(InviteStatus.DECLINED);
        projectInviteRepository.save(invite);
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

        if (project.getOwner().getId().equals(targetUserId)) {
            throw new BadRequestException("Không thể thay đổi vai trò của Chủ sở hữu (Owner) dự án.");
        }

        if (actorId.equals(targetUserId) && request.getRole() != ProjectRole.PROJECT_MANAGER) {
            throw new BadRequestException("Bạn không thể tự hạ quyền của chính mình.");
        }

        ProjectRole oldRole = targetMember.getProjectRole();
        targetMember.setProjectRole(request.getRole());
        projectMemberRepository.save(targetMember);

        // activityLogService.log(actorId, "PROJECT_MEMBER", targetMember.getId(), "CHANGED_ROLE", oldRole.name(), request.getRole().name());
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

        if (project.getOwner().getId().equals(targetUserId)) {
            throw new BadRequestException("Không thể xóa Chủ sở hữu (Owner) ra khỏi dự án.");
        }

        // Kiểm tra nếu đây là PROJECT_MANAGER duy nhất
        if (targetMember.getProjectRole() == ProjectRole.PROJECT_MANAGER) {
            long pmCount = projectMemberRepository.countByProjectIdAndProjectRole(projectId, ProjectRole.PROJECT_MANAGER);
            if (pmCount <= 1) {
                throw new BadRequestException("Không thể xóa Project Manager duy nhất của dự án. Hãy chỉ định người khác trước.");
            }
        }

        // Soft delete
        targetMember.setDeletedAt(Instant.now());
        projectMemberRepository.save(targetMember);
        reportService.invalidateOverviewCache(projectId);

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

        if (project.getOwner().getId().equals(actorId)) {
            throw new BadRequestException("Chủ sở hữu (Owner) không thể rời dự án. Hãy chuyển quyền sở hữu trước.");
        }

        ProjectMember member = projectMemberRepository.findByProjectIdAndUserId(projectId, actorId)
                .orElseThrow(() -> new NotFoundException("Bạn không phải thành viên của dự án này."));

        // Kiểm tra nếu là PROJECT_MANAGER duy nhất
        if (member.getProjectRole() == ProjectRole.PROJECT_MANAGER) {
            long pmCount = projectMemberRepository.countByProjectIdAndProjectRole(projectId, ProjectRole.PROJECT_MANAGER);
            if (pmCount <= 1) {
                throw new BadRequestException("Bạn là Project Manager duy nhất. Hãy chỉ định người khác trước khi rời dự án.");
            }
        }

        // Soft delete
        member.setDeletedAt(Instant.now());
        projectMemberRepository.save(member);
        reportService.invalidateOverviewCache(projectId);
    }

    // =========================================================================
    // 6. TÌM KIẾM THÀNH VIÊN ĐỂ @MENTION
    // =========================================================================
    @Transactional(readOnly = true)
    public List<MemberSearchResponse> searchMembers(UUID projectId, String q, UUID currentUserId) {
        // Validate membership
        if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, currentUserId)) {
            throw new com.zone.tasksphere.exception.Forbidden("Bạn không phải thành viên dự án này");
        }

        List<User> users = userRepository.searchProjectMembers(projectId, q);

        return users.stream().map(u -> {
            ProjectRole role = projectMemberRepository.findByProjectIdAndUserId(projectId, u.getId())
                .map(ProjectMember::getProjectRole)
                .orElse(ProjectRole.MEMBER);
            return MemberSearchResponse.builder()
                .id(u.getId())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .avatarUrl(u.getAvatarUrl())
                .projectRole(role)
                .build();
        }).toList();
    }

    // =========================================================================
    // 5. CHẤP NHẬN LỜI MỜI (Dành cho người được mời click từ Email)
    // =========================================================================
    @Transactional
    public UUID acceptInvite(String token, UUID currentUserId) {
        ProjectInvite invite = projectInviteRepository.findByToken(token)
                .orElseThrow(() -> new NotFoundException("Đường dẫn lời mời không tồn tại hoặc không hợp lệ."));

        if (invite.getStatus() != InviteStatus.PENDING) {
            throw new BadRequestException("Lời mời này đã được xử lý hoặc bị thu hồi.");
        }
        if (invite.getExpiresAt().isBefore(Instant.now())) {
            invite.setStatus(InviteStatus.EXPIRED);
            throw new BadRequestException("Lời mời này đã hết hạn (quá 7 ngày).");
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Tài khoản không tồn tại."));

        if (!currentUser.getEmail().equalsIgnoreCase(invite.getInviteeEmail())) {
            throw new BadRequestException("Email của bạn không khớp với email được mời.");
        }

        if (projectMemberRepository.existsByProjectIdAndUserId(invite.getProject().getId(), currentUserId)) {
            invite.setStatus(InviteStatus.ACCEPTED);
            projectInviteRepository.save(invite);
            throw new ConflictException("Bạn đã là thành viên của dự án này rồi.");
        }

        ProjectMember newMember = ProjectMember.builder()
                .project(invite.getProject())
                .user(currentUser)
                .projectRole(invite.getProjectRole())
                .joinedAt(Instant.now())
                .invitedBy(invite.getInvitedBy().getId())
                .build();
        projectMemberRepository.save(newMember);
        reportService.invalidateOverviewCache(invite.getProject().getId());

        invite.setStatus(InviteStatus.ACCEPTED);
        projectInviteRepository.save(invite);

        return invite.getProject().getId();
    }
}