package com.etl.controlplane.monitoring;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRunSummaryRegistryTest {

	@Test
	void returnsAdvisoryRecoveryViewForExistingRun() {
		InMemoryRunSummaryRegistry registry = new InMemoryRunSummaryRegistry();
		registry.upsert(new RunSummaryView(
				"customer-load",
				7001L,
				"COMPLETED",
				LocalDateTime.parse("2026-05-27T09:00:00"),
				LocalDateTime.parse("2026-05-27T09:01:00"),
				60L,
				10L,
				10L,
				0L,
				"explicit-job",
				"rerun-from-start",
				"logs/2026-05-27/customer-load.log"
		));

		RunRecoveryView recovery = registry.findRecoveryByJobExecutionId(7001L).orElseThrow();
		assertEquals(7001L, recovery.jobExecutionId());
		assertEquals("rr-7001", recovery.runRecordId());
		assertFalse(recovery.resumeSupported());
		assertEquals(RunRecoveryView.RESUME_BLOCKED_REASON_CHECKPOINT_NOT_SHIPPED, recovery.resumeBlockedReason());
		assertEquals(1, recovery.checkpointAnchors().size());
		assertEquals("ca-log-7001", recovery.checkpointAnchors().get(0).checkpointAnchorId());
	}

	@Test
	void returnsEmptyRecoveryWhenRunDoesNotExist() {
		InMemoryRunSummaryRegistry registry = new InMemoryRunSummaryRegistry();
		assertTrue(registry.findRecoveryByJobExecutionId(9999L).isEmpty());
	}
}

