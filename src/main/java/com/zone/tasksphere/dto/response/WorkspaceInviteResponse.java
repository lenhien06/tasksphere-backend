package com.zone.tasksphere.dto.response;

import com.zone.tasksphere.entity.enums.WorkspaceRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Kết quả mời thành viên vào workspace")
public class WorkspaceInviteResponse {

    @Schema(description = "Email được mời", example = "user@example.com")
    private String email;

    @Schema(description = "Vai trò được gán", example = "MEMBER")
    private WorkspaceRole role;

    @Schema(description = "Người được mời đã có tài khoản trong hệ thống hay chưa", example = "true")
    private boolean existingUser;

    @Schema(description = "Đã được thêm trực tiếp vào workspace hay mới chỉ gửi mail", example = "true")
    private boolean addedToWorkspace;

    @Schema(description = "Trạng thái tổng quát", example = "active")
    private String status;
}
