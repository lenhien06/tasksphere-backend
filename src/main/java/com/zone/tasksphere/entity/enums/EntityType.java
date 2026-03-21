package com.zone.tasksphere.entity.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "Loại thực thể bị tác động")
public enum EntityType {
    @Schema(description = "Dự án")
    PROJECT("Dự án"),
    
    @Schema(description = "Công việc")
    TASK("Công việc"),
    
    @Schema(description = "Giai đoạn")
    SPRINT("Giai đoạn"),
    
    @Schema(description = "Bình luận")
    COMMENT("Bình luận"),
    
    @Schema(description = "Tệp đính kèm")
    ATTACHMENT("Tệp đính kèm"),
    
    @Schema(description = "Thành viên")
    MEMBER("Thành viên"),

    @Schema(description = "Người dùng")
    USER("Người dùng");

    private final String description;

    EntityType(String description) {
        this.description = description;
    }
}
