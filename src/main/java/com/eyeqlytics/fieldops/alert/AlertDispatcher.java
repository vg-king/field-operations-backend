package com.eyeqlytics.fieldops.alert;

import com.eyeqlytics.fieldops.events.DomainEvent;
import com.eyeqlytics.fieldops.identity.Role;
import com.eyeqlytics.fieldops.identity.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The "who needs to know about this?" rules. Consumes domain events from Redis and turns them
 * into per-user alerts. Business services never call this directly (they only publish events),
 * so this whole component could be extracted into a separate Notification Service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertDispatcher {

    private final ObjectMapper mapper;
    private final AlertService alerts;
    private final UserRepository users;

    public void handle(String json) {
        try {
            route(mapper.readValue(json, DomainEvent.class));
        } catch (Exception ex) {
            log.warn("Could not process event: {}", ex.getMessage());
        }
    }

    private void route(DomainEvent e) {
        Map<String, Object> p = e.payload() == null ? Map.of() : e.payload();
        String ref = "#" + e.entityId();
        switch (e.type()) {
            case "REQUEST_CREATED" -> {
                String prio = String.valueOf(p.get("priority"));
                AlertSeverity sev = ("CRITICAL".equals(prio) || "HIGH".equals(prio))
                        ? AlertSeverity.WARNING : AlertSeverity.INFO;
                toRole(e, Role.PLANNER, sev, "New " + prio + " priority request " + ref + ": " + p.get("title"));
            }
            case "WORK_ORDER_ASSIGNED" -> toUser(e, longOf(p.get("technicianId")), AlertSeverity.INFO,
                    "Work order " + ref + " has been assigned to you (priority " + p.get("priority") + ")");
            case "PRIORITY_CHANGED" -> {
                AlertSeverity sev = "CRITICAL".equals(String.valueOf(p.get("to")))
                        ? AlertSeverity.CRITICAL : AlertSeverity.WARNING;
                String msg = "Priority of work order " + ref + " changed " + p.get("from") + " -> " + p.get("to");
                toUser(e, longOf(p.get("technicianId")), sev, msg);
                toRole(e, Role.SUPERVISOR, sev, msg);
            }
            case "WORK_ORDER_COMPLETED" -> toRole(e, Role.SUPERVISOR, AlertSeverity.INFO,
                    "Work order " + ref + " completed and awaiting your verification");
            case "WORK_ORDER_REJECTED" -> toUser(e, longOf(p.get("technicianId")), AlertSeverity.WARNING,
                    "Work order " + ref + " was sent back for rework: " + p.get("remarks"));
            case "WORK_ORDER_DELAYED" -> {
                String msg = "Work order " + ref + " has passed its SLA deadline";
                toRole(e, Role.PLANNER, AlertSeverity.WARNING, msg);
                toUser(e, longOf(p.get("technicianId")), AlertSeverity.WARNING, msg);
            }
            case "WORK_ORDER_ESCALATED" -> {
                String msg = "ESCALATION: work order " + ref + " is badly overdue and still open";
                toRole(e, Role.SUPERVISOR, AlertSeverity.CRITICAL, msg);
                toRole(e, Role.PLANNER, AlertSeverity.CRITICAL, msg);
            }
            default -> { /* other events (e.g. status changes) have no alert rule */ }
        }
    }

    private void toRole(DomainEvent e, Role role, AlertSeverity sev, String msg) {
        if (e.zoneId() == null) return;
        users.findByRoleAndZoneIdAndActiveTrue(role, e.zoneId())
                .forEach(u -> alerts.create(u.getId(), e.zoneId(), sev, e.type(), e.entityType(), e.entityId(), msg));
    }

    private void toUser(DomainEvent e, Long userId, AlertSeverity sev, String msg) {
        if (userId == null) return;
        alerts.create(userId, e.zoneId(), sev, e.type(), e.entityType(), e.entityId(), msg);
    }

    private static Long longOf(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }
}
