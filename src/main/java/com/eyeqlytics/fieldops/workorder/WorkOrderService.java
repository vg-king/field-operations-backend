package com.eyeqlytics.fieldops.workorder;

import com.eyeqlytics.fieldops.audit.ActivityLog;
import com.eyeqlytics.fieldops.audit.AuditService;
import com.eyeqlytics.fieldops.common.ApiException;
import com.eyeqlytics.fieldops.common.Priority;
import com.eyeqlytics.fieldops.events.DomainEvent;
import com.eyeqlytics.fieldops.events.EventPublisher;
import com.eyeqlytics.fieldops.identity.Role;
import com.eyeqlytics.fieldops.identity.User;
import com.eyeqlytics.fieldops.identity.UserRepository;
import com.eyeqlytics.fieldops.request.RequestStatus;
import com.eyeqlytics.fieldops.request.ServiceRequest;
import com.eyeqlytics.fieldops.request.ServiceRequestRepository;
import com.eyeqlytics.fieldops.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkOrderService {

    static final String TYPE = "WORK_ORDER";
    private static final Set<WorkOrderStatus> TECH_ALLOWED =
            Set.of(WorkOrderStatus.ACCEPTED, WorkOrderStatus.IN_PROGRESS, WorkOrderStatus.COMPLETED);

    private final WorkOrderRepository workOrders;
    private final WorkOrderAssignmentRepository assignments;
    private final ServiceRequestRepository requests;
    private final UserRepository users;
    private final AuditService audit;
    private final EventPublisher events;

    @Value("${app.workorder.max-active-per-technician}")
    private int maxActivePerTechnician;

    // ------------------------------------------------------------------ planning

    @Transactional
    public WorkOrderView create(Long requestId, AuthUser actor) {
        ServiceRequest r = requests.findById(requestId)
                .orElseThrow(() -> ApiException.notFound("ServiceRequest", requestId));
        assertZone(actor, r.getZoneId());
        if (r.getStatus() != RequestStatus.TRIAGED)
            throw ApiException.conflict("INVALID_STATE",
                    "Only TRIAGED requests can be converted to a work order (current: " + r.getStatus() + ")");

        WorkOrder wo = new WorkOrder();
        wo.setRequestId(r.getId());
        wo.setTitle(r.getTitle());
        wo.setDescription(r.getDescription());
        wo.setPriority(r.getPriority());
        wo.setZoneId(r.getZoneId());
        wo.setPlannedBy(actor.id());
        wo.setSlaDueAt(r.getSlaDueAt());
        wo.setStatus(WorkOrderStatus.CREATED);
        workOrders.save(wo);

        r.setStatus(RequestStatus.CONVERTED_TO_WO);
        audit.log("SERVICE_REQUEST", r.getId(), "CONVERTED_TO_WO", "TRIAGED", "CONVERTED_TO_WO", actor.id(), "workOrderId=" + wo.getId());
        audit.log(TYPE, wo.getId(), "CREATED", null, "CREATED", actor.id(), "from request " + r.getId());
        events.publish(DomainEvent.of("WORK_ORDER_CREATED", TYPE, wo.getId(), wo.getZoneId(),
                Map.of("priority", wo.getPriority().name())));
        return view(wo);
    }

    /**
     * Must be called through {@link AssignmentFacade}, which holds the per-technician Redis lock
     * AROUND this transaction (so the lock is released only after commit).
     */
    @Transactional
    public WorkOrderView assign(Long workOrderId, Long technicianId, AuthUser actor) {
        WorkOrder wo = find(workOrderId);
        assertZone(actor, wo.getZoneId());
        if (wo.getStatus() != WorkOrderStatus.CREATED && wo.getStatus() != WorkOrderStatus.ASSIGNED)
            throw ApiException.conflict("INVALID_STATE",
                    "Can only (re)assign CREATED or ASSIGNED orders (current: " + wo.getStatus() + ")");

        User tech = users.findById(technicianId)
                .orElseThrow(() -> ApiException.notFound("Technician", technicianId));
        if (tech.getRole() != Role.TECHNICIAN || !tech.isActive())
            throw ApiException.badRequest("NOT_A_TECHNICIAN", "User is not an active technician");
        if (!wo.getZoneId().equals(tech.getZoneId()))
            throw ApiException.badRequest("TECH_WRONG_ZONE", "Technician belongs to a different zone");

        Optional<WorkOrderAssignment> current = assignments.findByWorkOrderIdAndActiveTrue(workOrderId);
        if (current.isPresent() && current.get().getTechnicianId().equals(technicianId))
            throw ApiException.conflict("ALREADY_ASSIGNED", "Work order is already assigned to this technician");

        long load = assignments.countActiveWorkload(technicianId, WorkOrderStatus.ACTIVE_WORK);
        if (load >= maxActivePerTechnician)
            throw ApiException.conflict("TECH_OVERLOADED",
                    "Technician already has " + load + " active work orders (limit " + maxActivePerTechnician + ")");

        current.ifPresent(a -> {
            a.setActive(false);
            // flush first: the partial unique index allows only one ACTIVE row per work order
            assignments.saveAndFlush(a);
        });

        WorkOrderAssignment a = new WorkOrderAssignment();
        a.setWorkOrderId(workOrderId);
        a.setTechnicianId(technicianId);
        a.setAssignedBy(actor.id());
        assignments.save(a);

        String from = wo.getStatus().name();
        wo.setStatus(WorkOrderStatus.ASSIGNED);
        audit.log(TYPE, workOrderId, current.isPresent() ? "REASSIGNED" : "ASSIGNED",
                from, "ASSIGNED", actor.id(), "technicianId=" + technicianId);
        events.publish(DomainEvent.of("WORK_ORDER_ASSIGNED", TYPE, workOrderId, wo.getZoneId(),
                Map.of("technicianId", technicianId, "priority", wo.getPriority().name())));
        return WorkOrderView.of(wo, technicianId);
    }

    @Transactional
    public WorkOrderView changePriority(Long id, Priority newPriority, String reason, AuthUser actor) {
        WorkOrder wo = find(id);
        assertZone(actor, wo.getZoneId());
        if (wo.getStatus() == WorkOrderStatus.VERIFIED || wo.getStatus() == WorkOrderStatus.CLOSED)
            throw ApiException.conflict("INVALID_STATE", "Cannot change priority of a " + wo.getStatus() + " order");
        Priority old = wo.getPriority();
        if (old == newPriority) return view(wo);
        wo.setPriority(newPriority);
        wo.setSlaDueAt(wo.getCreatedAt().plus(newPriority.sla()));
        audit.log(TYPE, id, "PRIORITY_CHANGED", wo.getStatus().name(), wo.getStatus().name(), actor.id(),
                old + " -> " + newPriority + (reason == null ? "" : " | " + reason));
        Long techId = assignments.findByWorkOrderIdAndActiveTrue(id).map(WorkOrderAssignment::getTechnicianId).orElse(null);
        Map<String, Object> payload = new HashMap<>();
        payload.put("from", old.name());
        payload.put("to", newPriority.name());
        payload.put("technicianId", techId);
        events.publish(DomainEvent.of("PRIORITY_CHANGED", TYPE, id, wo.getZoneId(), payload));
        return WorkOrderView.of(wo, techId);
    }

    // ------------------------------------------------------------------ execution

    @Transactional
    public WorkOrderView updateStatus(Long id, WorkOrderStatus target, String remarks, AuthUser actor) {
        WorkOrder wo = find(id);
        WorkOrderAssignment a = assignments.findByWorkOrderIdAndActiveTrue(id)
                .orElseThrow(() -> ApiException.conflict("NOT_ASSIGNED", "Work order has no assigned technician"));
        if (!a.getTechnicianId().equals(actor.id()))
            throw ApiException.forbidden("This work order is assigned to another technician");
        if (!TECH_ALLOWED.contains(target))
            throw ApiException.badRequest("STATUS_NOT_ALLOWED", "Technicians can only set: " + TECH_ALLOWED);
        if (target == WorkOrderStatus.COMPLETED) {
            if (remarks == null || remarks.isBlank())
                throw ApiException.badRequest("REMARKS_REQUIRED", "Completion notes are required");
            wo.setCompletedAt(Instant.now());
        }
        transition(wo, target, actor, remarks);
        return WorkOrderView.of(wo, a.getTechnicianId());
    }

    // ------------------------------------------------------------------ verification

    @Transactional
    public WorkOrderView verify(Long id, boolean approved, String remarks, AuthUser actor) {
        WorkOrder wo = find(id);
        assertZone(actor, wo.getZoneId());
        if (wo.getStatus() != WorkOrderStatus.COMPLETED)
            throw ApiException.conflict("INVALID_STATE", "Only COMPLETED orders can be verified (current: " + wo.getStatus() + ")");
        if (approved) {
            transition(wo, WorkOrderStatus.VERIFIED, actor, remarks);
        } else {
            if (remarks == null || remarks.isBlank())
                throw ApiException.badRequest("REMARKS_REQUIRED", "Explain why the work is rejected");
            wo.setCompletedAt(null);
            transition(wo, WorkOrderStatus.IN_PROGRESS, actor, "REJECTED BY SUPERVISOR: " + remarks);
            Long techId = assignments.findByWorkOrderIdAndActiveTrue(id)
                    .map(WorkOrderAssignment::getTechnicianId).orElse(null);
            Map<String, Object> rejection = new HashMap<>();
            rejection.put("technicianId", techId);
            rejection.put("remarks", remarks);
            events.publish(DomainEvent.of("WORK_ORDER_REJECTED", TYPE, id, wo.getZoneId(), rejection));
        }
        return view(wo);
    }

    @Transactional
    public WorkOrderView close(Long id, AuthUser actor) {
        WorkOrder wo = find(id);
        assertZone(actor, wo.getZoneId());
        transition(wo, WorkOrderStatus.CLOSED, actor, null);
        requests.findById(wo.getRequestId()).ifPresent(r -> {
            r.setStatus(RequestStatus.CLOSED);
            audit.log("SERVICE_REQUEST", r.getId(), "CLOSED", "CONVERTED_TO_WO", "CLOSED", actor.id(), "work order closed");
        });
        return view(wo);
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public WorkOrderView get(Long id, AuthUser actor) {
        WorkOrder wo = find(id);
        authorizeView(wo, actor);
        return view(wo);
    }

    @Transactional(readOnly = true)
    public Page<WorkOrderView> list(WorkOrderStatus status, Long zoneId, AuthUser actor, Pageable pageable) {
        Page<WorkOrder> page;
        if (actor.role() == Role.TECHNICIAN) {
            page = workOrders.findAssignedTo(actor.id(), pageable);   // technicians only ever see their own jobs
        } else {
            Specification<WorkOrder> spec = Specification.where(null);
            if (status != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
            Long effectiveZone = actor.isAdmin() ? zoneId : actor.zoneId();
            if (effectiveZone != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("zoneId"), effectiveZone));
            page = workOrders.findAll(spec, pageable);
        }
        // one query for all technicians on this page (avoids N+1)
        Map<Long, Long> techByOrder = assignments
                .findByWorkOrderIdInAndActiveTrue(page.getContent().stream().map(WorkOrder::getId).toList())
                .stream().collect(Collectors.toMap(WorkOrderAssignment::getWorkOrderId, WorkOrderAssignment::getTechnicianId));
        return page.map(w -> WorkOrderView.of(w, techByOrder.get(w.getId())));
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> history(Long id, AuthUser actor) {
        authorizeView(find(id), actor);
        return audit.history(TYPE, id);
    }

    // ------------------------------------------------------------------ helpers

    private void transition(WorkOrder wo, WorkOrderStatus target, AuthUser actor, String remarks) {
        WorkOrderStatus from = wo.getStatus();
        if (!from.canTransitionTo(target))
            throw ApiException.conflict("INVALID_TRANSITION", "Cannot move work order from " + from + " to " + target);
        wo.setStatus(target);
        audit.log(TYPE, wo.getId(), "STATUS_" + target, from.name(), target.name(), actor.id(), remarks);
        events.publish(DomainEvent.of("WORK_ORDER_" + target, TYPE, wo.getId(), wo.getZoneId(),
                Map.of("from", from.name(), "to", target.name())));
    }

    private WorkOrder find(Long id) {
        return workOrders.findById(id).orElseThrow(() -> ApiException.notFound("WorkOrder", id));
    }

    private WorkOrderView view(WorkOrder wo) {
        Long techId = assignments.findByWorkOrderIdAndActiveTrue(wo.getId())
                .map(WorkOrderAssignment::getTechnicianId).orElse(null);
        return WorkOrderView.of(wo, techId);
    }

    private void authorizeView(WorkOrder wo, AuthUser actor) {
        if (actor.role() == Role.TECHNICIAN) {
            boolean mine = assignments.findByWorkOrderIdAndActiveTrue(wo.getId())
                    .map(a -> a.getTechnicianId().equals(actor.id())).orElse(false);
            if (!mine) throw ApiException.forbidden("This work order is not assigned to you");
        } else {
            assertZone(actor, wo.getZoneId());
        }
    }

    /** Zone-level access control: planners/supervisors only act inside their own zone. */
    private static void assertZone(AuthUser actor, Long zoneId) {
        if (!actor.isAdmin() && !zoneId.equals(actor.zoneId()))
            throw ApiException.forbidden("This item belongs to a different zone");
    }
}
