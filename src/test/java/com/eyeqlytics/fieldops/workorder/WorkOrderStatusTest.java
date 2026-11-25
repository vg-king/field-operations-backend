package com.eyeqlytics.fieldops.workorder;

import org.junit.jupiter.api.Test;

import static com.eyeqlytics.fieldops.workorder.WorkOrderStatus.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrderStatusTest {

    @Test
    void happyPathIsAllowed() {
        assertTrue(CREATED.canTransitionTo(ASSIGNED));
        assertTrue(ASSIGNED.canTransitionTo(ACCEPTED));
        assertTrue(ACCEPTED.canTransitionTo(IN_PROGRESS));
        assertTrue(IN_PROGRESS.canTransitionTo(COMPLETED));
        assertTrue(COMPLETED.canTransitionTo(VERIFIED));
        assertTrue(VERIFIED.canTransitionTo(CLOSED));
    }

    @Test
    void supervisorRejectionSendsWorkBackToInProgress() {
        assertTrue(COMPLETED.canTransitionTo(IN_PROGRESS));
    }

    @Test
    void skippingStepsIsBlocked() {
        assertFalse(CREATED.canTransitionTo(COMPLETED));
        assertFalse(ASSIGNED.canTransitionTo(IN_PROGRESS));
        assertFalse(IN_PROGRESS.canTransitionTo(VERIFIED));
    }

    @Test
    void closedIsTerminal() {
        for (WorkOrderStatus s : values()) assertFalse(CLOSED.canTransitionTo(s));
    }
}
