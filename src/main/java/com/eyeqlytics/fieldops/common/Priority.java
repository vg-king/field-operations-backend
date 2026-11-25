package com.eyeqlytics.fieldops.common;

import java.time.Duration;

public enum Priority {
    LOW(168), MEDIUM(72), HIGH(24), CRITICAL(4);

    private final int slaHours;
    Priority(int slaHours) { this.slaHours = slaHours; }
    public Duration sla() { return Duration.ofHours(slaHours); }
}
