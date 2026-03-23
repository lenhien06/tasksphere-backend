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
    LOGIN("Đã đăng nhập"),

    @Schema(description = "Đã gửi lời mời thành viên")
    MEMBER_INVITED("Đã mời thành viên"),

    @Schema(description = "Thành viên đã tham gia dự án")
    MEMBER_JOINED("Thành viên đã tham gia"),

    @Schema(description = "Thành viên đã rời dự án")
    MEMBER_LEFT("Thành viên đã rời dự án"),

    @Schema(description = "Lời mời đã bị từ chối")
    INVITE_DECLINED("Đã từ chối lời mời"),

    @Schema(description = "Đã khôi phục dự án từ archive")
    PROJECT_RESTORED("Đã khôi phục dự án");

    private final String description;

    ActionType(String description) {
        this.description = description;
    }
}
