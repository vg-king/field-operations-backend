package com.eyeqlytics.fieldops.alert;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity @Table(name = "alerts") @Getter @Setter
public class Alert {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String type;
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;
    @Column(name = "target_user_id")
    private Long targetUserId;
    @Column(name = "zone_id")
    private Long zoneId;
    @Column(name = "entity_type")
    private String entityType;
    @Column(name = "entity_id")
    private Long entityId;
    private String message;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "read_at")
    private Instant readAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); }
}
