package com.zone.tasksphere.entity.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "Loại hành động thực hiện")
public enum ActionType {
    @Schema(description = "Đã tạo mới")
    CREATED("Đã tạo"),
    
    @Schema(description = "Đã cập nhật dữ liệu")
    UPDATED("Đã cập nhật"),
    
    @Schema(description = "Đã xóa (hoặc xóa mềm)")
    DELETED("Đã xóa"),
    
    @Schema(description = "Đã thay đổi trạng thái")
    STATUS_CHANGED("Đã đổi trạng thái"),

    @Schema(description = "Đã thay đổi vị trí trên Kanban")
    POSITION_CHANGED("Đã đổi vị trí"),

    @Schema(description = "Đã thay đổi sprint")
    SPRINT_CHANGED("Đã đổi sprint"),
    
    @Schema(description = "Đã phân công người thực hiện")
    ASSIGNED("Đã phân công"),

    @Schema(description = "Đã đăng nhập")
    LOGIN("Đã đăng nhập");

    private final String description;

    ActionType(String description) {
        this.description = description;
    }
}
