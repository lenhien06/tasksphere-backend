package com.zone.tasksphere.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class WorkspaceMemberId implements Serializable {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
        name = "workspace_id",
        length = 36,
        nullable = false,
        columnDefinition = "CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    )
    private UUID workspaceId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
        name = "user_id",
        length = 36,
        nullable = false,
        columnDefinition = "CHAR(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
    )
    private UUID userId;
}
