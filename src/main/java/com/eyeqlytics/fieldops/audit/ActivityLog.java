package com.eyeqlytics.fieldops.audit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;


@Entity @Table(name = "activity_logs") @Getter @Setter
public class ActivityLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "entity_type")
    private String entityType;
    @Column(name = "entity_id")
    private Long entityId;
    private String action;
    @Column(name = "from_status")
    private String fromStatus;
    @Column(name = "to_status")
    private String toStatus;
    @Column(name = "actor_id")
    private Long actorId;
    private String remarks;
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist void onCreate() { createdAt = Instant.now(); }
}
