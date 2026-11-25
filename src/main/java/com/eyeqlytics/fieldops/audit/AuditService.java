package com.eyeqlytics.fieldops.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final ActivityLogRepository repo;


    public void log(String entityType, Long entityId, String action,
                    String from, String to, Long actorId, String remarks) {
        ActivityLog l = new ActivityLog();
        l.setEntityType(entityType);
        l.setEntityId(entityId);
        l.setAction(action);
        l.setFromStatus(from);
        l.setToStatus(to);
        l.setActorId(actorId);
        l.setRemarks(remarks);
        repo.save(l);
    }

    public List<ActivityLog> history(String entityType, Long entityId) {
        return repo.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId);
    }
}
