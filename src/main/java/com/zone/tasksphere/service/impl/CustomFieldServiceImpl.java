package com.zone.tasksphere.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zone.tasksphere.dto.request.CreateCustomFieldRequest;
import com.zone.tasksphere.dto.request.SaveCustomFieldValuesRequest;
import com.zone.tasksphere.dto.request.UpdateCustomFieldRequest;
import com.zone.tasksphere.dto.response.CustomFieldDefinitionResponse;
import com.zone.tasksphere.dto.response.CustomFieldValueResponse;
import com.zone.tasksphere.entity.CustomField;
import com.zone.tasksphere.entity.CustomFieldValue;
import com.zone.tasksphere.entity.Task;
import com.zone.tasksphere.entity.enums.CustomFieldType;
import com.zone.tasksphere.entity.enums.ProjectRole;
import com.zone.tasksphere.entity.enums.SystemRole;
import com.zone.tasksphere.exception.BadRequestException;
import com.zone.tasksphere.exception.BusinessRuleException;
import com.zone.tasksphere.exception.ConflictException;
import com.zone.tasksphere.exception.Forbidden;
import com.zone.tasksphere.exception.NotFoundException;
import com.zone.tasksphere.repository.CustomFieldRepository;
import com.zone.tasksphere.repository.CustomFieldValueRepository;
import com.zone.tasksphere.repository.ProjectMemberRepository;
import com.zone.tasksphere.repository.ProjectRepository;
import com.zone.tasksphere.repository.TaskRepository;
import com.zone.tasksphere.repository.UserRepository;
import com.zone.tasksphere.service.CustomFieldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class CustomFieldServiceImpl implements CustomFieldService {

    private static final int MAX_FIELDS_PER_PROJECT = 20;

    private final CustomFieldRepository customFieldRepository;
    private final CustomFieldValueRepository customFieldValueRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public CustomFieldDefinitionResponse createField(UUID projectId, CreateCustomFieldRequest request, UUID currentUserId) {
        requirePm(projectId, currentUserId);

        // Validate project exists
        projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Dự án không tồn tại"));

        long count = customFieldRepository.countByProjectIdAndDeletedAtIsNull(projectId);
        if (count >= MAX_FIELDS_PER_PROJECT) {
            throw new BusinessRuleException("Tối đa " + MAX_FIELDS_PER_PROJECT + " custom fields mỗi dự án");
        }

        if (customFieldRepository.existsByProjectIdAndNameAndDeletedAtIsNull(projectId, request.getName())) {
            throw new ConflictException("TSK_011: Tên custom field '" + request.getName() + "' đã tồn tại trong dự án");
        }

        if (request.getFieldType() == CustomFieldType.SELECT) {
            validateSelectOptions(request.getOptions());
        }

        String optionsJson = null;
        if (request.getOptions() != null && !request.getOptions().isEmpty()) {
            try {
                optionsJson = objectMapper.writeValueAsString(request.getOptions());
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Không thể xử lý danh sách options");
            }
        }

        var project = projectRepository.getReferenceById(projectId);

        CustomField field = CustomField.builder()
                .project(project)
                .name(request.getName())
                .fieldType(request.getFieldType())
                .isRequired(request.isRequired())
                .isHidden(false)
                .options(optionsJson)
                .sortOrder(request.getPosition())
                .build();

        field = customFieldRepository.save(field);
        return toDefinitionResponse(field);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomFieldDefinitionResponse> getFields(UUID projectId, UUID currentUserId) {
        var memberOpt = projectMemberRepository.findByProjectIdAndUserId(projectId, currentUserId);
        boolean isAdmin = isCurrentUserAdmin(currentUserId);

        List<CustomField> fields;
        if (isAdmin || (memberOpt.isPresent() && memberOpt.get().getProjectRole() == ProjectRole.PROJECT_MANAGER)) {
            fields = customFieldRepository.findByProjectIdAndDeletedAtIsNullOrderBySortOrderAsc(projectId);
        } else {
            if (memberOpt.isEmpty()) {
                throw new Forbidden("Bạn không phải thành viên dự án");
            }
            fields = customFieldRepository.findByProjectIdAndIsHiddenFalseAndDeletedAtIsNullOrderBySortOrderAsc(projectId);
        }

        return fields.stream().map(this::toDefinitionResponse).toList();
    }

    @Override
    public CustomFieldDefinitionResponse updateField(UUID projectId, UUID fieldId, UpdateCustomFieldRequest request, UUID currentUserId) {
        requirePm(projectId, currentUserId);

        CustomField field = customFieldRepository.findByIdAndProjectIdAndDeletedAtIsNull(fieldId, projectId)
                .orElseThrow(() -> new NotFoundException("Custom field không tồn tại"));

        if (request.getName() != null && !request.getName().equals(field.getName())) {
            if (customFieldRepository.existsByProjectIdAndNameAndIdNotAndDeletedAtIsNull(projectId, request.getName(), fieldId)) {
                throw new ConflictException("TSK_011: Tên custom field '" + request.getName() + "' đã tồn tại trong dự án");
            }
            field.setName(request.getName());
        }

        if (request.getOptions() != null) {
            if (field.getFieldType() != CustomFieldType.SELECT) {
                throw new BadRequestException("Options chỉ áp dụng cho field type SELECT");
            }
            validateSelectOptions(request.getOptions());
            try {
                field.setOptions(objectMapper.writeValueAsString(request.getOptions()));
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Không thể xử lý danh sách options");
            }
        }

        if (request.getPosition() != null) {
            field.setSortOrder(request.getPosition());
        }

        if (request.getRequired() != null) {
            field.setRequired(request.getRequired());
        }

        if (request.getHidden() != null) {
            field.setHidden(request.getHidden());
        }

        field = customFieldRepository.save(field);
        return toDefinitionResponse(field);
    }

    @Override
    public Map<String, Object> deleteField(UUID projectId, UUID fieldId, UUID currentUserId) {
        requirePm(projectId, currentUserId);

        CustomField field = customFieldRepository.findByIdAndProjectIdAndDeletedAtIsNull(fieldId, projectId)
                .orElseThrow(() -> new NotFoundException("Custom field không tồn tại"));

        boolean hasValues = customFieldValueRepository.existsNonEmptyValueByCustomFieldId(fieldId);
        if (hasValues) {
            field.setHidden(true);
            customFieldRepository.save(field);
            return Map.of(
                    "message", "Field đã được ẩn vì có dữ liệu liên quan",
                    "action", "HIDDEN"
            );
        } else {
            customFieldValueRepository.deleteByCustomFieldId(fieldId);
            field.setDeletedAt(Instant.now());
            customFieldRepository.save(field);
            return Map.of(
                    "message", "Đã xóa custom field",
                    "action", "DELETED"
            );
        }
    }

    @Override
    public List<CustomFieldValueResponse> saveValues(UUID taskId, SaveCustomFieldValuesRequest request, UUID currentUserId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task không tồn tại"));

        UUID projectId = task.getProject().getId();
        var memberOpt = projectMemberRepository.findByProjectIdAndUserId(projectId, currentUserId);
        if (memberOpt.isEmpty() || memberOpt.get().getProjectRole() == ProjectRole.VIEWER) {
            throw new Forbidden("Chỉ PM và MEMBER mới được cập nhật custom field values");
        }

        for (SaveCustomFieldValuesRequest.CustomFieldValueItem item : request.getValues()) {
            CustomField field = customFieldRepository.findByIdAndProjectIdAndDeletedAtIsNull(item.getFieldId(), projectId)
                    .orElseThrow(() -> new BadRequestException("Custom field " + item.getFieldId() + " không thuộc dự án này"));

            if (item.getValue() == null) {
                // Delete existing value
                customFieldValueRepository.findByTaskIdAndCustomFieldId(taskId, item.getFieldId())
                        .ifPresent(customFieldValueRepository::delete);
            } else {
                validateFieldValue(field, item.getValue());

                var existingOpt = customFieldValueRepository.findByTaskIdAndCustomFieldId(taskId, item.getFieldId());
                CustomFieldValue cfv;
                if (existingOpt.isPresent()) {
                    cfv = existingOpt.get();
                } else {
                    cfv = new CustomFieldValue();
                    cfv.setTask(task);
                    cfv.setCustomField(field);
                }
                setTypedValue(cfv, field.getFieldType(), item.getValue());
                customFieldValueRepository.save(cfv);
            }
        }

        return getValues(taskId, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomFieldValueResponse> getValues(UUID taskId, UUID currentUserId) {
        taskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task không tồn tại"));

        List<CustomFieldValue> values = customFieldValueRepository.findByTaskId(taskId);
        return values.stream().map(this::toValueResponse).toList();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** PM của project hoặc System Admin (BR-05). */
    private void requirePm(UUID projectId, UUID userId) {
        if (isCurrentUserAdmin(userId)) {
            return;
        }
        var member = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new Forbidden("Bạn không phải thành viên dự án"));
        if (member.getProjectRole() != ProjectRole.PROJECT_MANAGER) {
            throw new Forbidden("Chỉ PM mới được quản lý custom fields");
        }
    }

    private boolean isCurrentUserAdmin(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getSystemRole() == SystemRole.ADMIN)
                .orElse(false);
    }

    private void validateSelectOptions(List<String> options) {
        if (options == null || options.size() < 2) {
            throw new BadRequestException("SELECT field phải có ít nhất 2 options");
        }
        if (options.size() > 20) {
            throw new BadRequestException("Tối đa 20 options cho SELECT field");
        }
        for (String opt : options) {
            if (opt == null || opt.isBlank()) {
                throw new BadRequestException("Option không được để trống");
            }
            if (opt.length() > 100) {
                throw new BadRequestException("Mỗi option tối đa 100 ký tự");
            }
        }
        long distinct = options.stream().distinct().count();
        if (distinct != options.size()) {
            throw new BadRequestException("Các options không được trùng nhau");
        }
    }

    private void validateFieldValue(CustomField field, String value) {
        if (value == null) return;
        switch (field.getFieldType()) {
            case NUMBER -> {
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException e) {
                    throw new BadRequestException("Field '" + field.getName() + "': giá trị phải là số");
                }
            }
            case DATE -> {
                try {
                    LocalDate.parse(value);
                } catch (DateTimeParseException e) {
                    throw new BadRequestException("Field '" + field.getName() + "': định dạng ngày phải là YYYY-MM-DD");
                }
            }
            case BOOLEAN -> {
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new BadRequestException("Field '" + field.getName() + "': giá trị phải là true/false");
                }
            }
            case SELECT -> {
                if (field.getOptions() == null) {
                    throw new BadRequestException("Field '" + field.getName() + "': chưa có options");
                }
                try {
                    List<String> opts = objectMapper.readValue(field.getOptions(), new TypeReference<>() {});
                    if (!opts.contains(value)) {
                        throw new BadRequestException("Field '" + field.getName() + "': giá trị không hợp lệ");
                    }
                } catch (JsonProcessingException e) {
                    throw new BadRequestException("Field '" + field.getName() + "': lỗi đọc options");
                }
            }
            case TEXT -> {
                if (value.length() > 1000) {
                    throw new BadRequestException("Field '" + field.getName() + "': tối đa 1000 ký tự");
                }
            }
            default -> {
                // URL, MULTI_SELECT: no special validation beyond basic text
            }
        }
    }

    private void setTypedValue(CustomFieldValue cfv, CustomFieldType type, String value) {
        // Reset all typed fields first
        cfv.setTextValue(null);
        cfv.setNumberValue(null);
        cfv.setDateValue(null);
        cfv.setBooleanValue(null);

        switch (type) {
            case TEXT, SELECT, MULTI_SELECT, URL -> cfv.setTextValue(value);
            case NUMBER -> cfv.setNumberValue(new BigDecimal(value));
            case DATE -> cfv.setDateValue(LocalDate.parse(value));
            case BOOLEAN -> cfv.setBooleanValue(Boolean.parseBoolean(value));
        }
    }

    private String getStringValue(CustomFieldValue cfv, CustomFieldType type) {
        return switch (type) {
            case TEXT, SELECT, MULTI_SELECT, URL -> cfv.getTextValue();
            case NUMBER -> cfv.getNumberValue() != null ? cfv.getNumberValue().toPlainString() : null;
            case DATE -> cfv.getDateValue() != null ? cfv.getDateValue().toString() : null;
            case BOOLEAN -> cfv.getBooleanValue() != null ? cfv.getBooleanValue().toString() : null;
        };
    }

    private Object getTypedValue(CustomFieldType type, String value) {
        if (value == null) return null;
        return switch (type) {
            case NUMBER -> Double.parseDouble(value);
            case DATE -> LocalDate.parse(value);
            case BOOLEAN -> Boolean.parseBoolean(value);
            default -> value;
        };
    }

    private CustomFieldDefinitionResponse toDefinitionResponse(CustomField field) {
        List<String> optionsList = null;
        if (field.getOptions() != null && !field.getOptions().isBlank()) {
            try {
                optionsList = objectMapper.readValue(field.getOptions(), new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                log.warn("Could not parse options for field {}: {}", field.getId(), e.getMessage());
            }
        }

        boolean hasValues = customFieldValueRepository.existsNonEmptyValueByCustomFieldId(field.getId());

        return CustomFieldDefinitionResponse.builder()
                .id(field.getId())
                .name(field.getName())
                .fieldType(field.getFieldType())
                .options(optionsList)
                .required(field.isRequired())
                .hidden(field.isHidden())
                .position(field.getSortOrder())
                .hasValues(hasValues)
                .build();
    }

    private CustomFieldValueResponse toValueResponse(CustomFieldValue cfv) {
        CustomFieldType type = cfv.getCustomField().getFieldType();
        String strValue = getStringValue(cfv, type);
        return CustomFieldValueResponse.builder()
                .fieldId(cfv.getCustomField().getId())
                .fieldName(cfv.getCustomField().getName())
                .fieldType(type)
                .value(strValue)
                .typedValue(getTypedValue(type, strValue))
                .build();
    }
}
