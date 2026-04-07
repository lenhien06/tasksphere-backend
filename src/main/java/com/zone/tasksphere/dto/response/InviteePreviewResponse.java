package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Preview thông tin người được mời theo email")
public class InviteePreviewResponse {

    @Schema(description = "Email đã được chuẩn hóa", example = "user@example.com")
    private String email;

    @Schema(description = "Email này đã có tài khoản trong hệ thống hay chưa", example = "true")
    private boolean existsInSystem;

    @Schema(description = "User ID nếu email đã có tài khoản")
    private UUID userId;

    @Schema(description = "Tên hiển thị nếu email đã có tài khoản")
    private String fullName;

    @Schema(description = "Ảnh đại diện nếu email đã có tài khoản")
    private String avatarUrl;

    @Schema(description = "Skill tags từ profile người dùng", example = "[\"Java\", \"React\"]")
    private List<String> skillTags;
}
