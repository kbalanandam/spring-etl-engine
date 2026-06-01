package com.etl.controlplane.schedules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
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

