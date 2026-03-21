package com.zone.tasksphere.controller;

import com.zone.tasksphere.dto.request.CreateCustomFieldRequest;
import com.zone.tasksphere.dto.request.SaveCustomFieldValuesRequest;
import com.zone.tasksphere.dto.request.UpdateCustomFieldRequest;
import com.zone.tasksphere.dto.response.ApiResponse;
import com.zone.tasksphere.dto.response.CustomFieldDefinitionResponse;
import com.zone.tasksphere.dto.response.CustomFieldValueResponse;
import com.zone.tasksphere.dto.response.UserDetail;
import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.service.CustomFieldService;
import com.zone.tasksphere.utils.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "14. Custom Fields", description = "Quản lý trường tùy chỉnh cho dự án và task.")
@SecurityRequirement(name = "bearerAuth")
public class CustomFieldController {

    private final CustomFieldService customFieldService;

    private UUID getCurrentUserId() {
        UserDetail userDetail = AuthUtils.getUserDetail();
        if (userDetail == null || userDetail.getId() == null) {
            throw new CustomAuthenticationException("Phiên làm việc không hợp lệ hoặc chưa đăng nhập.");
        }
        return userDetail.getId();
    }

    @Operation(summary = "Lấy danh sách trường tùy chỉnh của dự án")
    @GetMapping("/projects/{projectId}/custom-fields")
    public ResponseEntity<ApiResponse<List<CustomFieldDefinitionResponse>>> getProjectCustomFields(
            @PathVariable UUID projectId) {
        List<CustomFieldDefinitionResponse> response = customFieldService.getFields(projectId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Tạo trường tùy chỉnh mới cho dự án")
    @PostMapping("/projects/{projectId}/custom-fields")
    public ResponseEntity<ApiResponse<CustomFieldDefinitionResponse>> createCustomField(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateCustomFieldRequest request) {
        CustomFieldDefinitionResponse response = customFieldService.createField(projectId, request, getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @Operation(summary = "Cập nhật trường tùy chỉnh")
    @PutMapping("/projects/{projectId}/custom-fields/{fieldId}")
    public ResponseEntity<ApiResponse<CustomFieldDefinitionResponse>> updateCustomField(
            @PathVariable UUID projectId,
            @PathVariable UUID fieldId,
            @Valid @RequestBody UpdateCustomFieldRequest request) {
        CustomFieldDefinitionResponse response = customFieldService.updateField(projectId, fieldId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Xóa trường tùy chỉnh")
    @DeleteMapping("/projects/{projectId}/custom-fields/{fieldId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteCustomField(
            @PathVariable UUID projectId,
            @PathVariable UUID fieldId) {
        Map<String, Object> result = customFieldService.deleteField(projectId, fieldId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @Operation(summary = "Lưu giá trị các trường tùy chỉnh cho task")
    @PostMapping("/tasks/{taskId}/custom-fields/values")
    public ResponseEntity<ApiResponse<List<CustomFieldValueResponse>>> saveCustomFieldValues(
            @PathVariable UUID taskId,
            @Valid @RequestBody SaveCustomFieldValuesRequest request) {
        List<CustomFieldValueResponse> response = customFieldService.saveValues(taskId, request, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Lấy giá trị các trường tùy chỉnh của task")
    @GetMapping("/tasks/{taskId}/custom-fields/values")
    public ResponseEntity<ApiResponse<List<CustomFieldValueResponse>>> getCustomFieldValues(
            @PathVariable UUID taskId) {
        List<CustomFieldValueResponse> response = customFieldService.getValues(taskId, getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
