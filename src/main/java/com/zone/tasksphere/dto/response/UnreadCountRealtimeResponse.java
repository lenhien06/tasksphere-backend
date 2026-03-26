package com.zone.tasksphere.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnreadCountRealtimeResponse {
    private long unreadCount;
    private Instant updatedAt;
}
