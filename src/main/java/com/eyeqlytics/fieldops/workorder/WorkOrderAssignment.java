package com.eyeqlytics.fieldops.workorder;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity @Table(name = "work_order_assignments") @Getter @Setter
public class WorkOrderAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "work_order_id")
    private Long workOrderId;
    @Column(name = "technician_id")
    private Long technicianId;
    @Column(name = "assigned_by")
    private Long assignedBy;
    @Column(name = "assigned_at")
    private Instant assignedAt;
    private boolean active = true;

    @PrePersist void onCreate() { assignedAt = Instant.now(); }
}
