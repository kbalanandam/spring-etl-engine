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
						is_enabled, is_paused, description, created_at, updated_at, watcher_key, last_accepted_due_at
					) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
					toTimestamp(schedule.lastAcceptedDueAt())
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
					last_accepted_due_at timestamp
				)
				""");
		jdbcTemplate.execute("alter table controlplane_schedule add column if not exists last_accepted_due_at timestamp");
		jdbcTemplate.execute("""
				create index if not exists idx_schedule_selected_job
				on controlplane_schedule (selected_job_key, updated_at)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_schedule_state
				on controlplane_schedule (is_enabled, is_paused, updated_at)
				""");
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

