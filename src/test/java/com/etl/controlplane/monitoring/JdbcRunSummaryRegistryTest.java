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
import static org.junit.jupiter.api.Assertions.assertNull;
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

