package com.eyeqlytics.fieldops.request;

import com.eyeqlytics.fieldops.audit.AuditService;
import com.eyeqlytics.fieldops.common.ApiException;
import com.eyeqlytics.fieldops.common.Priority;
import com.eyeqlytics.fieldops.events.DomainEvent;
import com.eyeqlytics.fieldops.events.EventPublisher;
import com.eyeqlytics.fieldops.identity.Role;
import com.eyeqlytics.fieldops.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ServiceRequestService {

    private static final String TYPE = "SERVICE_REQUEST";

    private final ServiceRequestRepository repo;
    private final AuditService audit;
    private final EventPublisher events;

    @Transactional
    public ServiceRequest create(String title, String description, String address,
                                 Long zoneId, Priority priority, AuthUser actor) {
        ServiceRequest r = new ServiceRequest();
        r.setTitle(title);
        r.setDescription(description);
        r.setLocationAddress(address);
        r.setZoneId(zoneId);
        r.setPriority(priority);
        r.setStatus(RequestStatus.OPEN);
        r.setRequestedBy(actor.id());
        r.setSlaDueAt(Instant.now().plus(priority.sla()));
        repo.save(r);
        audit.log(TYPE, r.getId(), "CREATED", null, r.getStatus().name(), actor.id(), null);
        events.publish(DomainEvent.of("REQUEST_CREATED", TYPE, r.getId(), r.getZoneId(),
                Map.of("priority", priority.name(), "title", title)));
        return r;
    }

    @Transactional(readOnly = true)
    public ServiceRequest get(Long id, AuthUser actor) {
        ServiceRequest r = repo.findById(id).orElseThrow(() -> ApiException.notFound("ServiceRequest", id));
        if (actor.role() == Role.REQUESTER && !r.getRequestedBy().equals(actor.id()))
            throw ApiException.forbidden("You can only view your own requests");
        return r;
    }

    @Transactional(readOnly = true)
    public Page<ServiceRequest> search(RequestStatus status, Long zoneId, AuthUser actor, Pageable pageable) {
        Specification<ServiceRequest> spec = Specification.where(null);
        if (status != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        // Non-admins are always scoped to their own zone; requesters only see their own requests.
        Long effectiveZone = actor.isAdmin() ? zoneId : actor.zoneId();
        if (effectiveZone != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("zoneId"), effectiveZone));
        if (actor.role() == Role.REQUESTER)
            spec = spec.and((root, q, cb) -> cb.equal(root.get("requestedBy"), actor.id()));
        return repo.findAll(spec, pageable);
    }

    @Transactional
    public ServiceRequest triage(Long id, Priority newPriority, String remarks, AuthUser actor) {
        ServiceRequest r = load(id);
        assertZone(actor, r.getZoneId());
        if (r.getStatus() != RequestStatus.OPEN)
            throw ApiException.conflict("INVALID_STATE", "Only OPEN requests can be triaged (current: " + r.getStatus() + ")");
        if (newPriority != null && newPriority != r.getPriority()) {
            r.setPriority(newPriority);
            r.setSlaDueAt(r.getCreatedAt().plus(newPriority.sla()));
        }
        r.setStatus(RequestStatus.TRIAGED);
        audit.log(TYPE, id, "TRIAGED", "OPEN", "TRIAGED", actor.id(), remarks);
        return r;
    }

    @Transactional
    public ServiceRequest reject(Long id, String remarks, AuthUser actor) {
        ServiceRequest r = load(id);
        assertZone(actor, r.getZoneId());
        if (r.getStatus() != RequestStatus.OPEN && r.getStatus() != RequestStatus.TRIAGED)
            throw ApiException.conflict("INVALID_STATE", "Cannot reject a request in state " + r.getStatus());
        String from = r.getStatus().name();
        r.setStatus(RequestStatus.REJECTED);
        audit.log(TYPE, id, "REJECTED", from, "REJECTED", actor.id(), remarks);
        events.publish(DomainEvent.of("REQUEST_REJECTED", TYPE, id, r.getZoneId(), Map.of("remarks", remarks)));
        return r;
    }

    private ServiceRequest load(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("ServiceRequest", id));
    }


    static void assertZone(AuthUser actor, Long zoneId) {
        if (!actor.isAdmin() && !zoneId.equals(actor.zoneId()))
            throw ApiException.forbidden("This item belongs to a different zone");
    }
}
