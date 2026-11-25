package com.eyeqlytics.fieldops.workorder;

import com.eyeqlytics.fieldops.common.Priority;

import java.time.Instant;

/** API response shape for a work order (what the frontend consumes). */
public record WorkOrderView(Long id, Long requestId, String title, String description,
                            Priority priority, WorkOrderStatus status, Long zoneId, Long plannedBy,
                            Long assignedTechnicianId, Instant slaDueAt, boolean overdue,
                            Instant completedAt, Instant createdAt, Instant updatedAt) {

    static WorkOrderView of(WorkOrder w, Long technicianId) {
        boolean open = w.getStatus() != WorkOrderStatus.VERIFIED && w.getStatus() != WorkOrderStatus.CLOSED;
        boolean overdue = open && w.getSlaDueAt().isBefore(Instant.now());
        return new WorkOrderView(w.getId(), w.getRequestId(), w.getTitle(), w.getDescription(),
                w.getPriority(), w.getStatus(), w.getZoneId(), w.getPlannedBy(),
                technicianId, w.getSlaDueAt(), overdue, w.getCompletedAt(), w.getCreatedAt(), w.getUpdatedAt());
    }
}
