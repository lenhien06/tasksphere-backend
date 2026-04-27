package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.CreateWorkspaceRequest;
import com.zone.tasksphere.dto.request.UpdateWorkspaceRequest;
import com.zone.tasksphere.dto.request.WorkspaceInviteMemberRequest;
import com.zone.tasksphere.dto.response.WorkspaceInviteListResponse;
import com.zone.tasksphere.dto.request.UpdateMemberSkillsRequest;
import com.zone.tasksphere.dto.response.UserProfileResponse;
import com.zone.tasksphere.dto.response.VerifyWorkspaceInviteResponse;
import com.zone.tasksphere.dto.response.WorkspaceInviteResponse;
import com.zone.tasksphere.dto.response.WorkspaceMemberResponse;
import com.zone.tasksphere.dto.response.WorkspaceResponse;
import com.zone.tasksphere.entity.User;
import com.zone.tasksphere.entity.enums.InviteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface WorkspaceService {

    WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, UUID creatorId);

    List<WorkspaceResponse> getMyWorkspaces(UUID userId);

    WorkspaceResponse getBySlug(String slug, UUID currentUserId);

    WorkspaceResponse updateWorkspace(UUID workspaceId, UpdateWorkspaceRequest request, UUID requesterId);

    void deleteWorkspace(UUID workspaceId, UUID requesterId);

    WorkspaceInviteResponse inviteMember(UUID workspaceId, WorkspaceInviteMemberRequest request, UUID inviterId);

    Page<WorkspaceInviteListResponse> getInvitesByStatus(UUID workspaceId, UUID actorId, InviteStatus status, Pageable pageable);

    void revokeInvite(UUID workspaceId, UUID inviteId, UUID actorId);

    void resendInvite(UUID workspaceId, UUID inviteId, UUID actorId);

    List<WorkspaceInviteListResponse> getMyWorkspaceInvites(String email);

    VerifyWorkspaceInviteResponse verifyInviteToken(String token);

    WorkspaceResponse acceptInvite(String token, UUID currentUserId);

    void declineInvite(String token, UUID currentUserId);

    void acceptInviteAfterSignup(String token, User newUser);

    List<WorkspaceMemberResponse> getMembers(UUID workspaceId);

    UserProfileResponse getMemberProfile(UUID workspaceId, UUID targetUserId, UUID requesterId);

    WorkspaceMemberResponse updateMemberSkills(UUID workspaceId, UUID userId,
                                               UpdateMemberSkillsRequest request, UUID requesterId);

    void removeMember(UUID workspaceId, UUID userId, UUID requesterId);
}
