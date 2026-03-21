package com.zone.tasksphere.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum AttachmentType {
    IMAGE("Image", List.of("jpg", "png", "webp", "gif")),
    DOCUMENT("Document", List.of("pdf", "doc", "docx", "xls", "xlsx")),
    VIDEO("Video", List.of("mp4", "mov")),
    ARCHIVE("Archive", List.of("zip", "rar")),
    CODE("Code", List.of("txt", "json", "xml", "yaml")),
    OTHER("Other", List.of());

    private final String displayName;
    private final List<String> allowedExtensions;
}
