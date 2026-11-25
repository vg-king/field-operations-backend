package com.eyeqlytics.fieldops.alert;

import java.time.Instant;

public record AlertView(Long id, String type, AlertSeverity severity, String entityType,
                        Long entityId, String message, Instant createdAt, Instant readAt) {
    static AlertView from(Alert a) {
        return new AlertView(a.getId(), a.getType(), a.getSeverity(), a.getEntityType(),
                a.getEntityId(), a.getMessage(), a.getCreatedAt(), a.getReadAt());
    }
}
