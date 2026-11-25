package com.eyeqlytics.fieldops.dashboard;

import com.eyeqlytics.fieldops.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    /** Planners/supervisors always see their own zone; admins may pick a zone (or omit it for all zones). */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('PLANNER','SUPERVISOR','ADMIN')")
    public DashboardSummary summary(@RequestParam(required = false) Long zoneId,
                                    @AuthenticationPrincipal AuthUser actor) {
        return service.summary(actor.isAdmin() ? zoneId : actor.zoneId());
    }
}
