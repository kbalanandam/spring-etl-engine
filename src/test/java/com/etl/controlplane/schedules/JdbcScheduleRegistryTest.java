package com.etl.controlplane.schedules;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcScheduleRegistryTest {

	@Test
	void upsertsAndFindsByIdAndKey() {
		JdbcScheduleRegistry registry = new JdbcScheduleRegistry(new JdbcTemplate(inMemoryDataSource()));
		ScheduleView schedule = schedule("sch-1", "daily-a", LocalDateTime.parse("2026-05-28T09:00:00"));
		registry.upsert(schedule);

		assertTrue(registry.findByScheduleId("sch-1").isPresent());
		assertTrue(registry.findByScheduleKey("daily-a").isPresent());
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
		dataSource.setDriverClassName("org.h2.Driver");
		dataSource.setUrl("jdbc:h2:mem:cp-schedules-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");
		return dataSource;
	}
}

