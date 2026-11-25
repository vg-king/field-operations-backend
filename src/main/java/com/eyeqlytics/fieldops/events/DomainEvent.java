package com.eyeqlytics.fieldops.events;

import java.time.Instant;
import java.util.Map;

public record DomainEvent(String type, String entityType, Long entityId, Long zoneId,
                          Map<String, Object> payload, Instant at) {
    public static DomainEvent of(String type, String entityType, Long entityId, Long zoneId, Map<String, Object> payload) {
        return new DomainEvent(type, entityType, entityId, zoneId, payload, Instant.now());
    }
}
