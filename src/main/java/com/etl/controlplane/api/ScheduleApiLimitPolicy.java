package com.etl.controlplane.api;

/**
 * Centralizes schedule API request-limit and enabled-default policies.
 */
final class ScheduleApiLimitPolicy {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 200;
    private static final int DEFAULT_TRIGGER_EVENT_LIMIT = 20;
    private static final int MAX_TRIGGER_EVENT_LIMIT = 200;

    int scheduleLimit(Integer requestedLimit) {
        return clampLimit(requestedLimit, DEFAULT_LIMIT, MAX_LIMIT);
    }

    int triggerEventLimit(Integer requestedLimit) {
        return clampLimit(requestedLimit, DEFAULT_TRIGGER_EVENT_LIMIT, MAX_TRIGGER_EVENT_LIMIT);
    }

    boolean resolveEnabledDefault(Boolean enabled) {
        return enabled == null || enabled;
    }

    private int clampLimit(Integer requestedLimit, int defaultLimit, int maxLimit) {
        if (requestedLimit == null) {
            return defaultLimit;
        }
        return Math.max(1, Math.min(requestedLimit, maxLimit));
    }
}

