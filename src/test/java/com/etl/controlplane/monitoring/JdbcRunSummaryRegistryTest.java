package com.etl.controlplane.monitoring;

import com.etl.controlplane.triggers.JdbcTriggerEventRegistry;
import com.etl.controlplane.triggers.TriggerEventView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcRunSummaryRegistryTest {

	@TempDir
	Path tempDir;

	@Test
	void upsertsAndReadsLatestRuns() {
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(new JdbcTemplate(inMemoryDataSource()), 100);
		registry.upsert(run(1001L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));
		registry.upsert(run(1002L, "customer-delta", LocalDateTime.parse("2026-05-27T10:00:00"), "FAILED"));

		List<RunSummaryView> runs = registry.latestRuns(10);
		assertEquals(2, runs.size());
		assertEquals(1002L, runs.get(0).jobExecutionId());
		assertEquals("FAILED", runs.get(0).status());
	}

	@Test
	void updatesExistingRunOnSameJobExecutionId() {
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(new JdbcTemplate(inMemoryDataSource()), 100);
		registry.upsert(run(1001L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "STARTED"));
		registry.upsert(run(1001L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		RunSummaryView run = registry.findByJobExecutionId(1001L).orElseThrow();
		assertEquals("COMPLETED", run.status());
		assertEquals(1, registry.latestRuns(10).size());
	}

	@Test
	void writesRunRecordFoundationRowsAlongsideRunSummary() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		registry.upsert(run(2001L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		Long recordCount = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_run_record where job_execution_id = ?",
				Long.class,
				2001L
		);
		String runRecordId = jdbcTemplate.queryForObject(
				"select run_record_id from controlplane_run_record where job_execution_id = ?",
				String.class,
				2001L
		);
		String selectedJobKey = jdbcTemplate.queryForObject(
				"select selected_job_key from controlplane_run_record where job_execution_id = ?",
				String.class,
				2001L
		);
		Long runRecordPk = jdbcTemplate.queryForObject(
				"select run_record_pk from controlplane_run_record where job_execution_id = ?",
				Long.class,
				2001L
		);
		assertEquals(1L, recordCount);
		assertEquals("rr-2001", runRecordId);
		assertEquals("customer-load", selectedJobKey);
		assertEquals(1L, runRecordPk);
	}

	@Test
	void persistsRunModeAndRecoveryPolicyInRunSummaryAndRunRecord() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		registry.upsert(new RunSummaryView(
				"customer-load",
				2450L,
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

		String summaryRunMode = jdbcTemplate.queryForObject(
				"select run_mode from controlplane_run_summary where job_execution_id = ?",
				String.class,
				2450L
		);
		String summaryRecoveryPolicy = jdbcTemplate.queryForObject(
				"select recovery_policy from controlplane_run_summary where job_execution_id = ?",
				String.class,
				2450L
		);
		String recordRunMode = jdbcTemplate.queryForObject(
				"select run_mode from controlplane_run_record where job_execution_id = ?",
				String.class,
				2450L
		);
		String recordRecoveryPolicy = jdbcTemplate.queryForObject(
				"select recovery_policy from controlplane_run_record where job_execution_id = ?",
				String.class,
				2450L
		);

		assertEquals("explicit-job", summaryRunMode);
		assertEquals("rerun-from-start", summaryRecoveryPolicy);
		assertEquals("explicit-job", recordRunMode);
		assertEquals("rerun-from-start", recordRecoveryPolicy);
	}

	@Test
	void writesMinimalS4cAttemptLinkAndCheckpointAnchorRows() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		registry.upsert(run(2101L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		String attemptLinkId = jdbcTemplate.queryForObject(
				"select attempt_link_id from controlplane_attempt_link where run_record_id = ?",
				String.class,
				"rr-2101"
		);
		String linkKind = jdbcTemplate.queryForObject(
				"select link_kind from controlplane_attempt_link where run_record_id = ?",
				String.class,
				"rr-2101"
		);
		String priorRunRecordId = jdbcTemplate.queryForObject(
				"select prior_run_record_id from controlplane_attempt_link where run_record_id = ?",
				String.class,
				"rr-2101"
		);
		String checkpointAnchorId = jdbcTemplate.queryForObject(
				"select checkpoint_anchor_id from controlplane_checkpoint_anchor where run_record_id = ?",
				String.class,
				"rr-2101"
		);
		String anchorKind = jdbcTemplate.queryForObject(
				"select anchor_kind from controlplane_checkpoint_anchor where run_record_id = ?",
				String.class,
				"rr-2101"
		);
		String anchorRef = jdbcTemplate.queryForObject(
				"select anchor_ref from controlplane_checkpoint_anchor where run_record_id = ?",
				String.class,
				"rr-2101"
		);

		assertEquals("al-2101", attemptLinkId);
		assertEquals("INITIAL", linkKind);
		assertNull(priorRunRecordId);
		assertEquals("ca-log-2101", checkpointAnchorId);
		assertEquals("RUN_LOG", anchorKind);
		assertEquals("logs/2026-05-27/customer-load.log", anchorRef);
	}

	@Test
	void marksLaterRunAsRerunAndLinksPriorRunRecord() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		registry.upsert(run(2201L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));
		registry.upsert(run(2202L, "customer-load", LocalDateTime.parse("2026-05-27T10:00:00"), "COMPLETED"));

		String linkKind = jdbcTemplate.queryForObject(
				"select link_kind from controlplane_attempt_link where run_record_id = ?",
				String.class,
				"rr-2202"
		);
		String priorRunRecordId = jdbcTemplate.queryForObject(
				"select prior_run_record_id from controlplane_attempt_link where run_record_id = ?",
				String.class,
				"rr-2202"
		);

		assertEquals("RERUN", linkKind);
		assertEquals("rr-2201", priorRunRecordId);
	}

	@Test
	void readsAdvisoryRecoveryViewFromAttemptLinksAndCheckpointAnchors() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		registry.upsert(run(2401L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "FAILED"));
		registry.upsert(run(2402L, "customer-load", LocalDateTime.parse("2026-05-27T10:00:00"), "COMPLETED"));

		RunRecoveryView recovery = registry.findRecoveryByJobExecutionId(2402L).orElseThrow();

		assertEquals(2402L, recovery.jobExecutionId());
		assertEquals("rr-2402", recovery.runRecordId());
		assertEquals("al-2402", recovery.attemptLinkId());
		assertEquals("RERUN", recovery.linkKind());
		assertEquals("rr-2401", recovery.priorRunRecordId());
		assertEquals(2401L, recovery.priorJobExecutionId());
		assertFalse(recovery.resumeSupported());
		assertNotNull(recovery.resumeBlockedReason());
		assertEquals(1, recovery.checkpointAnchors().size());
		assertEquals("ca-log-2402", recovery.checkpointAnchors().get(0).checkpointAnchorId());
		assertEquals("RUN_LOG", recovery.checkpointAnchors().get(0).anchorKind());
	}

	@Test
	void returnsRecoveryViewWithoutAttemptLinkWhenRunRecordExists() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		registry.upsert(run(2403L, "customer-load", LocalDateTime.parse("2026-05-27T11:00:00"), "COMPLETED"));
		jdbcTemplate.update("delete from controlplane_attempt_link where run_record_id = ?", "rr-2403");

		RunRecoveryView recovery = registry.findRecoveryByJobExecutionId(2403L).orElseThrow();

		assertEquals("rr-2403", recovery.runRecordId());
		assertNull(recovery.attemptLinkId());
		assertFalse(recovery.resumeSupported());
		assertEquals(1, recovery.checkpointAnchors().size());
	}

	@Test
	void keepsRunSummaryUpsertWorkingWhenS4cTablesAreMissing() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		jdbcTemplate.execute("drop table controlplane_attempt_link");
		jdbcTemplate.execute("drop table controlplane_checkpoint_anchor");

		assertDoesNotThrow(() -> registry.upsert(run(2301L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED")));
		RunSummaryView run = registry.findByJobExecutionId(2301L).orElseThrow();
		assertEquals("customer-load", run.scenario());
	}

	@Test
	void usesBigintTypeForRunRecordSurrogateAndLinkageColumns() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		String runRecordPkType = jdbcTemplate.queryForObject(
				"select type from pragma_table_info('controlplane_run_record') where lower(name) = 'run_record_pk'",
				String.class
		);
		String triggerEventPkType = jdbcTemplate.queryForObject(
				"select type from pragma_table_info('controlplane_run_record') where lower(name) = 'trigger_event_pk'",
				String.class
		);
		assertEquals("bigint", runRecordPkType == null ? "" : runRecordPkType.toLowerCase());
		assertEquals("bigint", triggerEventPkType == null ? "" : triggerEventPkType.toLowerCase());
	}

	@Test
	void usesRunRecordPkAsPrimaryKeyAndKeepsRunRecordIdUnique() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"select lower(name) as name, pk from pragma_table_info('controlplane_run_record') where lower(name) in ('run_record_pk', 'run_record_id')"
		);
		Map<String, Integer> pkFlags = new java.util.HashMap<>();
		for (Map<String, Object> row : rows) {
			pkFlags.put(String.valueOf(row.get("name")), ((Number) row.get("pk")).intValue());
		}

		assertEquals(1, pkFlags.getOrDefault("run_record_pk", 0));
		assertEquals(0, pkFlags.getOrDefault("run_record_id", 0));
	}

	@Test
	void migratesLegacyRunRecordIdPrimaryKeyShape() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		jdbcTemplate.execute("""
				create table controlplane_run_record (
					run_record_pk bigint,
					run_record_id varchar(80) primary key,
					job_execution_id bigint not null unique,
					trigger_event_pk bigint,
					trigger_event_id varchar(80),
					selected_job_key varchar(200),
					scenario varchar(200) not null,
					run_status varchar(50) not null,
					started_at timestamp,
					finished_at timestamp,
					duration_seconds bigint,
					source_count bigint,
					written_count bigint,
					rejected_count bigint,
					created_at timestamp not null,
					updated_at timestamp not null
				)
				""");
		jdbcTemplate.update("""
				insert into controlplane_run_record (
					run_record_pk, run_record_id, job_execution_id, trigger_event_pk, trigger_event_id,
					selected_job_key, scenario, run_status, started_at, finished_at,
					duration_seconds, source_count, written_count, rejected_count, created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				23L, "rr-legacy", 7001L, null, null,
				"customer-load", "customer-load", "COMPLETED", java.sql.Timestamp.valueOf("2026-05-27 09:00:00"), java.sql.Timestamp.valueOf("2026-05-27 09:05:00"),
				300L, 10L, 10L, 0L, java.sql.Timestamp.valueOf("2026-05-27 09:00:00"), java.sql.Timestamp.valueOf("2026-05-27 09:05:00")
		);

		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		Integer runRecordPkPkFlag = jdbcTemplate.queryForObject(
				"select pk from pragma_table_info('controlplane_run_record') where lower(name) = 'run_record_pk'",
				Integer.class
		);
		Integer runRecordIdPkFlag = jdbcTemplate.queryForObject(
				"select pk from pragma_table_info('controlplane_run_record') where lower(name) = 'run_record_id'",
				Integer.class
		);
		assertEquals(1, runRecordPkPkFlag == null ? 0 : runRecordPkPkFlag);
		assertEquals(0, runRecordIdPkFlag == null ? 0 : runRecordIdPkFlag);

		Long migratedPk = jdbcTemplate.queryForObject(
				"select run_record_pk from controlplane_run_record where job_execution_id = ?",
				Long.class,
				7001L
		);
		assertEquals(23L, migratedPk);
	}

	@Test
	void backfillsMissingRunRecordRowsFromRunSummaryOnStartup() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry first = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		first.upsert(run(3001L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));
		jdbcTemplate.update("delete from controlplane_run_record where job_execution_id = ?", 3001L);

		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		Long recordCount = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_run_record where job_execution_id = ?",
				Long.class,
				3001L
		);
		assertEquals(1L, recordCount);
	}

	@Test
	void linksRunRecordToTriggerEventWhenLaunchedRunIdMatchesJobExecutionId() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcTriggerEventRegistry triggerRegistry = new JdbcTriggerEventRegistry(jdbcTemplate, 100);
		TriggerEventView triggerEvent = triggerRegistry.recordAccepted("customer-load", "manual_operator_request", "operator-ui", "queued");
		jdbcTemplate.update(
				"update controlplane_trigger_event set launched_run_id = ? where trigger_event_id = ?",
				"4001",
				triggerEvent.triggerEventId()
		);

		JdbcRunSummaryRegistry runRegistry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		runRegistry.upsert(run(4001L, "customer-load", LocalDateTime.now(), "COMPLETED"));

		String linkedTriggerEventId = jdbcTemplate.queryForObject(
				"select trigger_event_id from controlplane_run_record where job_execution_id = ?",
				String.class,
				4001L
		);
		Long linkedTriggerEventPk = jdbcTemplate.queryForObject(
				"select trigger_event_pk from controlplane_run_record where job_execution_id = ?",
				Long.class,
				4001L
		);
		Long launchedRunPk = jdbcTemplate.queryForObject(
				"select launched_run_pk from controlplane_trigger_event where trigger_event_id = ?",
				Long.class,
				triggerEvent.triggerEventId()
		);
		Long runRecordPk = jdbcTemplate.queryForObject(
				"select run_record_pk from controlplane_run_record where job_execution_id = ?",
				Long.class,
				4001L
		);
		assertEquals(triggerEvent.triggerEventId(), linkedTriggerEventId);
		assertEquals(1L, linkedTriggerEventPk);
		assertEquals(runRecordPk, launchedRunPk);
	}

	@Test
	void projectsTriggerOriginFromLinkedTriggerEvent() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcTriggerEventRegistry triggerRegistry = new JdbcTriggerEventRegistry(jdbcTemplate, 100);
		TriggerEventView triggerEvent = triggerRegistry.recordAcceptedForSchedule("sch-1", "customer-load", "schedule_tick", "scheduler", "queued");
		jdbcTemplate.update(
				"update controlplane_trigger_event set launched_run_id = ? where trigger_event_id = ?",
				"4401",
				triggerEvent.triggerEventId()
		);

		JdbcRunSummaryRegistry runRegistry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		runRegistry.upsert(run(4401L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		RunSummaryView run = runRegistry.findByJobExecutionId(4401L).orElseThrow();
		assertEquals("SCHEDULE", run.triggerOrigin());
	}

	@Test
	void infersScheduleTriggerOriginWhenLinkedTriggerOriginIsMissing() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcTriggerEventRegistry triggerRegistry = new JdbcTriggerEventRegistry(jdbcTemplate, 100);
		TriggerEventView triggerEvent = triggerRegistry.recordAcceptedForSchedule("sch-2", "customer-load", "schedule_tick", "scheduler", "queued");
		jdbcTemplate.update("update controlplane_trigger_event set trigger_origin = null where trigger_event_id = ?", triggerEvent.triggerEventId());
		jdbcTemplate.update(
				"update controlplane_trigger_event set launched_run_id = ? where trigger_event_id = ?",
				"4402",
				triggerEvent.triggerEventId()
		);

		JdbcRunSummaryRegistry runRegistry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		runRegistry.upsert(run(4402L, "customer-load", LocalDateTime.parse("2026-05-27T09:05:00"), "COMPLETED"));

		RunSummaryView run = runRegistry.findByJobExecutionId(4402L).orElseThrow();
		assertEquals("SCHEDULE", run.triggerOrigin());
	}

	@Test
	void startupBackfillsTriggerEventIdForExistingRunRecordWhenLaunchedRunIdArrivesLater() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcTriggerEventRegistry triggerRegistry = new JdbcTriggerEventRegistry(jdbcTemplate, 100);
		JdbcRunSummaryRegistry firstRegistry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		firstRegistry.upsert(run(5001L, "customer-load", LocalDateTime.now(), "COMPLETED"));

		TriggerEventView triggerEvent = triggerRegistry.recordAccepted("customer-load", "manual_operator_request", "operator-ui", "queued");
		jdbcTemplate.update(
				"update controlplane_trigger_event set launched_run_id = ? where trigger_event_id = ?",
				"5001",
				triggerEvent.triggerEventId()
		);

		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		String linkedTriggerEventId = jdbcTemplate.queryForObject(
				"select trigger_event_id from controlplane_run_record where job_execution_id = ?",
				String.class,
				5001L
		);
		Long linkedTriggerEventPk = jdbcTemplate.queryForObject(
				"select trigger_event_pk from controlplane_run_record where job_execution_id = ?",
				Long.class,
				5001L
		);
		assertEquals(triggerEvent.triggerEventId(), linkedTriggerEventId);
		assertEquals(1L, linkedTriggerEventPk);
	}

	@Test
	void startupBackfillPrefersLaunchedRunPkOverAmbiguousLaunchedRunIdMatches() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcTriggerEventRegistry(jdbcTemplate, 100);
		JdbcRunSummaryRegistry firstRegistry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		firstRegistry.upsert(run(5101L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		Long runRecordPk = jdbcTemplate.queryForObject(
				"select run_record_pk from controlplane_run_record where job_execution_id = ?",
				Long.class,
				5101L
		);

		jdbcTemplate.update("""
				insert into controlplane_trigger_event (
					trigger_event_pk, trigger_event_id, job_key, decision_status, reason, requested_by,
					requested_at, launched_run_pk, launched_run_id, message, trigger_origin
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				11L, "te-id-only", "customer-load", "ACCEPTED", "manual_operator_request", "operator-ui",
				Timestamp.valueOf(LocalDateTime.parse("2026-05-27T09:10:00")), null, "5101", "legacy-id", "MANUAL"
		);
		jdbcTemplate.update("""
				insert into controlplane_trigger_event (
					trigger_event_pk, trigger_event_id, job_key, decision_status, reason, requested_by,
					requested_at, launched_run_pk, launched_run_id, message, trigger_origin
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				12L, "te-pk-match", "customer-load", "ACCEPTED", "manual_operator_request", "operator-ui",
				Timestamp.valueOf(LocalDateTime.parse("2026-05-27T09:05:00")), runRecordPk, "5101", "pk-match", "MANUAL"
		);

		jdbcTemplate.update("update controlplane_run_record set trigger_event_id = null, trigger_event_pk = null where job_execution_id = ?", 5101L);

		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		String linkedTriggerEventId = jdbcTemplate.queryForObject(
				"select trigger_event_id from controlplane_run_record where job_execution_id = ?",
				String.class,
				5101L
		);
		Long linkedTriggerEventPk = jdbcTemplate.queryForObject(
				"select trigger_event_pk from controlplane_run_record where job_execution_id = ?",
				Long.class,
				5101L
		);

		assertEquals("te-pk-match", linkedTriggerEventId);
		assertEquals(12L, linkedTriggerEventPk);
	}

	@Test
	void doesNotUseTimeWindowFallbackDuringUpsertWhenLaunchIdIsNotSet() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcTriggerEventRegistry(jdbcTemplate, 100);
		jdbcTemplate.update("""
				insert into controlplane_trigger_event (
					trigger_event_id, job_key, decision_status, reason, requested_by,
					requested_at, launched_run_id, message, trigger_origin
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				"te-window-1",
				"customer-load",
				"ACCEPTED",
				"manual_operator_request",
				"operator-ui",
				Timestamp.valueOf(LocalDateTime.parse("2026-05-27T08:45:00")),
				null,
				"queued",
				"MANUAL"
		);

		JdbcRunSummaryRegistry runRegistry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		runRegistry.upsert(run(6001L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		String linkedTriggerEventId = jdbcTemplate.queryForObject(
				"select trigger_event_id from controlplane_run_record where job_execution_id = ?",
				String.class,
				6001L
		);
		String launchedRunId = jdbcTemplate.queryForObject(
				"select launched_run_id from controlplane_trigger_event where trigger_event_id = ?",
				String.class,
				"te-window-1"
		);
		assertNull(linkedTriggerEventId);
		assertNull(launchedRunId);
	}

	@Test
	void startupBackfillUsesSingleCandidateTimeWindowFallbackForLegacyRows() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcTriggerEventRegistry(jdbcTemplate, 100);
		jdbcTemplate.update("""
				insert into controlplane_trigger_event (
					trigger_event_id, job_key, decision_status, reason, requested_by,
					requested_at, launched_run_id, message, trigger_origin
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				"te-window-backfill-1",
				"customer-load",
				"ACCEPTED",
				"manual_operator_request",
				"operator-ui",
				Timestamp.valueOf(LocalDateTime.parse("2026-05-27T08:45:00")),
				null,
				"queued",
				"MANUAL"
		);

		JdbcRunSummaryRegistry firstRegistry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		firstRegistry.upsert(run(7001L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		String linkedTriggerEventId = jdbcTemplate.queryForObject(
				"select trigger_event_id from controlplane_run_record where job_execution_id = ?",
				String.class,
				7001L
		);
		String launchedRunId = jdbcTemplate.queryForObject(
				"select launched_run_id from controlplane_trigger_event where trigger_event_id = ?",
				String.class,
				"te-window-backfill-1"
		);
		assertEquals("te-window-backfill-1", linkedTriggerEventId);
		assertEquals("7001", launchedRunId);
	}

	@Test
	void startupBackfillSkipsTimeWindowFallbackWhenMultipleCandidatesExist() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcTriggerEventRegistry(jdbcTemplate, 100);
		jdbcTemplate.update("""
				insert into controlplane_trigger_event (
					trigger_event_id, job_key, decision_status, reason, requested_by,
					requested_at, launched_run_id, message, trigger_origin
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				"te-window-multi-1",
				"customer-load",
				"ACCEPTED",
				"manual_operator_request",
				"operator-ui",
				Timestamp.valueOf(LocalDateTime.parse("2026-05-27T08:45:00")),
				null,
				"queued",
				"MANUAL"
		);
		jdbcTemplate.update("""
				insert into controlplane_trigger_event (
					trigger_event_id, job_key, decision_status, reason, requested_by,
					requested_at, launched_run_id, message, trigger_origin
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				"te-window-multi-2",
				"customer-load",
				"ACCEPTED",
				"manual_operator_request",
				"operator-ui",
				Timestamp.valueOf(LocalDateTime.parse("2026-05-27T08:50:00")),
				null,
				"queued",
				"MANUAL"
		);

		JdbcRunSummaryRegistry firstRegistry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		firstRegistry.upsert(run(7101L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));
		jdbcTemplate.update("update controlplane_run_record set trigger_event_id = null, trigger_event_pk = null where job_execution_id = ?", 7101L);

		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		String linkedTriggerEventId = jdbcTemplate.queryForObject(
				"select trigger_event_id from controlplane_run_record where job_execution_id = ?",
				String.class,
				7101L
		);
		assertNull(linkedTriggerEventId);
	}

	@Test
	void startupBackfillsSelectedJobKeyForLegacyRowsWhenMissing() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry firstRegistry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		firstRegistry.upsert(run(8001L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));
		jdbcTemplate.update("update controlplane_run_record set selected_job_key = null where job_execution_id = ?", 8001L);

		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		String selectedJobKey = jdbcTemplate.queryForObject(
				"select selected_job_key from controlplane_run_record where job_execution_id = ?",
				String.class,
				8001L
		);
		assertEquals("customer-load", selectedJobKey);
	}

	@Test
	void enforcesGlobalRetention() {
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(new JdbcTemplate(inMemoryDataSource()), 2);
		registry.upsert(run(1001L, "a", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));
		registry.upsert(run(1002L, "b", LocalDateTime.parse("2026-05-27T10:00:00"), "COMPLETED"));
		registry.upsert(run(1003L, "c", LocalDateTime.parse("2026-05-27T11:00:00"), "COMPLETED"));

		List<RunSummaryView> runs = registry.latestRuns(10);
		assertEquals(2, runs.size());
		assertEquals(1003L, runs.get(0).jobExecutionId());
		assertEquals(1002L, runs.get(1).jobExecutionId());
		assertTrue(registry.findByJobExecutionId(1001L).isEmpty());
	}

	@Test
	void retentionEvictionRemovesRunRecordGraphAndApiReadsReturnEmpty() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		jdbcTemplate.execute("""
				create table batch_step_execution (
					step_execution_id bigint primary key,
					job_execution_id bigint not null,
					step_name varchar(100) not null,
					status varchar(20),
					start_time timestamp,
					end_time timestamp,
					read_count bigint,
					write_count bigint,
					filter_count bigint,
					rollback_count bigint
				)
				""");
		jdbcTemplate.update("""
				insert into batch_step_execution (
					step_execution_id, job_execution_id, step_name, status, start_time, end_time,
					read_count, write_count, filter_count, rollback_count
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				401L,
				11001L,
				"load-customers",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00"),
				10L,
				10L,
				0L,
				0L
		);

		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 2);
		registry.upsert(run(11001L, "alpha", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));
		registry.upsert(run(11002L, "beta", LocalDateTime.parse("2026-05-27T10:00:00"), "COMPLETED"));
		registry.upsert(run(11003L, "gamma", LocalDateTime.parse("2026-05-27T11:00:00"), "COMPLETED"));

		Long runRecordCount = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_run_record where job_execution_id = ?",
				Long.class,
				11001L
		);
		Long stepRecordCount = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_step_record where run_record_id = ?",
				Long.class,
				"rr-11001"
		);
		Long artifactRecordCount = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_artifact_record where run_record_id = ?",
				Long.class,
				"rr-11001"
		);

		assertEquals(0L, runRecordCount);
		assertEquals(0L, stepRecordCount);
		assertEquals(0L, artifactRecordCount);
		assertTrue(registry.listStepRecordsByJobExecutionId(11001L, 10).isEmpty());
		assertTrue(registry.listArtifactRecordsByJobExecutionId(11001L, 10).isEmpty());
	}

	@Test
	void initializesStepAndArtifactTablesForS4bSlice() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		Long stepTableExists = jdbcTemplate.queryForObject(
				"select count(*) from sqlite_master where type = 'table' and name = 'controlplane_step_record'",
				Long.class
		);
		Long artifactTableExists = jdbcTemplate.queryForObject(
				"select count(*) from sqlite_master where type = 'table' and name = 'controlplane_artifact_record'",
				Long.class
		);

		assertEquals(1L, stepTableExists);
		assertEquals(1L, artifactTableExists);
	}

	@Test
	void initializesAttemptLinkAndCheckpointAnchorTablesForS4cSlice() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		Long attemptLinkTableExists = jdbcTemplate.queryForObject(
				"select count(*) from sqlite_master where type = 'table' and name = 'controlplane_attempt_link'",
				Long.class
		);
		Long checkpointAnchorTableExists = jdbcTemplate.queryForObject(
				"select count(*) from sqlite_master where type = 'table' and name = 'controlplane_checkpoint_anchor'",
				Long.class
		);
		Long attemptLinkRunIndexExists = jdbcTemplate.queryForObject(
				"select count(*) from sqlite_master where type = 'index' and name = 'idx_attempt_link_run'",
				Long.class
		);
		Long checkpointAnchorRunIndexExists = jdbcTemplate.queryForObject(
				"select count(*) from sqlite_master where type = 'index' and name = 'idx_checkpoint_anchor_run'",
				Long.class
		);
		Long attemptLinkPriorIndexExists = jdbcTemplate.queryForObject(
				"select count(*) from sqlite_master where type = 'index' and name = 'idx_attempt_link_prior'",
				Long.class
		);
		Long checkpointAnchorStepIndexExists = jdbcTemplate.queryForObject(
				"select count(*) from sqlite_master where type = 'index' and name = 'idx_checkpoint_anchor_step'",
				Long.class
		);

		assertEquals(1L, attemptLinkTableExists);
		assertEquals(1L, checkpointAnchorTableExists);
		assertEquals(1L, attemptLinkRunIndexExists);
		assertEquals(1L, checkpointAnchorRunIndexExists);
		assertEquals(1L, attemptLinkPriorIndexExists);
		assertEquals(1L, checkpointAnchorStepIndexExists);
	}

	@Test
	void retentionEvictionRemovesAttemptLinkAndCheckpointAnchorRowsForEvictedRuns() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 2);

		registry.upsert(run(12001L, "alpha", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));
		registry.upsert(run(12002L, "beta", LocalDateTime.parse("2026-05-27T10:00:00"), "COMPLETED"));

		registry.upsert(run(12003L, "gamma", LocalDateTime.parse("2026-05-27T11:00:00"), "COMPLETED"));

		Long evictedAttemptLinks = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_attempt_link where run_record_id = ?",
				Long.class,
				"rr-12001"
		);
		Long evictedCheckpointAnchors = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_checkpoint_anchor where run_record_id = ?",
				Long.class,
				"rr-12001"
		);
		Long retainedAttemptLinks = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_attempt_link where run_record_id = ?",
				Long.class,
				"rr-12002"
		);

		assertEquals(0L, evictedAttemptLinks);
		assertEquals(0L, evictedCheckpointAnchors);
		assertEquals(1L, retainedAttemptLinks);
	}

	@Test
	void allowsStepLevelArtifactWhenRunLineageMatches() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		jdbcTemplate.update("""
				insert into controlplane_run_record (
					run_record_pk, run_record_id, job_execution_id, scenario, run_status, created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?)
				""",
				1L,
				"rr-9001",
				9001L,
				"customer-load",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00")
		);
		jdbcTemplate.update("""
				insert into controlplane_step_record (
					step_record_pk, step_record_id, run_record_id, step_name, step_status, created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?)
				""",
				1L,
				"sr-9001-1",
				"rr-9001",
				"load-customers",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00")
		);

		jdbcTemplate.update("""
				insert into controlplane_artifact_record (
					artifact_record_pk, artifact_record_id, run_record_id, step_record_id, artifact_role, artifact_path, created_at
				) values (?, ?, ?, ?, ?, ?, ?)
				""",
				1L,
				"ar-9001-1",
				"rr-9001",
				"sr-9001-1",
				"STEP_OUTPUT",
				"output/customers.csv",
				Timestamp.valueOf("2026-05-27 09:01:00")
		);

		Long count = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_artifact_record where artifact_record_id = ?",
				Long.class,
				"ar-9001-1"
		);
		assertEquals(1L, count);
	}

	@Test
	void rejectsStepLevelArtifactWhenStepLineageDoesNotMatchRun() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		jdbcTemplate.update("""
				insert into controlplane_run_record (
					run_record_pk, run_record_id, job_execution_id, scenario, run_status, created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?)
				""",
				1L,
				"rr-9101",
				9101L,
				"customer-load",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00")
		);
		jdbcTemplate.update("""
				insert into controlplane_run_record (
					run_record_pk, run_record_id, job_execution_id, scenario, run_status, created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?)
				""",
				2L,
				"rr-9102",
				9102L,
				"customer-load",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00")
		);
		jdbcTemplate.update("""
				insert into controlplane_step_record (
					step_record_pk, step_record_id, run_record_id, step_name, step_status, created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?)
				""",
				1L,
				"sr-9102-1",
				"rr-9102",
				"load-customers",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00")
		);

		assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbcTemplate.update("""
				insert into controlplane_artifact_record (
					artifact_record_pk, artifact_record_id, run_record_id, step_record_id, artifact_role, artifact_path, created_at
				) values (?, ?, ?, ?, ?, ?, ?)
				""",
				1L,
				"ar-9101-1",
				"rr-9101",
				"sr-9102-1",
				"STEP_OUTPUT",
				"output/customers.csv",
				Timestamp.valueOf("2026-05-27 09:01:00")
		));
	}

	@Test
	void writesRunLogArtifactDuringRunSummaryUpsert() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		registry.upsert(run(9201L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		String artifactRole = jdbcTemplate.queryForObject(
				"select artifact_role from controlplane_artifact_record where artifact_record_id = ?",
				String.class,
				"ar-log-9201"
		);
		String artifactPath = jdbcTemplate.queryForObject(
				"select artifact_path from controlplane_artifact_record where artifact_record_id = ?",
				String.class,
				"ar-log-9201"
		);
		assertEquals("RUN_LOG", artifactRole);
		assertEquals("logs/2026-05-27/customer-load.log", artifactPath);
	}

	@Test
	void projectsStepRecordsFromBatchStepMetadataWhenAvailable() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		jdbcTemplate.execute("""
				create table batch_step_execution (
					step_execution_id bigint primary key,
					job_execution_id bigint not null,
					step_name varchar(100) not null,
					status varchar(20),
					start_time timestamp,
					end_time timestamp,
					read_count bigint,
					write_count bigint,
					filter_count bigint,
					rollback_count bigint
				)
				""");
		jdbcTemplate.update("""
				insert into batch_step_execution (
					step_execution_id, job_execution_id, step_name, status, start_time, end_time,
					read_count, write_count, filter_count, rollback_count
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				101L,
				9301L,
				"load-customers",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00"),
				10L,
				10L,
				0L,
				0L
		);

		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		registry.upsert(run(9301L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		String stepStatus = jdbcTemplate.queryForObject(
				"select step_status from controlplane_step_record where step_record_id = ?",
				String.class,
				"sr-9301-101"
		);
		Long readCount = jdbcTemplate.queryForObject(
				"select read_count from controlplane_step_record where step_record_id = ?",
				Long.class,
				"sr-9301-101"
		);
		assertEquals("COMPLETED", stepStatus);
		assertEquals(10L, readCount);

		registry.upsert(run(9301L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));
		Long count = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_step_record where step_record_id = ?",
				Long.class,
				"sr-9301-101"
		);
		assertEquals(1L, count);
	}

	@Test
	void startsAgainstPreS4bSchemaAndPreservesExistingRunData() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		jdbcTemplate.execute("""
				create table controlplane_run_summary (
					job_execution_id bigint primary key,
					scenario varchar(200) not null,
					status varchar(50) not null,
					start_time timestamp,
					end_time timestamp,
					duration_seconds bigint,
					source_count bigint,
					written_count bigint,
					rejected_count bigint,
					log_path varchar(2000),
					last_seen_at timestamp not null
				)
				""");
		jdbcTemplate.execute("""
				create table controlplane_run_record (
					run_record_pk bigint primary key,
					run_record_id varchar(80) not null unique,
					job_execution_id bigint not null unique,
					trigger_event_pk bigint,
					trigger_event_id varchar(80),
					selected_job_key varchar(200),
					scenario varchar(200) not null,
					run_status varchar(50) not null,
					started_at timestamp,
					finished_at timestamp,
					duration_seconds bigint,
					source_count bigint,
					written_count bigint,
					rejected_count bigint,
					created_at timestamp not null,
					updated_at timestamp not null
				)
				""");
		jdbcTemplate.update("""
				insert into controlplane_run_summary (
					job_execution_id, scenario, status, start_time, end_time,
					duration_seconds, source_count, written_count, rejected_count, log_path, last_seen_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				9801L,
				"customer-load",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00"),
				60L,
				10L,
				10L,
				0L,
				"logs/2026-05-27/customer-load.log",
				Timestamp.valueOf("2026-05-27 09:01:00")
		);
		jdbcTemplate.update("""
				insert into controlplane_run_record (
					run_record_pk, run_record_id, job_execution_id, trigger_event_pk, trigger_event_id,
					selected_job_key, scenario, run_status, started_at, finished_at,
					duration_seconds, source_count, written_count, rejected_count, created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				1L,
				"rr-9801",
				9801L,
				null,
				null,
				"customer-load",
				"customer-load",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00"),
				60L,
				10L,
				10L,
				0L,
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00")
		);

		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		Long stepTableExists = jdbcTemplate.queryForObject(
				"select count(*) from sqlite_master where type = 'table' and name = 'controlplane_step_record'",
				Long.class
		);
		Long artifactTableExists = jdbcTemplate.queryForObject(
				"select count(*) from sqlite_master where type = 'table' and name = 'controlplane_artifact_record'",
				Long.class
		);
		Long preservedRunSummary = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_run_summary where job_execution_id = ?",
				Long.class,
				9801L
		);
		Long preservedRunRecord = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_run_record where job_execution_id = ?",
				Long.class,
				9801L
		);

		assertEquals(1L, stepTableExists);
		assertEquals(1L, artifactTableExists);
		assertEquals(1L, preservedRunSummary);
		assertEquals(1L, preservedRunRecord);
	}

	@Test
	void startsAgainstPreS4cSchemaAndPreservesExistingRunData() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		jdbcTemplate.execute("""
				create table controlplane_run_summary (
					job_execution_id bigint primary key,
					scenario varchar(200) not null,
					status varchar(50) not null,
					start_time timestamp,
					end_time timestamp,
					duration_seconds bigint,
					source_count bigint,
					written_count bigint,
					rejected_count bigint,
					log_path varchar(2000),
					last_seen_at timestamp not null
				)
				""");
		jdbcTemplate.execute("""
				create table controlplane_run_record (
					run_record_pk bigint primary key,
					run_record_id varchar(80) not null unique,
					job_execution_id bigint not null unique,
					trigger_event_pk bigint,
					trigger_event_id varchar(80),
					selected_job_key varchar(200),
					scenario varchar(200) not null,
					run_status varchar(50) not null,
					started_at timestamp,
					finished_at timestamp,
					duration_seconds bigint,
					source_count bigint,
					written_count bigint,
					rejected_count bigint,
					created_at timestamp not null,
					updated_at timestamp not null
				)
				""");
		jdbcTemplate.update("""
				insert into controlplane_run_summary (
					job_execution_id, scenario, status, start_time, end_time,
					duration_seconds, source_count, written_count, rejected_count, log_path, last_seen_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				9901L,
				"customer-load",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00"),
				60L,
				10L,
				10L,
				0L,
				"logs/2026-05-27/customer-load.log",
				Timestamp.valueOf("2026-05-27 09:01:00")
		);
		jdbcTemplate.update("""
				insert into controlplane_run_record (
					run_record_pk, run_record_id, job_execution_id, trigger_event_pk, trigger_event_id,
					selected_job_key, scenario, run_status, started_at, finished_at,
					duration_seconds, source_count, written_count, rejected_count, created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				1L,
				"rr-9901",
				9901L,
				null,
				null,
				"customer-load",
				"customer-load",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00"),
				60L,
				10L,
				10L,
				0L,
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00")
		);

		new JdbcRunSummaryRegistry(jdbcTemplate, 100);

		Long attemptLinkTableExists = jdbcTemplate.queryForObject(
				"select count(*) from sqlite_master where type = 'table' and name = 'controlplane_attempt_link'",
				Long.class
		);
		Long checkpointAnchorTableExists = jdbcTemplate.queryForObject(
				"select count(*) from sqlite_master where type = 'table' and name = 'controlplane_checkpoint_anchor'",
				Long.class
		);
		Long preservedRunSummary = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_run_summary where job_execution_id = ?",
				Long.class,
				9901L
		);
		Long preservedRunRecord = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_run_record where job_execution_id = ?",
				Long.class,
				9901L
		);

		assertEquals(1L, attemptLinkTableExists);
		assertEquals(1L, checkpointAnchorTableExists);
		assertEquals(1L, preservedRunSummary);
		assertEquals(1L, preservedRunRecord);
	}

	@Test
	void listsStepRecordsByJobExecutionId() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		jdbcTemplate.execute("""
				create table batch_step_execution (
					step_execution_id bigint primary key,
					job_execution_id bigint not null,
					step_name varchar(100) not null,
					status varchar(20),
					start_time timestamp,
					end_time timestamp,
					read_count bigint,
					write_count bigint,
					filter_count bigint,
					rollback_count bigint
				)
				""");
		jdbcTemplate.update("""
				insert into batch_step_execution (
					step_execution_id, job_execution_id, step_name, status, start_time, end_time,
					read_count, write_count, filter_count, rollback_count
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				201L,
				9401L,
				"extract-customers",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00"),
				5L,
				5L,
				0L,
				0L
		);
		jdbcTemplate.update("""
				insert into batch_step_execution (
					step_execution_id, job_execution_id, step_name, status, start_time, end_time,
					read_count, write_count, filter_count, rollback_count
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				202L,
				9401L,
				"load-customers",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:01:00"),
				Timestamp.valueOf("2026-05-27 09:02:00"),
				5L,
				5L,
				0L,
				0L
		);

		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		registry.upsert(run(9401L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		List<RunStepRecordView> steps = registry.listStepRecordsByJobExecutionId(9401L, 10);
		assertEquals(2, steps.size());
		assertEquals("sr-9401-201", steps.get(0).stepRecordId());
		assertEquals("extract-customers", steps.get(0).stepName());
		assertEquals("sr-9401-202", steps.get(1).stepRecordId());
		assertEquals("load-customers", steps.get(1).stepName());
	}

	@Test
	void listsArtifactsByJobExecutionIdAndStepRecordId() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		registry.upsert(run(9501L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		jdbcTemplate.update("""
				insert into controlplane_step_record (
					step_record_pk, step_record_id, run_record_id, step_name, step_status, created_at, updated_at
				) values (?, ?, ?, ?, ?, ?, ?)
				""",
				1L,
				"sr-9501-1",
				"rr-9501",
				"load-customers",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00")
		);
		jdbcTemplate.update("""
				insert into controlplane_artifact_record (
					artifact_record_pk, artifact_record_id, run_record_id, step_record_id, artifact_role, artifact_path, created_at
				) values (?, ?, ?, ?, ?, ?, ?)
				""",
				2L,
				"ar-step-9501-1",
				"rr-9501",
				"sr-9501-1",
				"STEP_OUTPUT",
				"output/customers.csv",
				Timestamp.valueOf("2026-05-27 09:01:00")
		);

		List<RunArtifactRecordView> runArtifacts = registry.listArtifactRecordsByJobExecutionId(9501L, 10);
		assertEquals(2, runArtifacts.size());
		List<String> artifactIds = runArtifacts.stream().map(RunArtifactRecordView::artifactRecordId).toList();
		assertTrue(artifactIds.contains("ar-step-9501-1"));
		assertTrue(artifactIds.contains("ar-log-9501"));

		List<RunArtifactRecordView> stepArtifacts = registry.listArtifactRecordsByStepRecordId("sr-9501-1", 10);
		assertEquals(1, stepArtifacts.size());
		assertEquals("ar-step-9501-1", stepArtifacts.get(0).artifactRecordId());
		assertEquals("STEP_OUTPUT", stepArtifacts.get(0).artifactRole());
	}

	@Test
	void writesStepLevelArtifactsFromBatchStepExecutionContext() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		jdbcTemplate.execute("""
				create table batch_step_execution (
					step_execution_id bigint primary key,
					job_execution_id bigint not null,
					step_name varchar(100) not null,
					status varchar(20),
					start_time timestamp,
					end_time timestamp,
					read_count bigint,
					write_count bigint,
					filter_count bigint,
					rollback_count bigint
				)
				""");
		jdbcTemplate.execute("""
				create table batch_step_execution_context (
					step_execution_id bigint primary key,
					short_context varchar(2500)
				)
				""");
		jdbcTemplate.update("""
				insert into batch_step_execution (
					step_execution_id, job_execution_id, step_name, status, start_time, end_time,
					read_count, write_count, filter_count, rollback_count
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				303L,
				9701L,
				"load-customers",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00"),
				10L,
				10L,
				0L,
				0L
		);
		jdbcTemplate.update(
				"insert into batch_step_execution_context (step_execution_id, short_context) values (?, ?)",
				303L,
				"{\"rejectOutputPath\":\"output/rejects/customers.csv\",\"archivedSourcePath\":\"archive/customers.csv\"}"
		);

		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		registry.upsert(run(9701L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		List<RunArtifactRecordView> stepArtifacts = registry.listArtifactRecordsByStepRecordId("sr-9701-303", 10);
		assertEquals(2, stepArtifacts.size());
		List<String> roles = stepArtifacts.stream().map(RunArtifactRecordView::artifactRole).toList();
		assertTrue(roles.contains("STEP_REJECT_OUTPUT"));
		assertTrue(roles.contains("STEP_ARCHIVED_SOURCE"));

		registry.upsert(run(9701L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));
		Long count = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_artifact_record where step_record_id = ?",
				Long.class,
				"sr-9701-303"
		);
		assertEquals(2L, count);
	}

	@Test
	void projectsStepRecordsFromStructuredLogWhenBatchMetadataIsUnavailable() throws Exception {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		Path logPath = tempDir.resolve("logs/orders-flow.log");
		java.nio.file.Files.createDirectories(logPath.getParent());
		java.nio.file.Files.writeString(logPath, String.join(System.lineSeparator(),
				"2026-05-27T09:00:00.000+00:00 INFO [main] [scenario:orders-flow] [run:run-1] [job:9901] [step:load-orders] logger - STEP_EVENT event=step_started stepName=load-orders stepExecutionId=501 status=STARTED",
				"2026-05-27T09:01:00.000+00:00 INFO [main] [scenario:orders-flow] [run:run-1] [job:9901] [step:load-orders] logger - STEP_EVENT event=step_finished stepName=load-orders stepExecutionId=501 status=COMPLETED readCount=10 writeCount=10 filterCount=0 skipCount=0 rollbackCount=0 rejectedCount=1 rejectOutputPath=output/rejects/orders.csv archivedSourcePath=archive/orders.csv"
		));

		RunSummaryView runSummary = new RunSummaryView(
				"orders-flow",
				9901L,
				"COMPLETED",
				LocalDateTime.parse("2026-05-27T09:00:00"),
				LocalDateTime.parse("2026-05-27T09:02:00"),
				120L,
				10L,
				10L,
				1L,
				logPath.toString()
		);
		registry.upsert(runSummary);

		List<RunStepRecordView> steps = registry.listStepRecordsByJobExecutionId(9901L, 10);
		assertEquals(1, steps.size());
		assertEquals("load-orders", steps.get(0).stepName());
		assertEquals("COMPLETED", steps.get(0).stepStatus());
		assertEquals(10L, steps.get(0).readCount());
		assertEquals(1L, steps.get(0).rejectedCount());

		List<RunArtifactRecordView> artifacts = registry.listArtifactRecordsByStepRecordId("sr-9901-501", 10);
		assertEquals(2, artifacts.size());
		List<String> roles = artifacts.stream().map(RunArtifactRecordView::artifactRole).toList();
		assertTrue(roles.contains("STEP_REJECT_OUTPUT"));
		assertTrue(roles.contains("STEP_ARCHIVED_SOURCE"));
	}

	@Test
	void mergesNameOnlyAndIdBasedStructuredStepEventsIntoSingleStepRecord() throws Exception {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		Path logPath = tempDir.resolve("logs/customer-load.log");
		java.nio.file.Files.createDirectories(logPath.getParent());
		java.nio.file.Files.writeString(logPath, String.join(System.lineSeparator(),
				"2026-05-27T09:00:00.000+00:00 INFO [main] [scenario:customer-load] [run:run-2] [job:9902] [step:customers-step] logger - STEP_EVENT event=step_started stepName=customers-step status=STARTED",
				"2026-05-27T09:01:00.000+00:00 INFO [main] [scenario:customer-load] [run:run-2] [job:9902] [step:customers-step] logger - STEP_EVENT event=step_finished stepName=customers-step stepExecutionId=601 status=COMPLETED readCount=3 writeCount=3 filterCount=0 skipCount=0 rollbackCount=0 rejectedCount=0"
		));

		RunSummaryView runSummary = new RunSummaryView(
				"customer-load",
				9902L,
				"COMPLETED",
				LocalDateTime.parse("2026-05-27T09:00:00"),
				LocalDateTime.parse("2026-05-27T09:02:00"),
				120L,
				3L,
				3L,
				0L,
				logPath.toString()
		);
		registry.upsert(runSummary);

		List<RunStepRecordView> steps = registry.listStepRecordsByJobExecutionId(9902L, 10);
		assertEquals(1, steps.size());
		assertEquals("customers-step", steps.get(0).stepName());
		assertEquals("COMPLETED", steps.get(0).stepStatus());
		assertEquals(3L, steps.get(0).readCount());
		assertEquals(3L, steps.get(0).writeCount());

		Long duplicateCount = jdbcTemplate.queryForObject(
				"""
				select count(*)
				from controlplane_step_record
				where run_record_id = ?
				  and lower(step_name) = lower(?)
				""",
				Long.class,
				"rr-9902",
				"customers-step"
		);
		assertEquals(1L, duplicateCount);
	}

	@Test
	void writesStepLevelArtifactsFromEscapedJsonExecutionContextValues() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		jdbcTemplate.execute("""
				create table batch_step_execution (
					step_execution_id bigint primary key,
					job_execution_id bigint not null,
					step_name varchar(100) not null,
					status varchar(20),
					start_time timestamp,
					end_time timestamp,
					read_count bigint,
					write_count bigint,
					filter_count bigint,
					rollback_count bigint
				)
				""");
		jdbcTemplate.execute("""
				create table batch_step_execution_context (
					step_execution_id bigint primary key,
					short_context varchar(2500)
				)
				""");
		jdbcTemplate.update("""
				insert into batch_step_execution (
					step_execution_id, job_execution_id, step_name, status, start_time, end_time,
					read_count, write_count, filter_count, rollback_count
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				304L,
				9702L,
				"load-customers",
				"COMPLETED",
				Timestamp.valueOf("2026-05-27 09:00:00"),
				Timestamp.valueOf("2026-05-27 09:01:00"),
				10L,
				10L,
				0L,
				0L
		);
		jdbcTemplate.update(
				"insert into batch_step_execution_context (step_execution_id, short_context) values (?, ?)",
				304L,
				"{\"rejectOutputPath\":\"output\\\\rejects\\\\customer\\\"special\\\".csv\",\"archivedSourcePath\":\"archive\\\\customers.csv\"}"
		);

		JdbcRunSummaryRegistry registry = new JdbcRunSummaryRegistry(jdbcTemplate, 100);
		registry.upsert(run(9702L, "customer-load", LocalDateTime.parse("2026-05-27T09:00:00"), "COMPLETED"));

		List<RunArtifactRecordView> stepArtifacts = registry.listArtifactRecordsByStepRecordId("sr-9702-304", 10);
		assertEquals(2, stepArtifacts.size());
		Map<String, String> artifactByRole = stepArtifacts.stream()
				.collect(java.util.stream.Collectors.toMap(RunArtifactRecordView::artifactRole, RunArtifactRecordView::artifactPath));
		assertEquals("output\\rejects\\customer\"special\".csv", artifactByRole.get("STEP_REJECT_OUTPUT"));
		assertEquals("archive\\customers.csv", artifactByRole.get("STEP_ARCHIVED_SOURCE"));
	}

	private RunSummaryView run(Long id, String scenario, LocalDateTime start, String status) {
		return new RunSummaryView(
				scenario,
				id,
				status,
				start,
				start.plusMinutes(1),
				60L,
				10L,
				10L,
				0L,
				"logs/2026-05-27/" + scenario + ".log"
		);
	}

	private DriverManagerDataSource inMemoryDataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.sqlite.JDBC");
		Path databasePath = tempDir.resolve("cp-runs.db");
		dataSource.setUrl("jdbc:sqlite:" + databasePath.toAbsolutePath().toString().replace('\\', '/'));
		return dataSource;
	}
}

