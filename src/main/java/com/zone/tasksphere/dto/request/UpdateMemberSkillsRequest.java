package com.zone.tasksphere.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Cập nhật skill tags của thành viên workspace")
public class UpdateMemberSkillsRequest {

    @NotNull(message = "skillTags không được null")
    @Schema(description = "Danh sách kỹ năng", example = "[\"React\",\"TypeScript\",\"Java\"]")
    private List<String> skillTags;
}
