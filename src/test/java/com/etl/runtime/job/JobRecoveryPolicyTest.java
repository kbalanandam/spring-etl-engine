package com.etl.runtime.job;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobRecoveryPolicyTest {

    @Test
    void fromLogValueParsesCanonicalTokens() {
        assertEquals(Optional.of(JobRecoveryPolicy.RERUN_FROM_START),
                JobRecoveryPolicy.fromLogValue("rerun-from-start"));
        assertEquals(Optional.of(JobRecoveryPolicy.RESUME_FROM_CHECKPOINT),
                JobRecoveryPolicy.fromLogValue("resume-from-checkpoint"));
    }

    @Test
    void fromLogValueParsesShortAliases() {
        assertEquals(Optional.of(JobRecoveryPolicy.RERUN_FROM_START),
                JobRecoveryPolicy.fromLogValue("rerun"));
        assertEquals(Optional.of(JobRecoveryPolicy.RESUME_FROM_CHECKPOINT),
                JobRecoveryPolicy.fromLogValue("restart"));
    }

    @Test
    void fromLogValueReturnsEmptyForBlankOrUnknownValues() {
        assertTrue(JobRecoveryPolicy.fromLogValue(null).isEmpty());
        assertTrue(JobRecoveryPolicy.fromLogValue(" ").isEmpty());
        assertTrue(JobRecoveryPolicy.fromLogValue("restart-from-checkpoint").isEmpty());
    }
}

