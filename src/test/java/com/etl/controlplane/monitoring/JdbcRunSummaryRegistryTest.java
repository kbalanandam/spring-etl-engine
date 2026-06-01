package com.etl.controlplane.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		assertEquals(1L, recordCount);
		assertEquals("rr-2001", runRecordId);
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

