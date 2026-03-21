package com.zone.tasksphere.service;

import com.zone.tasksphere.dto.request.CreateCustomFieldRequest;
import com.zone.tasksphere.dto.request.SaveCustomFieldValuesRequest;
import com.zone.tasksphere.dto.request.UpdateCustomFieldRequest;
import com.zone.tasksphere.dto.response.CustomFieldDefinitionResponse;
import com.zone.tasksphere.dto.response.CustomFieldValueResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface CustomFieldService {

    CustomFieldDefinitionResponse createField(UUID projectId, CreateCustomFieldRequest request, UUID currentUserId);

    List<CustomFieldDefinitionResponse> getFields(UUID projectId, UUID currentUserId);

    CustomFieldDefinitionResponse updateField(UUID projectId, UUID fieldId, UpdateCustomFieldRequest request, UUID currentUserId);

    Map<String, Object> deleteField(UUID projectId, UUID fieldId, UUID currentUserId);

    List<CustomFieldValueResponse> saveValues(UUID taskId, SaveCustomFieldValuesRequest request, UUID currentUserId);

    List<CustomFieldValueResponse> getValues(UUID taskId, UUID currentUserId);
}
