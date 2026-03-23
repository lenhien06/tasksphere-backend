package com.zone.tasksphere.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.data.domain.Page;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Api Response")
public class ApiResponse<T> {

    @Schema(description = "Data", example = "example")
    private T data;
    @Schema(description = "Meta", example = "example")
    private Map<String, Object> meta;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .data(data)
                .meta(defaultMeta())
                .build();
    }

    public static <T> ApiResponse<Map<String, Object>> success(Page<T> page) {
        Map<String, Object> meta = new HashMap<>(defaultMeta());
        meta.put("page", page.getNumber());
        meta.put("size", page.getSize());
        meta.put("totalElements", page.getTotalElements());
        meta.put("totalPages", page.getTotalPages());

        return ApiResponse.<Map<String, Object>>builder()
                .data(Map.of("content", page.getContent()))
                .meta(meta)
                .build();
    }

    /**
     * Trả về phân trang theo chuẩn spec:
     * data = { content, page, size, totalElements, totalPages }
     * meta = { message, timestamp, version }
     */
    public static <T> ApiResponse<Map<String, Object>> successPage(Page<T> page, String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("content", page.getContent());
        data.put("page", page.getNumber());
        data.put("size", page.getSize());
        data.put("totalElements", page.getTotalElements());
        data.put("totalPages", page.getTotalPages());

        Map<String, Object> meta = new HashMap<>(defaultMeta());
        meta.put("message", message);

        return ApiResponse.<Map<String, Object>>builder()
                .data(data)
                .meta(meta)
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        Map<String, Object> meta = new HashMap<>(defaultMeta());
        meta.put("message", message);

        return ApiResponse.<T>builder()
                .data(data)
                .meta(meta)
                .build();
    }

    /** Trả về data = null với meta chuẩn (tránh ambiguity khi gọi success(null)). */
    public static ApiResponse<Void> voidSuccess() {
        return ApiResponse.<Void>builder()
                .data(null)
                .meta(new HashMap<>(defaultMeta()))
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        Map<String, Object> meta = new HashMap<>(defaultMeta());
        meta.put("error", "Error"); // Default error name
        meta.put("message", message);

        return ApiResponse.<T>builder()
                .meta(meta)
                .build();
    }

    public static <T> ApiResponse<T> error(String error, String message) {
        Map<String, Object> meta = new HashMap<>(defaultMeta());
        meta.put("error", error);
        meta.put("message", message);

        return ApiResponse.<T>builder()
                .meta(meta)
                .build();
    }

    private static Map<String, Object> defaultMeta() {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "version", "v1"
        );
    }
}
