package com.eyeqlytics.fieldops.alert;

import com.eyeqlytics.fieldops.common.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AlertService {

    private final AlertRepository repo;
    private final SseRegistry sse;

    /** Persists the alert (so it survives offline users) and pushes it live to connected browsers. */
    public void create(Long targetUserId, Long zoneId, AlertSeverity severity, String type,
                       String entityType, Long entityId, String message) {
        Alert a = new Alert();
        a.setTargetUserId(targetUserId);
        a.setZoneId(zoneId);
        a.setSeverity(severity);
        a.setType(type);
        a.setEntityType(entityType);
        a.setEntityId(entityId);
        a.setMessage(message.length() > 480 ? message.substring(0, 480) : message);
        repo.save(a);
        sse.push(targetUserId, AlertView.from(a));
    }

    @Transactional(readOnly = true)
    public Page<AlertView> list(Long userId, boolean unreadOnly, Pageable pageable) {
        Page<Alert> page = unreadOnly
                ? repo.findByTargetUserIdAndReadAtIsNullOrderByCreatedAtDesc(userId, pageable)
                : repo.findByTargetUserIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(AlertView::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repo.countByTargetUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public AlertView markRead(Long alertId, Long userId) {
        Alert a = repo.findByIdAndTargetUserId(alertId, userId)
                .orElseThrow(() -> ApiException.notFound("Alert", alertId));
        if (a.getReadAt() == null) a.setReadAt(Instant.now());
        return AlertView.from(a);
    }
}
