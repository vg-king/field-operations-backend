package com.eyeqlytics.fieldops.workorder;

import com.eyeqlytics.fieldops.audit.ActivityLog;
import com.eyeqlytics.fieldops.common.Priority;
import com.eyeqlytics.fieldops.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/work-orders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','PLANNER','SUPERVISOR','TECHNICIAN')")
public class WorkOrderController {

    private final WorkOrderService service;
    private final AssignmentFacade assignmentFacade;

    public record CreateRequest(@NotNull Long requestId) {}
    public record AssignRequest(@NotNull Long technicianId) {}
    public record StatusRequest(@NotNull WorkOrderStatus status, String remarks) {}
    public record VerifyRequest(@NotNull Boolean approved, String remarks) {}
    public record PriorityRequest(@NotNull Priority priority, String reason) {}
    public record HistoryEntry(Long id, String action, String fromStatus, String toStatus,
                               Long actorId, String remarks, Instant at) {
        static HistoryEntry from(ActivityLog l) {
            return new HistoryEntry(l.getId(), l.getAction(), l.getFromStatus(), l.getToStatus(),
                    l.getActorId(), l.getRemarks(), l.getCreatedAt());
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PLANNER','ADMIN')")
    public ResponseEntity<WorkOrderView> create(@Valid @RequestBody CreateRequest req,
                                                @AuthenticationPrincipal AuthUser actor) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req.requestId(), actor));
    }

    @GetMapping
    public Page<WorkOrderView> list(@RequestParam(required = false) WorkOrderStatus status,
                                    @RequestParam(required = false) Long zoneId,
                                    @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
                                    @AuthenticationPrincipal AuthUser actor) {
        return service.list(status, zoneId, actor, pageable);
    }

    @GetMapping("/{id}")
    public WorkOrderView get(@PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        return service.get(id, actor);
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('PLANNER','ADMIN')")
    public WorkOrderView assign(@PathVariable Long id, @Valid @RequestBody AssignRequest req,
                                @AuthenticationPrincipal AuthUser actor) {
        return assignmentFacade.assign(id, req.technicianId(), actor);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public WorkOrderView updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req,
                                      @AuthenticationPrincipal AuthUser actor) {
        return service.updateStatus(id, req.status(), req.remarks(), actor);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public WorkOrderView verify(@PathVariable Long id, @Valid @RequestBody VerifyRequest req,
                                @AuthenticationPrincipal AuthUser actor) {
        return service.verify(id, req.approved(), req.remarks(), actor);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public WorkOrderView close(@PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        return service.close(id, actor);
    }

    @PatchMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('PLANNER','SUPERVISOR','ADMIN')")
    public WorkOrderView changePriority(@PathVariable Long id, @Valid @RequestBody PriorityRequest req,
                                        @AuthenticationPrincipal AuthUser actor) {
        return service.changePriority(id, req.priority(), req.reason(), actor);
    }

    @GetMapping("/{id}/history")
    public List<HistoryEntry> history(@PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        return service.history(id, actor).stream().map(HistoryEntry::from).toList();
    }
}
