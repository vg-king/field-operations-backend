package com.eyeqlytics.fieldops.sla;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class SlaAdminController {

    private final SlaMonitor monitor;

    /** Runs the SLA check immediately (the scheduler runs it every minute anyway). Handy for demos. */
    @PostMapping("/sla-check")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Integer> runNow() {
        return Map.of("eventsRaised", monitor.check());
    }
}
