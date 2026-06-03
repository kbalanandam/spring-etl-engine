package com.etl.controlplane.schedules;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-backed registry for durable schedule records.
 */
@Component
@ConditionalOnProperty(name = "controlplane.schedules.persistence.mode", havingValue = "jdbc")
public class JdbcScheduleRegistry implements ScheduleRegistry {

	private final JdbcTemplate jdbcTemplate;

	public JdbcScheduleRegistry(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
		initializeSchema();
	}

	@Override
	public ScheduleView upsert(ScheduleView schedule) {
		int updated = jdbcTemplate.update("""
				update controlplane_schedule
				set schedule_key = ?, selected_job_key = ?, expression = ?, timezone = ?,
				    is_enabled = ?, is_paused = ?, description = ?, updated_at = ?, watcher_key = ?, last_accepted_due_at = ?
				where schedule_id = ?
				""",
				schedule.scheduleKey(),
				schedule.selectedJobKey(),
				schedule.expression(),
				schedule.timezone(),
				schedule.enabled(),
				schedule.paused(),
				schedule.description(),
				toTimestamp(schedule.updatedAt()),
				schedule.watcherKey(),
				toTimestamp(schedule.lastAcceptedDueAt()),
				schedule.scheduleId()
		);
		if (updated == 0) {
			jdbcTemplate.update("""
					insert into controlplane_schedule (
						schedule_id, schedule_key, selected_job_key, expression, timezone,
						is_enabled, is_paused, description, created_at, updated_at, watcher_key, last_accepted_due_at, schedule_pk
					) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					schedule.scheduleId(),
					schedule.scheduleKey(),
					schedule.selectedJobKey(),
					schedule.expression(),
					schedule.timezone(),
					schedule.enabled(),
					schedule.paused(),
					schedule.description(),
					toTimestamp(schedule.createdAt()),
					toTimestamp(schedule.updatedAt()),
					schedule.watcherKey(),
					toTimestamp(schedule.lastAcceptedDueAt()),
					nextSchedulePk()
			);
		}
		return schedule;
	}

	@Override
	public Optional<ScheduleView> findByScheduleId(String scheduleId) {
		List<ScheduleView> schedules = jdbcTemplate.query("""
				select schedule_id, schedule_key, selected_job_key, expression, timezone,
				       is_enabled, is_paused, description, created_at, updated_at, watcher_key, last_accepted_due_at
				from controlplane_schedule
				where schedule_id = ?
				""", this::mapRow, normalize(scheduleId));
		return schedules.stream().findFirst();
	}

	@Override
	public Optional<ScheduleView> findByScheduleKey(String scheduleKey) {
		List<ScheduleView> schedules = jdbcTemplate.query("""
				select schedule_id, schedule_key, selected_job_key, expression, timezone,
				       is_enabled, is_paused, description, created_at, updated_at, watcher_key, last_accepted_due_at
				from controlplane_schedule
				where schedule_key = ?
				""", this::mapRow, normalize(scheduleKey));
		return schedules.stream().findFirst();
	}

	@Override
	public List<ScheduleView> list(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		List<ScheduleView> schedules = jdbcTemplate.query("""
				select schedule_id, schedule_key, selected_job_key, expression, timezone,
				       is_enabled, is_paused, description, created_at, updated_at, watcher_key, last_accepted_due_at
				from controlplane_schedule
				order by updated_at desc, schedule_id desc
				""", this::mapRow);
		return schedules.size() <= limit ? schedules : schedules.subList(0, limit);
	}

	@Override
	public boolean tryAdvanceLastAcceptedDueAt(String scheduleId, Instant dueAt) {
		if (dueAt == null) {
			return false;
		}
		String normalizedScheduleId = normalize(scheduleId);
		if (normalizedScheduleId.isBlank()) {
			return false;
		}
		Timestamp dueTimestamp = Timestamp.from(dueAt);
		int updated = jdbcTemplate.update("""
				update controlplane_schedule
				set last_accepted_due_at = ?, updated_at = ?
				where schedule_id = ?
				  and (last_accepted_due_at is null or last_accepted_due_at < ?)
				""",
				dueTimestamp,
				Timestamp.valueOf(LocalDateTime.now()),
				normalizedScheduleId,
				dueTimestamp
		);
		return updated > 0;
	}

	private ScheduleView mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new ScheduleView(
				rs.getString("schedule_id"),
				rs.getString("schedule_key"),
				rs.getString("selected_job_key"),
				rs.getString("expression"),
				rs.getString("timezone"),
				rs.getBoolean("is_enabled"),
				rs.getBoolean("is_paused"),
				rs.getString("description"),
				toLocalDateTime(rs.getTimestamp("created_at")),
				toLocalDateTime(rs.getTimestamp("updated_at")),
				rs.getString("watcher_key"),
				toInstant(rs.getTimestamp("last_accepted_due_at"))
		);
	}

	private void initializeSchema() {
		jdbcTemplate.execute("""
				create table if not exists controlplane_schedule (
					schedule_pk bigint primary key,
					schedule_id varchar(80) not null unique,
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
					last_accepted_due_at timestamp
				)
				""");
		migrateLegacyPrimaryKeyIfRequired();
		ensureColumnExists("controlplane_schedule", "last_accepted_due_at", "timestamp");
		ensureColumnExists("controlplane_schedule", "schedule_pk", "bigint");
		backfillSchedulePk();
		jdbcTemplate.execute("""
				create unique index if not exists idx_schedule_pk
				on controlplane_schedule (schedule_pk)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_schedule_selected_job
				on controlplane_schedule (selected_job_key, updated_at)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_schedule_state
				on controlplane_schedule (is_enabled, is_paused, updated_at)
				""");
	}

	private void migrateLegacyPrimaryKeyIfRequired() {
		List<String> primaryKeyColumns = jdbcTemplate.query(
				"select lower(name) from pragma_table_info('controlplane_schedule') where pk > 0 order by pk",
				(rs, rowNum) -> rs.getString(1)
		);
		if (primaryKeyColumns.size() == 1 && "schedule_pk".equals(primaryKeyColumns.get(0))) {
			return;
		}

		jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
			boolean originalAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);
			try (java.sql.Statement statement = connection.createStatement()) {
				statement.execute("""
						create table controlplane_schedule_new (
							schedule_pk bigint primary key,
							schedule_id varchar(80) not null unique,
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
							last_accepted_due_at timestamp
						)
						""");
				statement.execute("""
						insert into controlplane_schedule_new (
							schedule_pk,
							schedule_id,
							schedule_key,
							selected_job_key,
							expression,
							timezone,
							is_enabled,
							is_paused,
							description,
							created_at,
							updated_at,
							watcher_key,
							last_accepted_due_at
						)
						select
							coalesce(schedule_pk, rowid),
							schedule_id,
							schedule_key,
							selected_job_key,
							expression,
							timezone,
							is_enabled,
							is_paused,
							description,
							created_at,
							updated_at,
							watcher_key,
							last_accepted_due_at
						from controlplane_schedule
						""");
				statement.execute("drop table controlplane_schedule");
				statement.execute("alter table controlplane_schedule_new rename to controlplane_schedule");
				connection.commit();
			} catch (java.sql.SQLException ex) {
				connection.rollback();
				throw ex;
			} catch (RuntimeException ex) {
				connection.rollback();
				throw ex;
			} finally {
				connection.setAutoCommit(originalAutoCommit);
			}
			return null;
		});
	}

	private void ensureColumnExists(String tableName, String columnName, String columnDefinition) {
		Boolean columnExists = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
			try (java.sql.Statement statement = connection.createStatement();
			     java.sql.ResultSet resultSet = statement.executeQuery("select * from " + tableName + " where 1 = 0")) {
				java.sql.ResultSetMetaData metadata = resultSet.getMetaData();
				for (int index = 1; index <= metadata.getColumnCount(); index++) {
					if (columnName.equalsIgnoreCase(metadata.getColumnName(index))) {
						return true;
					}
				}
				return false;
			}
		});
		if (Boolean.FALSE.equals(columnExists)) {
			jdbcTemplate.execute("alter table " + tableName + " add column " + columnName + " " + columnDefinition);
		}
	}

	private void backfillSchedulePk() {
		jdbcTemplate.update("""
				update controlplane_schedule
				set schedule_pk = rowid
				where schedule_pk is null
				""");
	}

	private long nextSchedulePk() {
		Long value = jdbcTemplate.queryForObject("select coalesce(max(schedule_pk), 0) + 1 from controlplane_schedule", Long.class);
		return value == null ? 1L : value;
	}

	private static Timestamp toTimestamp(LocalDateTime value) {
		return value == null ? null : Timestamp.valueOf(value);
	}

	private static LocalDateTime toLocalDateTime(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}

	private static Timestamp toTimestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private static java.time.Instant toInstant(Timestamp value) {
		return value == null ? null : value.toInstant();
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase();
	}
}

