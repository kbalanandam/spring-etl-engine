package com.etl.controlplane.schedules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcScheduleRegistryTest {

	@TempDir
	Path tempDir;

	@Test
	void upsertsAndFindsByIdAndKey() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcScheduleRegistry registry = new JdbcScheduleRegistry(jdbcTemplate);
		ScheduleView schedule = schedule("sch-1", "daily-a", LocalDateTime.parse("2026-05-28T09:00:00"));
		registry.upsert(schedule);

		assertTrue(registry.findByScheduleId("sch-1").isPresent());
		assertTrue(registry.findByScheduleKey("daily-a").isPresent());
		Long schedulePk = jdbcTemplate.queryForObject(
				"select schedule_pk from controlplane_schedule where schedule_id = ?",
				Long.class,
				"sch-1"
		);
		assertTrue(schedulePk != null && schedulePk > 0);
	}

	@Test
	void assignsDistinctSchedulePkValuesForNewRows() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcScheduleRegistry registry = new JdbcScheduleRegistry(jdbcTemplate);
		registry.upsert(schedule("sch-1", "daily-a", LocalDateTime.parse("2026-05-28T09:00:00")));
		registry.upsert(schedule("sch-2", "daily-b", LocalDateTime.parse("2026-05-28T10:00:00")));

		List<Long> values = jdbcTemplate.queryForList(
				"select schedule_pk from controlplane_schedule order by schedule_pk asc",
				Long.class
		);
		assertEquals(2, values.size());
		assertTrue(values.get(0) != null && values.get(1) != null);
		assertTrue(values.get(1) > values.get(0));
	}

	@Test
	void usesBigintTypeForSchedulePkColumn() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcScheduleRegistry(jdbcTemplate);

		String columnType = jdbcTemplate.queryForObject(
				"select type from pragma_table_info('controlplane_schedule') where lower(name) = 'schedule_pk'",
				String.class
		);
		assertEquals("bigint", columnType == null ? "" : columnType.toLowerCase());
	}

	@Test
	void usesSchedulePkAsPrimaryKeyAndKeepsScheduleIdUnique() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcScheduleRegistry(jdbcTemplate);

		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"select lower(name) as name, pk from pragma_table_info('controlplane_schedule') where lower(name) in ('schedule_pk', 'schedule_id')"
		);
		Map<String, Integer> pkFlags = new java.util.HashMap<>();
		for (Map<String, Object> row : rows) {
			pkFlags.put(String.valueOf(row.get("name")), ((Number) row.get("pk")).intValue());
		}

		assertEquals(1, pkFlags.getOrDefault("schedule_pk", 0));
		assertEquals(0, pkFlags.getOrDefault("schedule_id", 0));
	}

	@Test
	void migratesLegacyScheduleIdPrimaryKeyShape() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		jdbcTemplate.execute("""
				create table controlplane_schedule (
					schedule_id varchar(80) primary key,
					schedule_key varchar(200) not null unique,
					selected_job_key varchar(200) not null,
					expression varchar(200) not null,
					timezone varchar(100) not null,
					is_enabled boolean not null,
					is_paused boolean not null,
					description varchar(2000),
					created_at timestamp not null,
					updated_at timestamp not null,
					watcher_key varchar(200),
					last_accepted_due_at timestamp,
					schedule_pk bigint
				)
				""");
		jdbcTemplate.update("""
				insert into controlplane_schedule (
					schedule_id, schedule_key, selected_job_key, expression, timezone,
					is_enabled, is_paused, description, created_at, updated_at, watcher_key, last_accepted_due_at, schedule_pk
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				"sch-legacy", "daily-legacy", "customer-load", "0 0 * * *", "UTC",
				true, false, "legacy", java.sql.Timestamp.valueOf("2026-05-28 09:00:00"), java.sql.Timestamp.valueOf("2026-05-28 10:00:00"), null, null, 11L
		);

		JdbcScheduleRegistry registry = new JdbcScheduleRegistry(jdbcTemplate);
		assertTrue(registry.findByScheduleId("sch-legacy").isPresent());

		Integer schedulePkPkFlag = jdbcTemplate.queryForObject(
				"select pk from pragma_table_info('controlplane_schedule') where lower(name) = 'schedule_pk'",
				Integer.class
		);
		Integer scheduleIdPkFlag = jdbcTemplate.queryForObject(
				"select pk from pragma_table_info('controlplane_schedule') where lower(name) = 'schedule_id'",
				Integer.class
		);
		assertEquals(1, schedulePkPkFlag == null ? 0 : schedulePkPkFlag);
		assertEquals(0, scheduleIdPkFlag == null ? 0 : scheduleIdPkFlag);

		Long migratedPk = jdbcTemplate.queryForObject(
				"select schedule_pk from controlplane_schedule where schedule_id = ?",
				Long.class,
				"sch-legacy"
		);
		assertEquals(11L, migratedPk);
	}

	@Test
	void updatesExistingSchedule() {
		JdbcScheduleRegistry registry = new JdbcScheduleRegistry(new JdbcTemplate(inMemoryDataSource()));
		registry.upsert(schedule("sch-1", "daily-a", LocalDateTime.parse("2026-05-28T09:00:00")));
		registry.upsert(new ScheduleView(
				"sch-1", "daily-a", "customer-load", "0 15 * * *", "UTC", false, true,
				"updated", LocalDateTime.parse("2026-05-28T08:00:00"), LocalDateTime.parse("2026-05-28T10:00:00"), "watcher-a", Instant.parse("2026-05-28T10:00:00Z")
		));

		ScheduleView updated = registry.findByScheduleId("sch-1").orElseThrow();
		assertEquals("0 15 * * *", updated.expression());
		assertEquals("watcher-a", updated.watcherKey());
		assertFalse(updated.enabled());
		assertEquals(Instant.parse("2026-05-28T10:00:00Z"), updated.lastAcceptedDueAt());
	}

	@Test
	void listsByUpdatedAtDesc() {
		JdbcScheduleRegistry registry = new JdbcScheduleRegistry(new JdbcTemplate(inMemoryDataSource()));
		registry.upsert(schedule("sch-1", "daily-a", LocalDateTime.parse("2026-05-28T09:00:00")));
		registry.upsert(schedule("sch-2", "daily-b", LocalDateTime.parse("2026-05-28T10:00:00")));

		List<ScheduleView> schedules = registry.list(10);
		assertEquals(2, schedules.size());
		assertEquals("sch-2", schedules.get(0).scheduleId());
	}

	@Test
	void advancesDueWatermarkMonotonically() {
		JdbcScheduleRegistry registry = new JdbcScheduleRegistry(new JdbcTemplate(inMemoryDataSource()));
		registry.upsert(schedule("sch-1", "daily-a", LocalDateTime.parse("2026-05-28T09:00:00")));

		boolean first = registry.tryAdvanceLastAcceptedDueAt("sch-1", Instant.parse("2026-05-28T10:00:00Z"));
		boolean older = registry.tryAdvanceLastAcceptedDueAt("sch-1", Instant.parse("2026-05-28T09:59:59Z"));
		boolean newer = registry.tryAdvanceLastAcceptedDueAt("sch-1", Instant.parse("2026-05-28T10:01:00Z"));

		assertTrue(first);
		assertFalse(older);
		assertTrue(newer);
		assertEquals(Instant.parse("2026-05-28T10:01:00Z"), registry.findByScheduleId("sch-1").orElseThrow().lastAcceptedDueAt());
	}

	@Test
	void sameDueInstantCanOnlyBeClaimedOnceAcrossConcurrentCallers() throws Exception {
		JdbcScheduleRegistry registry = new JdbcScheduleRegistry(new JdbcTemplate(inMemoryDataSource()));
		registry.upsert(schedule("sch-1", "daily-a", LocalDateTime.parse("2026-05-28T09:00:00")));
		Instant dueAt = Instant.parse("2026-05-28T10:00:00Z");

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		java.util.concurrent.atomic.AtomicBoolean firstResult = new java.util.concurrent.atomic.AtomicBoolean(false);
		java.util.concurrent.atomic.AtomicBoolean secondResult = new java.util.concurrent.atomic.AtomicBoolean(false);

		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			executor.submit(() -> {
				ready.countDown();
				awaitLatch(start);
				firstResult.set(registry.tryAdvanceLastAcceptedDueAt("sch-1", dueAt));
			});
			executor.submit(() -> {
				ready.countDown();
				awaitLatch(start);
				secondResult.set(registry.tryAdvanceLastAcceptedDueAt("sch-1", dueAt));
			});
			ready.await(2, TimeUnit.SECONDS);
			start.countDown();
			executor.shutdown();
			executor.awaitTermination(5, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertEquals(1, (firstResult.get() ? 1 : 0) + (secondResult.get() ? 1 : 0));
		assertEquals(dueAt, registry.findByScheduleId("sch-1").orElseThrow().lastAcceptedDueAt());
	}

	private static void awaitLatch(CountDownLatch latch) {
		try {
			latch.await(2, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	private ScheduleView schedule(String id, String key, LocalDateTime updatedAt) {
		return new ScheduleView(
				id,
				key,
				"customer-load",
				"0 0 * * *",
				"UTC",
				true,
				false,
				"desc",
				updatedAt.minusHours(1),
				updatedAt,
				null,
				null
		);
	}

	private DriverManagerDataSource inMemoryDataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.sqlite.JDBC");
		Path databasePath = tempDir.resolve("cp-schedules.db");
		dataSource.setUrl("jdbc:sqlite:" + databasePath.toAbsolutePath().toString().replace('\\', '/'));
		return dataSource;
	}
}

