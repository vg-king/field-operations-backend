package com.eyeqlytics.fieldops.sla;

import com.eyeqlytics.fieldops.audit.AuditService;
import com.eyeqlytics.fieldops.events.DomainEvent;
import com.eyeqlytics.fieldops.events.EventPublisher;
import com.eyeqlytics.fieldops.workorder.WorkOrder;
import com.eyeqlytics.fieldops.workorder.WorkOrderAssignment;
import com.eyeqlytics.fieldops.workorder.WorkOrderAssignmentRepository;
import com.eyeqlytics.fieldops.workorder.WorkOrderRepository;
import com.eyeqlytics.fieldops.workorder.WorkOrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds work orders that missed their SLA and raises DELAYED, then ESCALATED events.
 *
 * Redis SETNX keys make every alert fire exactly once per SLA deadline, even if several app
 * instances run this job at the same time (whoever wins the key raises the event).
 *
 * Escalation rule: still not completed one full SLA-length after the deadline.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SlaMonitor {

    private static final List<WorkOrderStatus> NOT_OVERDUE_STATES =
            List.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.VERIFIED, WorkOrderStatus.CLOSED);

    private final WorkOrderRepository workOrders;
    private final WorkOrderAssignmentRepository assignments;
    private final StringRedisTemplate redis;
    private final EventPublisher events;
    private final AuditService audit;

    @Scheduled(fixedDelayString = "${app.sla.check-interval-ms:60000}", initialDelay = 30000)
    public void scheduledCheck() {
        int raised = check();
        if (raised > 0) log.info("SLA monitor raised {} alert event(s)", raised);
    }

    /** @return number of events raised in this run (also used by the admin "run now" endpoint) */
    public int check() {
        Instant now = Instant.now();
        int raised = 0;
        for (WorkOrder w : workOrders.findOverdueOpen(now, NOT_OVERDUE_STATES)) {
            long due = w.getSlaDueAt().toEpochMilli();
            Long techId = assignments.findByWorkOrderIdAndActiveTrue(w.getId())
                    .map(WorkOrderAssignment::getTechnicianId).orElse(null);

            if (firstTime("sla:delayed:" + w.getId() + ":" + due)) {
                audit.log("WORK_ORDER", w.getId(), "SLA_BREACHED", w.getStatus().name(), w.getStatus().name(),
                        null, "SLA deadline passed");
                publish("WORK_ORDER_DELAYED", w, techId);
                raised++;
            }

            Duration slaLength = Duration.between(w.getCreatedAt(), w.getSlaDueAt());
            if (slaLength.isNegative() || slaLength.isZero()) slaLength = Duration.ofHours(1);
            if (now.isAfter(w.getSlaDueAt().plus(slaLength)) && firstTime("sla:escalated:" + w.getId() + ":" + due)) {
                audit.log("WORK_ORDER", w.getId(), "ESCALATED", w.getStatus().name(), w.getStatus().name(),
                        null, "Still open one SLA period after the deadline");
                publish("WORK_ORDER_ESCALATED", w, techId);
                raised++;
            }
        }
        return raised;
    }

    private void publish(String type, WorkOrder w, Long techId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("technicianId", techId);
        payload.put("priority", w.getPriority().name());
        payload.put("dueAt", w.getSlaDueAt().toString());
        events.publish(DomainEvent.of(type, "WORK_ORDER", w.getId(), w.getZoneId(), payload));
    }

    private boolean firstTime(String key) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(2)));
    }
}
