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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

