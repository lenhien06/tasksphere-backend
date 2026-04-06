package com.zone.tasksphere.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateMemberSkillsRequest {

    @NotNull(message = "skillTags không được null")
    @Size(max = 20, message = "Tối đa 20 skill tags")
    private List<@Size(max = 50, message = "Mỗi skill tối đa 50 ký tự") String> skillTags;
}
