package com.etl.controlplane.triggers;

import com.etl.controlplane.schedules.JdbcScheduleRegistry;
import com.etl.controlplane.schedules.ScheduleView;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTriggerEventRegistryTest {

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
		assertEquals("MANUAL", events.get(0).triggerOrigin());
	}

	@Test
	void assignsTriggerEventPkForNewRows() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcTriggerEventRegistry registry = new JdbcTriggerEventRegistry(jdbcTemplate, 10);
		TriggerEventView created = registry.recordAccepted("customer-load", "reason-a", "user-a", "first");

		Long triggerEventPk = jdbcTemplate.queryForObject(
				"select trigger_event_pk from controlplane_trigger_event where trigger_event_id = ?",
				Long.class,
				created.triggerEventId()
		);
		assertEquals(1L, triggerEventPk);
	}

	@Test
	void initializesAndRecordsAcceptedEventOnH2MySqlMode() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(h2MySqlModeDataSource());
		JdbcTriggerEventRegistry registry = new JdbcTriggerEventRegistry(jdbcTemplate, 10);

		TriggerEventView created = registry.recordAccepted("customer-load", "manual_operator_request", "operator-ui", "portable");
		assertEquals("ACCEPTED", created.decisionStatus());
		assertEquals(1L, jdbcTemplate.queryForObject("select count(*) from controlplane_trigger_event", Long.class));
	}

	@Test
	void usesBigintTypeForSurrogateAndLinkageColumns() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcTriggerEventRegistry(jdbcTemplate, 10);

		String schedulePkType = columnType(jdbcTemplate, "controlplane_trigger_event", "schedule_pk");
		String triggerEventPkType = columnType(jdbcTemplate, "controlplane_trigger_event", "trigger_event_pk");
		String launchedRunPkType = columnType(jdbcTemplate, "controlplane_trigger_event", "launched_run_pk");
		assertEquals("bigint", schedulePkType == null ? "" : schedulePkType.toLowerCase());
		assertEquals("bigint", triggerEventPkType == null ? "" : triggerEventPkType.toLowerCase());
		assertEquals("bigint", launchedRunPkType == null ? "" : launchedRunPkType.toLowerCase());
	}

	@Test
	void usesTriggerEventPkAsPrimaryKeyAndKeepsTriggerEventIdUnique() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcTriggerEventRegistry(jdbcTemplate, 10);

		assertTrue(isPrimaryKey(jdbcTemplate, "controlplane_trigger_event", "trigger_event_pk"));
		assertFalse(isPrimaryKey(jdbcTemplate, "controlplane_trigger_event", "trigger_event_id"));
	}

	@Test
	void migratesLegacyTriggerEventIdPrimaryKeyShape() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		jdbcTemplate.execute("""
				create table controlplane_trigger_event (
					trigger_event_pk bigint,
					trigger_event_id varchar(80) primary key,
					job_key varchar(200) not null,
					decision_status varchar(50) not null,
					reason varchar(200),
					requested_by varchar(200),
					requested_at timestamp not null,
					launched_run_pk bigint,
					launched_run_id varchar(80),
					message varchar(2000),
					trigger_origin varchar(50),
					schedule_pk bigint,
					external_origin_key varchar(200)
				)
				""");
		jdbcTemplate.update("""
				insert into controlplane_trigger_event (
					trigger_event_pk, trigger_event_id, job_key, decision_status, reason, requested_by,
					requested_at, launched_run_pk, launched_run_id, message, trigger_origin, schedule_pk, external_origin_key
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				17L, "te-legacy", "customer-load", "ACCEPTED", "manual_operator_request", "operator-ui",
				java.sql.Timestamp.valueOf("2026-05-28 10:00:00"), null, null, "legacy", "MANUAL", null, null
		);

		new JdbcTriggerEventRegistry(jdbcTemplate, 10);

		assertFalse(isPrimaryKey(jdbcTemplate, "controlplane_trigger_event", "trigger_event_pk"));
		assertTrue(isPrimaryKey(jdbcTemplate, "controlplane_trigger_event", "trigger_event_id"));

		Long migratedPk = jdbcTemplate.queryForObject(
				"select trigger_event_pk from controlplane_trigger_event where trigger_event_id = ?",
				Long.class,
				"te-legacy"
		);
		assertEquals(17L, migratedPk);
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
		scheduleRegistry.upsert(new ScheduleView(
				"sch-2",
				"daily-b",
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
		JdbcTriggerEventRegistry registry = new JdbcTriggerEventRegistry(jdbcTemplate, 10);
		registry.recordAcceptedForSchedule("sch-1", "customer-load", "schedule_tick", "scheduler", "first");
		Thread.sleep(5L);
		registry.recordAcceptedForSchedule("sch-2", "customer-load", "schedule_tick", "scheduler", "other");
		Thread.sleep(5L);
		registry.recordAcceptedForSchedule("sch-1", "customer-load", "schedule_tick", "scheduler", "second");

		List<TriggerEventView> events = registry.listByScheduleId("sch-1", 10);
		assertEquals(2, events.size());
		assertEquals("second", events.get(0).message());
		assertEquals("first", events.get(1).message());
		assertEquals("SCHEDULE", events.get(0).triggerOrigin());
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
		TriggerEventView created = triggerRegistry.recordAcceptedForSchedule("sch-1", "customer-load", "schedule_tick", "scheduler", "first");

		Long expectedSchedulePk = jdbcTemplate.queryForObject(
				"select schedule_pk from controlplane_schedule where schedule_id = ?",
				Long.class,
				"sch-1"
		);
		Long recordedSchedulePk = jdbcTemplate.queryForObject(
				"select schedule_pk from controlplane_trigger_event where trigger_event_id = ?",
				Long.class,
				created.triggerEventId()
		);

		assertEquals(expectedSchedulePk, recordedSchedulePk);
	}

	@Test
	void backfillsLegacyTriggerEventPkOnStartup() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcTriggerEventRegistry(jdbcTemplate, 10);
		jdbcTemplate.update("""
				insert into controlplane_trigger_event (
					trigger_event_pk, trigger_event_id, job_key, decision_status, reason, requested_by,
					requested_at, launched_run_id, launched_run_pk, message, trigger_origin
				) values (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?)
				""",
				22L, "te-legacy-pk-1", "customer-load", "ACCEPTED", "manual_operator_request", "operator-ui",
				null, null, "legacy", "MANUAL"
		);

		new JdbcTriggerEventRegistry(jdbcTemplate, 10);

		Long triggerEventPk = jdbcTemplate.queryForObject(
				"select trigger_event_pk from controlplane_trigger_event where trigger_event_id = ?",
				Long.class,
				"te-legacy-pk-1"
		);
		assertEquals(22L, triggerEventPk);
	}

	@Test
	void listByScheduleIdReturnsOnlySchedulePkLinkedRows() {
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
		assertEquals(1, events.size());
		assertEquals(second.triggerEventId(), events.get(0).triggerEventId());
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

		assertEquals(expectedSchedulePk, recordedSchedulePk);
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

	@Test
	void listByScheduleIdInfersScheduleOriginWhenLegacyTriggerOriginIsMissing() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcScheduleRegistry scheduleRegistry = new JdbcScheduleRegistry(jdbcTemplate);
		scheduleRegistry.upsert(new ScheduleView(
				"sch-legacy-origin",
				"daily-legacy-origin",
				"customer-load",
				"0 0 * * *",
				"UTC",
				true,
				false,
				"legacy-origin",
				LocalDateTime.parse("2026-05-28T09:00:00"),
				LocalDateTime.parse("2026-05-28T10:00:00"),
				null,
				null
		));

		JdbcTriggerEventRegistry registry = new JdbcTriggerEventRegistry(jdbcTemplate, 10);
		TriggerEventView created = registry.recordAcceptedForSchedule("sch-legacy-origin", "customer-load", "schedule_tick", "scheduler", "legacy-origin-row");
		jdbcTemplate.update("update controlplane_trigger_event set trigger_origin = null where trigger_event_id = ?", created.triggerEventId());

		List<TriggerEventView> events = registry.listByScheduleId("sch-legacy-origin", 10);
		assertEquals(1, events.size());
		assertEquals("SCHEDULE", events.get(0).triggerOrigin());
	}

	private DriverManagerDataSource inMemoryDataSource() {
		return h2MySqlModeDataSource();
	}

	private String columnType(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
		return jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<String>) connection -> {
			try (java.sql.ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, tableName, columnName)) {
				return columns.next() ? columns.getString("TYPE_NAME") : "";
			}
		});
	}

	private boolean isPrimaryKey(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
		Boolean isPrimary = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
			try (java.sql.ResultSet primaryKeys = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, tableName)) {
				while (primaryKeys.next()) {
					String existingColumnName = primaryKeys.getString("COLUMN_NAME");
					if (existingColumnName != null && columnName.equalsIgnoreCase(existingColumnName)) {
						return true;
					}
				}
				return false;
			}
		});
		return Boolean.TRUE.equals(isPrimary);
	}

	private DriverManagerDataSource h2MySqlModeDataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName("org.h2.Driver");
		dataSource.setUrl("jdbc:h2:mem:cp-trigger-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");
		return dataSource;
	}
}



