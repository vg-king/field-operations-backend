package com.eyeqlytics.fieldops.request;

import com.eyeqlytics.fieldops.common.Priority;
import com.eyeqlytics.fieldops.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class ServiceRequestController {

    private final ServiceRequestService service;

    public record CreateRequest(@NotBlank String title, String description, String locationAddress,
                                @NotNull Long zoneId, @NotNull Priority priority) {}
    public record TriageRequest(Priority priority, String remarks) {}
    public record RejectRequest(@NotBlank String remarks) {}

    public record RequestResponse(Long id, String title, String description, String locationAddress,
                                  Long zoneId, Priority priority, RequestStatus status,
                                  Long requestedBy, Instant slaDueAt, Instant createdAt, Instant updatedAt) {
        static RequestResponse from(ServiceRequest r) {
            return new RequestResponse(r.getId(), r.getTitle(), r.getDescription(), r.getLocationAddress(),
                    r.getZoneId(), r.getPriority(), r.getStatus(), r.getRequestedBy(),
                    r.getSlaDueAt(), r.getCreatedAt(), r.getUpdatedAt());
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('REQUESTER','PLANNER','ADMIN')")
    public ResponseEntity<RequestResponse> create(@Valid @RequestBody CreateRequest req,
                                                  @AuthenticationPrincipal AuthUser actor) {
        ServiceRequest r = service.create(req.title(), req.description(), req.locationAddress(),
                req.zoneId(), req.priority(), actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(RequestResponse.from(r));
    }

    @GetMapping
    public Page<RequestResponse> list(@RequestParam(required = false) RequestStatus status,
                                      @RequestParam(required = false) Long zoneId,
                                      @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
                                      @AuthenticationPrincipal AuthUser actor) {
        return service.search(status, zoneId, actor, pageable).map(RequestResponse::from);
    }

    @GetMapping("/{id}")
    public RequestResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        return RequestResponse.from(service.get(id, actor));
    }

    @PostMapping("/{id}/triage")
    @PreAuthorize("hasAnyRole('PLANNER','ADMIN')")
    public RequestResponse triage(@PathVariable Long id, @RequestBody(required = false) TriageRequest req,
                                  @AuthenticationPrincipal AuthUser actor) {
        TriageRequest r = req == null ? new TriageRequest(null, null) : req;
        return RequestResponse.from(service.triage(id, r.priority(), r.remarks(), actor));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('PLANNER','ADMIN')")
    public RequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest req,
                                  @AuthenticationPrincipal AuthUser actor) {
        return RequestResponse.from(service.reject(id, req.remarks(), actor));
    }
}
