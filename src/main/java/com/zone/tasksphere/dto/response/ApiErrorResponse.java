package com.zone.tasksphere.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Chuẩn lỗi API cho Member/Invite (top-level error + message + meta tùy chọn).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Structured API error")
public class ApiErrorResponse {

    @Schema(description = "Mã lỗi ổn định cho FE", example = "MEMBER_LIMIT_EXCEEDED")
    private String error;

    @Schema(description = "Thông báo hiển thị cho người dùng")
    private String message;

    @Schema(description = "Dữ liệu bổ sung (ví dụ currentCount, limit, plan)")
    private Map<String, Object> meta;
}
