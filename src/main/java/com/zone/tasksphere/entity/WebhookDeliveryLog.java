package com.zone.tasksphere.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_delivery_logs",
    indexes = {
        @Index(name = "idx_wdl_webhook", columnList = "webhook_id"),
        @Index(name = "idx_wdl_delivered", columnList = "delivered_at")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WebhookDeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", length = 36, updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "webhook_id", nullable = false)
    private Webhook webhook;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "request_body", columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private int attemptNumber = 1;

    @CreatedDate
    @Column(name = "delivered_at", nullable = false, updatable = false)
    private Instant deliveredAt;
}
