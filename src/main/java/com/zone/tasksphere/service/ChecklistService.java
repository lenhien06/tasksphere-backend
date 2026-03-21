package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.CreateChecklistItemRequest;
import com.zone.tasksphere.dto.request.ReorderChecklistRequest;
import com.zone.tasksphere.dto.request.UpdateChecklistItemRequest;
import com.zone.tasksphere.dto.response.ChecklistItemResponse;
import com.zone.tasksphere.dto.response.ChecklistSummary;

import java.util.UUID;

public interface ChecklistService {

    ChecklistItemResponse addItem(UUID taskId, CreateChecklistItemRequest request, UUID currentUserId);

    ChecklistSummary getChecklist(UUID taskId, UUID currentUserId);

    ChecklistItemResponse updateItem(UUID itemId, UpdateChecklistItemRequest request, UUID currentUserId);

    void deleteItem(UUID itemId, UUID currentUserId);

    void reorder(UUID taskId, ReorderChecklistRequest request, UUID currentUserId);
}
