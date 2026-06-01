package com.etl.controlplane.triggers;

import com.etl.controlplane.schedules.JdbcScheduleRegistry;
import com.etl.controlplane.schedules.ScheduleView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcTriggerEventRegistryTest {

	@TempDir
	Path tempDir;

	@Test
	void recordsAndListsPersistedEventsInReverseChronologicalOrder() throws Exception {
		JdbcTriggerEventRegistry registry = new JdbcTriggerEventRegistry(new JdbcTemplate(inMemoryDataSource()), 10);
		TriggerEventView first = registry.recordAccepted("customer-load", "reason-a", "user-a", "first");
		Thread.sleep(5L);
		TriggerEventView second = registry.recordAccepted("customer-load", "reason-b", "user-b", "second");

		List<TriggerEventView> events = registry.listByJobKey("customer-load", 10);

		assertEquals(2, events.size());
		assertEquals(second.triggerEventId(), events.get(0).triggerEventId());
		assertEquals(first.triggerEventId(), events.get(1).triggerEventId());
	}

	@Test
	void enforcesRetentionPerJob() throws Exception {
		JdbcTriggerEventRegistry registry = new JdbcTriggerEventRegistry(new JdbcTemplate(inMemoryDataSource()), 2);
		registry.recordAccepted("customer-load", "reason-a", "user-a", "first");
		Thread.sleep(5L);
		registry.recordAccepted("customer-load", "reason-b", "user-b", "second");
		Thread.sleep(5L);
		registry.recordAccepted("customer-load", "reason-c", "user-c", "third");

		List<TriggerEventView> events = registry.listByJobKey("customer-load", 10);
		assertEquals(2, events.size());
		assertEquals("reason-c", events.get(0).reason());
		assertEquals("reason-b", events.get(1).reason());
	}

	@Test
	void appliesListLimit() {
		JdbcTriggerEventRegistry registry = new JdbcTriggerEventRegistry(new JdbcTemplate(inMemoryDataSource()), 10);
		registry.recordAccepted("customer-load", "reason-a", "user-a", "first");
		registry.recordAccepted("customer-load", "reason-b", "user-b", "second");

		List<TriggerEventView> events = registry.listByJobKey("customer-load", 1);
		assertEquals(1, events.size());
	}

	@Test
	void recordsAndListsByScheduleId() throws Exception {
		JdbcTriggerEventRegistry registry = new JdbcTriggerEventRegistry(new JdbcTemplate(inMemoryDataSource()), 10);
		registry.recordAcceptedForSchedule("sch-1", "customer-load", "schedule_tick", "scheduler", "first");
		Thread.sleep(5L);
		registry.recordAcceptedForSchedule("sch-2", "customer-load", "schedule_tick", "scheduler", "other");
		Thread.sleep(5L);
		registry.recordAcceptedForSchedule("sch-1", "customer-load", "schedule_tick", "scheduler", "second");

		List<TriggerEventView> events = registry.listByScheduleId("sch-1", 10);
		assertEquals(2, events.size());
		assertEquals("second", events.get(0).message());
		assertEquals("first", events.get(1).message());
	}

	@Test
	void recordsSchedulePkWhenScheduleExists() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcScheduleRegistry scheduleRegistry = new JdbcScheduleRegistry(jdbcTemplate);
		scheduleRegistry.upsert(new ScheduleView(
				"sch-1",
				"daily-a",
				"customer-load",
				"0 0 * * *",
				"UTC",
				true,
				false,
				"desc",
				LocalDateTime.parse("2026-05-28T09:00:00"),
				LocalDateTime.parse("2026-05-28T10:00:00"),
				null,
				null
		));

		JdbcTriggerEventRegistry triggerRegistry = new JdbcTriggerEventRegistry(jdbcTemplate, 10);
		triggerRegistry.recordAcceptedForSchedule("sch-1", "customer-load", "schedule_tick", "scheduler", "first");

		Long expectedSchedulePk = jdbcTemplate.queryForObject(
				"select schedule_pk from controlplane_schedule where schedule_id = ?",
				Long.class,
				"sch-1"
		);
		Long recordedSchedulePk = jdbcTemplate.queryForObject(
				"select schedule_pk from controlplane_trigger_event where schedule_id = ?",
				Long.class,
				"sch-1"
		);

		assertEquals(expectedSchedulePk, recordedSchedulePk);
	}

	private DriverManagerDataSource inMemoryDataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.sqlite.JDBC");
		Path databasePath = tempDir.resolve("cp-trigger.db");
		dataSource.setUrl("jdbc:sqlite:" + databasePath.toAbsolutePath().toString().replace('\\', '/'));
		return dataSource;
	}
}



