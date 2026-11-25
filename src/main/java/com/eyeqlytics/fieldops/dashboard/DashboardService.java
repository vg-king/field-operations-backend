package com.eyeqlytics.fieldops.dashboard;

import com.eyeqlytics.fieldops.common.Priority;
import com.eyeqlytics.fieldops.request.RequestStatus;
import com.eyeqlytics.fieldops.workorder.WorkOrderStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregations are the same for every supervisor in a zone and are requested often (a live board
 * that refreshes), so the result is cached in Redis for a short TTL. If Redis is down we simply
 * compute from the database - the cache is an optimisation, never a dependency.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private static final Duration TTL = Duration.ofSeconds(30);
    private static final List<WorkOrderStatus> FINISHED =
            List.of(WorkOrderStatus.COMPLETED, WorkOrderStatus.VERIFIED, WorkOrderStatus.CLOSED);

    @PersistenceContext
    private EntityManager em;

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public DashboardSummary summary(Long zoneId) {
        String key = "dashboard:zone:" + (zoneId == null ? "all" : zoneId);
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) return mapper.readValue(cached, DashboardSummary.class);
        } catch (Exception e) {
            log.warn("Dashboard cache read failed: {}", e.getMessage());
        }
        DashboardSummary fresh = compute(zoneId);
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(fresh), TTL);
        } catch (Exception e) {
            log.warn("Dashboard cache write failed: {}", e.getMessage());
        }
        return fresh;
    }

    private DashboardSummary compute(Long zoneId) {
        String where = zoneId == null ? "" : " where x.zoneId = :zone";
        String and = zoneId == null ? "" : " and x.zoneId = :zone";

        Map<String, Long> byStatus = zeroFilled(WorkOrderStatus.values());
        for (Object[] r : grouped("select x.status, count(x) from WorkOrder x" + where + " group by x.status", zoneId))
            byStatus.put(((WorkOrderStatus) r[0]).name(), (Long) r[1]);

        Map<String, Long> reqByStatus = zeroFilled(RequestStatus.values());
        for (Object[] r : grouped("select x.status, count(x) from ServiceRequest x" + where + " group by x.status", zoneId))
            reqByStatus.put(((RequestStatus) r[0]).name(), (Long) r[1]);

        long overdue = count("select count(x) from WorkOrder x where x.slaDueAt < :now and x.status not in :finished" + and,
                zoneId, null);
        long critical = count("select count(x) from WorkOrder x where x.priority = :priority and x.status not in :finished" + and,
                zoneId, Priority.CRITICAL);

        return new DashboardSummary(zoneId, byStatus, reqByStatus, overdue, critical, Instant.now());
    }

    private List<Object[]> grouped(String jpql, Long zoneId) {
        TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class);
        if (zoneId != null) q.setParameter("zone", zoneId);
        return q.getResultList();
    }

    private long count(String jpql, Long zoneId, Priority priority) {
        TypedQuery<Long> q = em.createQuery(jpql, Long.class);
        if (zoneId != null) q.setParameter("zone", zoneId);
        if (jpql.contains(":now")) q.setParameter("now", Instant.now());
        if (jpql.contains(":finished")) q.setParameter("finished", FINISHED);
        if (priority != null) q.setParameter("priority", priority);
        return q.getSingleResult();
    }

    private static <E extends Enum<E>> Map<String, Long> zeroFilled(E[] values) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (E v : values) m.put(v.name(), 0L);
        return m;
    }
}
