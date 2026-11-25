package com.eyeqlytics.fieldops.common;

import java.time.Instant;

public record ApiError(Instant timestamp, int status, String code, String message, Object details) {
    public static ApiError of(int status, String code, String message, Object details) {
        return new ApiError(Instant.now(), status, code, message, details);
    }
}
