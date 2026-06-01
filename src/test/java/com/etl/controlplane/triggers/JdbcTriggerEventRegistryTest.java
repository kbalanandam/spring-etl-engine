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

	@Test
	void backfillsLegacyScheduleEventsWithSchedulePkOnStartup() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcTriggerEventRegistry(jdbcTemplate, 10);
		jdbcTemplate.update("""
				insert into controlplane_trigger_event (
					trigger_event_id, job_key, decision_status, reason, requested_by,
					requested_at, message, trigger_origin, schedule_id, schedule_pk
				) values (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?)
				""",
				"te-legacy-1", "customer-load", "ACCEPTED", "schedule_tick", "scheduler",
				"legacy", "SCHEDULE", "sch-legacy", null
		);

		JdbcScheduleRegistry scheduleRegistry = new JdbcScheduleRegistry(jdbcTemplate);
		scheduleRegistry.upsert(new ScheduleView(
				"sch-legacy",
				"daily-legacy",
				"customer-load",
				"0 0 * * *",
				"UTC",
				true,
				false,
				"legacy",
				LocalDateTime.parse("2026-05-28T09:00:00"),
				LocalDateTime.parse("2026-05-28T10:00:00"),
				null,
				null
		));

		new JdbcTriggerEventRegistry(jdbcTemplate, 10);

		Long expectedSchedulePk = jdbcTemplate.queryForObject(
				"select schedule_pk from controlplane_schedule where schedule_id = ?",
				Long.class,
				"sch-legacy"
		);
		Long backfilledSchedulePk = jdbcTemplate.queryForObject(
				"select schedule_pk from controlplane_trigger_event where trigger_event_id = ?",
				Long.class,
				"te-legacy-1"
		);
		assertEquals(expectedSchedulePk, backfilledSchedulePk);
	}

	@Test
	void listByScheduleIdReturnsPkAndLegacyRowsTogether() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcScheduleRegistry scheduleRegistry = new JdbcScheduleRegistry(jdbcTemplate);
		scheduleRegistry.upsert(new ScheduleView(
				"sch-join",
				"daily-join",
				"customer-load",
				"0 0 * * *",
				"UTC",
				true,
				false,
				"join",
				LocalDateTime.parse("2026-05-28T09:00:00"),
				LocalDateTime.parse("2026-05-28T10:00:00"),
				null,
				null
		));
		JdbcTriggerEventRegistry registry = new JdbcTriggerEventRegistry(jdbcTemplate, 10);
		TriggerEventView first = registry.recordAcceptedForSchedule("sch-join", "customer-load", "schedule_tick", "scheduler", "first");
		jdbcTemplate.update("update controlplane_trigger_event set schedule_pk = null where trigger_event_id = ?", first.triggerEventId());
		TriggerEventView second = registry.recordAcceptedForSchedule("sch-join", "customer-load", "schedule_tick", "scheduler", "second");

		List<TriggerEventView> events = registry.listByScheduleId("sch-join", 10);
		assertEquals(2, events.size());
		assertEquals(second.triggerEventId(), events.get(0).triggerEventId());
		assertEquals(first.triggerEventId(), events.get(1).triggerEventId());
	}

	@Test
	void resolvesSchedulePkForMixedCaseAndWhitespaceScheduleIdInputs() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcScheduleRegistry scheduleRegistry = new JdbcScheduleRegistry(jdbcTemplate);
		scheduleRegistry.upsert(new ScheduleView(
				"sch-mixed",
				"daily-mixed",
				"customer-load",
				"0 0 * * *",
				"UTC",
				true,
				false,
				"mixed",
				LocalDateTime.parse("2026-05-28T09:00:00"),
				LocalDateTime.parse("2026-05-28T10:00:00"),
				null,
				null
		));

		JdbcTriggerEventRegistry registry = new JdbcTriggerEventRegistry(jdbcTemplate, 10);
		TriggerEventView created = registry.recordAcceptedForSchedule("  SCH-MIXED  ", "customer-load", "schedule_tick", "scheduler", "mixed-case");

		Long expectedSchedulePk = jdbcTemplate.queryForObject(
				"select schedule_pk from controlplane_schedule where schedule_id = ?",
				Long.class,
				"sch-mixed"
		);
		Long recordedSchedulePk = jdbcTemplate.queryForObject(
				"select schedule_pk from controlplane_trigger_event where trigger_event_id = ?",
				Long.class,
				created.triggerEventId()
		);
		String recordedScheduleId = jdbcTemplate.queryForObject(
				"select schedule_id from controlplane_trigger_event where trigger_event_id = ?",
				String.class,
				created.triggerEventId()
		);

		assertEquals(expectedSchedulePk, recordedSchedulePk);
		assertEquals("sch-mixed", recordedScheduleId);
	}

	@Test
	void listByScheduleIdMatchesRowsCaseInsensitively() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcScheduleRegistry scheduleRegistry = new JdbcScheduleRegistry(jdbcTemplate);
		scheduleRegistry.upsert(new ScheduleView(
				"sch-case",
				"daily-case",
				"customer-load",
				"0 0 * * *",
				"UTC",
				true,
				false,
				"case",
				LocalDateTime.parse("2026-05-28T09:00:00"),
				LocalDateTime.parse("2026-05-28T10:00:00"),
				null,
				null
		));
		JdbcTriggerEventRegistry registry = new JdbcTriggerEventRegistry(jdbcTemplate, 10);
		registry.recordAcceptedForSchedule("sch-case", "customer-load", "schedule_tick", "scheduler", "first");

		List<TriggerEventView> events = registry.listByScheduleId("  SCH-CASE  ", 10);
		assertEquals(1, events.size());
		assertEquals("first", events.get(0).message());
	}

	private DriverManagerDataSource inMemoryDataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.sqlite.JDBC");
		Path databasePath = tempDir.resolve("cp-trigger.db");
		dataSource.setUrl("jdbc:sqlite:" + databasePath.toAbsolutePath().toString().replace('\\', '/'));
		return dataSource;
	}
}



