package com.eyeqlytics.fieldops.workorder;

import com.eyeqlytics.fieldops.common.ApiException;
import com.eyeqlytics.fieldops.events.RedisLockService;
import com.eyeqlytics.fieldops.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;


@Component
@RequiredArgsConstructor
public class AssignmentFacade {

    private final RedisLockService locks;
    private final WorkOrderService workOrders;

    public WorkOrderView assign(Long workOrderId, Long technicianId, AuthUser actor) {
        String key = "assign:tech:" + technicianId;
        String token = locks.tryLock(key, Duration.ofSeconds(10));
        if (token == null)
            throw ApiException.conflict("TECH_BUSY", "Another assignment for this technician is in progress, retry shortly");
        try {
            return workOrders.assign(workOrderId, technicianId, actor);
        } finally {
            locks.unlock(key, token);
        }
    }
}
