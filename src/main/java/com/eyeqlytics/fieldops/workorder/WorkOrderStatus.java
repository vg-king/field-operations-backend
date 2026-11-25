package com.eyeqlytics.fieldops.workorder;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


public enum WorkOrderStatus {
    CREATED, ASSIGNED, ACCEPTED, IN_PROGRESS, COMPLETED, VERIFIED, CLOSED;

    private static final Map<WorkOrderStatus, Set<WorkOrderStatus>> NEXT = new EnumMap<>(WorkOrderStatus.class);
    static {
        NEXT.put(CREATED, EnumSet.of(ASSIGNED));
        NEXT.put(ASSIGNED, EnumSet.of(ACCEPTED));
        NEXT.put(ACCEPTED, EnumSet.of(IN_PROGRESS));
        NEXT.put(IN_PROGRESS, EnumSet.of(COMPLETED));
        NEXT.put(COMPLETED, EnumSet.of(VERIFIED, IN_PROGRESS));
        NEXT.put(VERIFIED, EnumSet.of(CLOSED));
        NEXT.put(CLOSED, EnumSet.noneOf(WorkOrderStatus.class));
    }

    public boolean canTransitionTo(WorkOrderStatus target) {
        return NEXT.get(this).contains(target);
    }


    public static final Set<WorkOrderStatus> ACTIVE_WORK = EnumSet.of(ASSIGNED, ACCEPTED, IN_PROGRESS);
}
