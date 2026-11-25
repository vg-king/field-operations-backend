package com.eyeqlytics.fieldops.alert;

import com.eyeqlytics.fieldops.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService service;
    private final SseRegistry sse;

    /** Polling fallback / history: my alerts, newest first. */
    @GetMapping
    public Page<AlertView> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                @PageableDefault(size = 20) Pageable pageable,
                                @AuthenticationPrincipal AuthUser actor) {
        return service.list(actor.id(), unreadOnly, pageable);
    }

    /** Cheap endpoint for the notification-bell badge. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthUser actor) {
        return Map.of("unread", service.unreadCount(actor.id()));
    }

    @PostMapping("/{id}/read")
    public AlertView markRead(@PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        return service.markRead(id, actor.id());
    }

    /**
     * Real-time channel (Server-Sent Events). Event names: "connected", "alert".
     * Browsers' native EventSource cannot send an Authorization header, so the frontend
     * should use a fetch-based SSE client (e.g. @microsoft/fetch-event-source).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@AuthenticationPrincipal AuthUser actor) {
        return sse.register(actor.id());
    }
}
