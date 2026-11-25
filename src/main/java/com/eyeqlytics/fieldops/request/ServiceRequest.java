package com.eyeqlytics.fieldops.request;

import com.eyeqlytics.fieldops.common.Priority;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity @Table(name = "service_requests") @Getter @Setter
public class ServiceRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String description;
    @Column(name = "location_address")
    private String locationAddress;
    @Column(name = "zone_id")
    private Long zoneId;
    @Enumerated(EnumType.STRING)
    private Priority priority;
    @Enumerated(EnumType.STRING)
    private RequestStatus status;
    @Column(name = "requested_by")
    private Long requestedBy;
    @Column(name = "sla_due_at")
    private Instant slaDueAt;
    @Column(name = "created_at")
    private Instant createdAt;
    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
}
