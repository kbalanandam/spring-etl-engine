package com.etl.controlplane.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleApiLimitPolicyTest {

    private final ScheduleApiLimitPolicy policy = new ScheduleApiLimitPolicy();

    @Test
    void returnsDefaultScheduleLimitWhenRequestIsNull() {
        assertEquals(25, policy.scheduleLimit(null));
    }

    @Test
    void clampsScheduleLimitToValidRange() {
        assertEquals(1, policy.scheduleLimit(-10));
        assertEquals(200, policy.scheduleLimit(999));
        assertEquals(50, policy.scheduleLimit(50));
    }

    @Test
    void returnsDefaultTriggerEventLimitWhenRequestIsNull() {
        assertEquals(20, policy.triggerEventLimit(null));
    }

    @Test
    void clampsTriggerEventLimitToValidRange() {
        assertEquals(1, policy.triggerEventLimit(0));
        assertEquals(200, policy.triggerEventLimit(500));
        assertEquals(42, policy.triggerEventLimit(42));
    }

    @Test
    void resolvesEnabledDefaultToTrueWhenMissing() {
        assertTrue(policy.resolveEnabledDefault(null));
        assertTrue(policy.resolveEnabledDefault(true));
        assertFalse(policy.resolveEnabledDefault(false));
    }
}

