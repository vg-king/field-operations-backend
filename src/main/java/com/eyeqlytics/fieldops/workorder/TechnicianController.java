package com.eyeqlytics.fieldops.workorder;

import com.eyeqlytics.fieldops.identity.Role;
import com.eyeqlytics.fieldops.identity.UserRepository;
import com.eyeqlytics.fieldops.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Powers the planner's "who can I assign this to?" dropdown. */
@RestController
@RequestMapping("/api/v1/technicians")
@RequiredArgsConstructor
public class TechnicianController {

    private final UserRepository users;
    private final WorkOrderAssignmentRepository assignments;

    @Value("${app.workorder.max-active-per-technician}")
    private int capacity;

    public record TechnicianAvailability(Long id, String fullName, int activeWorkOrders, int capacity, boolean available) {}

    @GetMapping
    @PreAuthorize("hasAnyRole('PLANNER','SUPERVISOR','ADMIN')")
    public List<TechnicianAvailability> list(@RequestParam(required = false) Long zoneId,
                                             @AuthenticationPrincipal AuthUser actor) {
        Long zone = actor.isAdmin() ? zoneId : actor.zoneId();
        if (zone == null) return List.of();
        return users.findByRoleAndZoneIdAndActiveTrue(Role.TECHNICIAN, zone).stream().map(t -> {
            int load = (int) assignments.countActiveWorkload(t.getId(), WorkOrderStatus.ACTIVE_WORK);
            return new TechnicianAvailability(t.getId(), t.getFullName(), load, capacity, load < capacity);
        }).toList();
    }
}
