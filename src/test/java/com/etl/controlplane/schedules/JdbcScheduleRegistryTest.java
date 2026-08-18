package com.etl.controlplane.schedules;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

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
	void stampsAuditActorFromApplicationName() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		JdbcScheduleRegistry registry = new JdbcScheduleRegistry(jdbcTemplate, "mysql", "controlplane-audit-test");
		registry.upsert(schedule("sch-audit", "daily-audit", LocalDateTime.parse("2026-05-28T09:00:00")));

		String createdBy = jdbcTemplate.queryForObject(
				"select created_by from controlplane_schedule where schedule_id = ?",
				String.class,
				"sch-audit"
		);
		String updatedBy = jdbcTemplate.queryForObject(
				"select updated_by from controlplane_schedule where schedule_id = ?",
				String.class,
				"sch-audit"
		);

		assertEquals("controlplane-audit-test", createdBy);
		assertEquals("controlplane-audit-test", updatedBy);
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
	void initializesAndUpsertsOnH2MySqlModeWithoutSqliteOnlySql() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(h2MySqlModeDataSource());
		JdbcScheduleRegistry registry = new JdbcScheduleRegistry(jdbcTemplate);

		registry.upsert(schedule("sch-h2", "daily-h2", LocalDateTime.parse("2026-05-28T11:00:00")));
		assertTrue(registry.findByScheduleId("sch-h2").isPresent());
		assertEquals(1L, jdbcTemplate.queryForObject("select count(*) from controlplane_schedule", Long.class));
	}

	@Test
	void usesBigintTypeForSchedulePkColumn() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcScheduleRegistry(jdbcTemplate);

		String columnType = columnType(jdbcTemplate, "controlplane_schedule", "schedule_pk");
		assertEquals("bigint", columnType == null ? "" : columnType.toLowerCase());
	}

	@Test
	void usesSchedulePkAsPrimaryKeyAndKeepsScheduleIdUnique() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(inMemoryDataSource());
		new JdbcScheduleRegistry(jdbcTemplate);

		assertTrue(isPrimaryKey(jdbcTemplate, "controlplane_schedule", "schedule_pk"));
		assertFalse(isPrimaryKey(jdbcTemplate, "controlplane_schedule", "schedule_id"));
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

		assertFalse(isPrimaryKey(jdbcTemplate, "controlplane_schedule", "schedule_pk"));
		assertTrue(isPrimaryKey(jdbcTemplate, "controlplane_schedule", "schedule_id"));

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
			assertTrue(ready.await(2, TimeUnit.SECONDS));
			start.countDown();
			executor.shutdown();
			assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
		}

		assertEquals(1, (firstResult.get() ? 1 : 0) + (secondResult.get() ? 1 : 0));
		assertEquals(dueAt, registry.findByScheduleId("sch-1").orElseThrow().lastAcceptedDueAt());
	}

	private static void awaitLatch(CountDownLatch latch) {
		try {
			assertTrue(latch.await(2, TimeUnit.SECONDS));
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
		dataSource.setUrl("jdbc:h2:mem:cp-schedules-" + System.nanoTime() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
		dataSource.setUsername("sa");
		dataSource.setPassword("");
		return dataSource;
	}
}

