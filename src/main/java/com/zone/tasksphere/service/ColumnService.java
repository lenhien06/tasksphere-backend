package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.CreateColumnRequest;
import com.zone.tasksphere.dto.request.ReorderColumnsRequest;
import com.zone.tasksphere.dto.request.UpdateColumnRequest;
import com.zone.tasksphere.dto.response.ColumnResponse;
import com.zone.tasksphere.dto.response.DeleteColumnResponse;

import java.util.List;
import java.util.UUID;

public interface ColumnService {

    /** POST /api/v1/projects/{projectId}/columns */
    ColumnResponse createColumn(UUID projectId, CreateColumnRequest request, UUID currentUserId);

    /** PUT /api/v1/columns/{columnId} */
    ColumnResponse updateColumn(UUID columnId, UpdateColumnRequest request, UUID currentUserId);

    /** DELETE /api/v1/columns/{columnId} */
    DeleteColumnResponse deleteColumn(UUID columnId, UUID currentUserId);

    /** PATCH /api/v1/projects/{projectId}/columns/reorder */
    List<ColumnResponse> reorderColumns(UUID projectId, ReorderColumnsRequest request, UUID currentUserId);
}
