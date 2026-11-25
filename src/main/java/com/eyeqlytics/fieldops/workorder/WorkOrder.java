package com.eyeqlytics.fieldops.workorder;

import com.eyeqlytics.fieldops.common.Priority;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity @Table(name = "work_orders") @Getter @Setter
public class WorkOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "request_id")
    private Long requestId;
    private String title;
    private String description;
    @Enumerated(EnumType.STRING)
    private Priority priority;
    @Enumerated(EnumType.STRING)
    private WorkOrderStatus status;
    @Column(name = "zone_id")
    private Long zoneId;
    @Column(name = "planned_by")
    private Long plannedBy;
    @Column(name = "sla_due_at")
    private Instant slaDueAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private Long version;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
