package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.CreateWorkspaceRequest;
import com.zone.tasksphere.dto.request.UpdateWorkspaceRequest;
import com.zone.tasksphere.dto.request.WorkspaceInviteMemberRequest;
import com.zone.tasksphere.dto.request.UpdateMemberSkillsRequest;
import com.zone.tasksphere.dto.response.UserProfileResponse;
import com.zone.tasksphere.dto.response.WorkspaceInviteResponse;
import com.zone.tasksphere.dto.response.WorkspaceMemberResponse;
import com.zone.tasksphere.dto.response.WorkspaceResponse;

import java.util.List;
import java.util.UUID;

public interface WorkspaceService {

    WorkspaceResponse createWorkspace(CreateWorkspaceRequest request, UUID creatorId);

    List<WorkspaceResponse> getMyWorkspaces(UUID userId);

    WorkspaceResponse getBySlug(String slug, UUID currentUserId);

    WorkspaceResponse updateWorkspace(UUID workspaceId, UpdateWorkspaceRequest request, UUID requesterId);

    void deleteWorkspace(UUID workspaceId, UUID requesterId);

    WorkspaceInviteResponse inviteMember(UUID workspaceId, WorkspaceInviteMemberRequest request, UUID inviterId);

    List<WorkspaceMemberResponse> getMembers(UUID workspaceId);

    UserProfileResponse getMemberProfile(UUID workspaceId, UUID targetUserId, UUID requesterId);

    WorkspaceMemberResponse updateMemberSkills(UUID workspaceId, UUID userId,
                                               UpdateMemberSkillsRequest request, UUID requesterId);

    void removeMember(UUID workspaceId, UUID userId, UUID requesterId);
}
