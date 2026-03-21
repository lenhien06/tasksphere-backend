package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.CreateSavedFilterRequest;
import com.zone.tasksphere.dto.response.SavedFilterResponse;

import java.util.List;
import java.util.UUID;

public interface SavedFilterService {

    /** POST /api/v1/projects/{projectId}/saved-filters */
    SavedFilterResponse createFilter(UUID projectId, CreateSavedFilterRequest request, UUID currentUserId);

    /** GET /api/v1/projects/{projectId}/saved-filters */
    List<SavedFilterResponse> getFilters(UUID projectId, UUID currentUserId);

    /** DELETE /api/v1/saved-filters/{filterId} */
    void deleteFilter(UUID filterId, UUID currentUserId);
}
