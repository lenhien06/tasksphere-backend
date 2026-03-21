package com.zone.tasksphere.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Page Response")
public class PageResponse<T> {
    
    @Schema(description = "Content", example = "[]")
    private List<T> content;

    @Schema(description = "Total elements", example = "10")
    private long totalElements;

    @Schema(description = "Total pages", example = "10")
    private int totalPages;

    @Schema(description = "Size", example = "10")
    private int size;

    @Schema(description = "Number", example = "1")
    private int number;

    @Schema(description = "First", example = "true")
    private boolean first;

    @Schema(description = "Last", example = "true")
    private boolean last;

    @Schema(description = "Empty", example = "true")
    private boolean empty;

    // FIX: P5-BE-05 - unreadCount cho notification endpoint (null = không áp dụng)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Unread count (only present in /notifications response)", example = "5")
    private Long unreadCount;

    /**
     * Chuyển đổi từ Spring Data Page sang PageResponse chuẩn của project
     */
    public static <T> PageResponse<T> fromPage(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .size(page.getSize())
                .number(page.getNumber())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build();
    }
}
