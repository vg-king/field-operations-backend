package com.eyeqlytics.fieldops.dashboard;

import java.time.Instant;
import java.util.Map;

/** One-call overview for the supervisor / planner board. */
public record DashboardSummary(Long zoneId,
                               Map<String, Long> workOrdersByStatus,
                               Map<String, Long> requestsByStatus,
                               long overdueWorkOrders,
                               long criticalActiveWorkOrders,
                               Instant generatedAt) { }
