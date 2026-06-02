package com.etl.controlplane.triggers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JDBC-backed trigger-event registry for durable control-plane trigger history.
 */
@Component
@ConditionalOnProperty(name = "controlplane.triggers.persistence.mode", havingValue = "jdbc")
public class JdbcTriggerEventRegistry implements TriggerEventRegistry {

	private final JdbcTemplate jdbcTemplate;
	private final int retentionPerJob;

	public JdbcTriggerEventRegistry(JdbcTemplate jdbcTemplate,
	                                @Value("${controlplane.triggers.retention-per-job:100}") int retentionPerJob) {
		this.jdbcTemplate = jdbcTemplate;
		this.retentionPerJob = Math.max(1, retentionPerJob);
		initializeSchema();
	}

	@Override
	public TriggerEventView recordAccepted(String jobKey, String reason, String requestedBy, String message) {
		return recordAcceptedInternal(null, "MANUAL", jobKey, reason, requestedBy, message);
	}

	@Override
	public TriggerEventView recordAcceptedForSchedule(String scheduleId, String jobKey, String reason, String requestedBy, String message) {
		return recordAcceptedInternal(scheduleId, "SCHEDULE", jobKey, reason, requestedBy, message);
	}

	private TriggerEventView recordAcceptedInternal(String scheduleId,
	                                              String triggerOrigin,
	                                              String jobKey,
	                                              String reason,
	                                              String requestedBy,
	                                              String message) {
		String normalizedJobKey = normalize(jobKey);
		String normalizedScheduleId = normalizeScheduleId(scheduleId);
		String normalizedReason = normalize(reason);
		String normalizedRequestedBy = normalize(requestedBy);
		Long triggerEventPk = nextTriggerEventPk();
		Long schedulePk = resolveSchedulePk(normalizedScheduleId);
		Instant requestedAt = Instant.now();
		String triggerEventId = "te-" + UUID.randomUUID();

		jdbcTemplate.update("""
				insert into controlplane_trigger_event (
					trigger_event_pk,
					trigger_event_id,
					job_key,
					decision_status,
					reason,
					requested_by,
					requested_at,
					launched_run_pk,
					launched_run_id,
					message,
					trigger_origin,
					schedule_id,
					schedule_pk,
					watcher_id,
					external_origin_key
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				triggerEventPk,
				triggerEventId,
				normalizedJobKey,
				"ACCEPTED",
				normalizedReason,
				normalizedRequestedBy,
				Timestamp.from(requestedAt),
				null,
				null,
				message,
				triggerOrigin,
				normalizedScheduleId.isBlank() ? null : normalizedScheduleId,
				schedulePk,
				null,
				null
		);
		pruneOverflow(normalizedJobKey);

		return new TriggerEventView(
				triggerEventId,
				normalizedJobKey,
				"ACCEPTED",
				normalizedReason,
				normalizedRequestedBy,
				requestedAt,
				null,
				message
		);
	}

	@Override
	public List<TriggerEventView> listByJobKey(String jobKey, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		List<TriggerEventView> events = jdbcTemplate.query("""
				select trigger_event_id, job_key, decision_status, reason, requested_by, requested_at, launched_run_id, message
				from controlplane_trigger_event
				where job_key = ?
				order by requested_at desc, trigger_event_id desc
				""",
				(rs, rowNum) -> toView(rs),
				normalize(jobKey)
		);
		return events.size() <= limit ? events : events.subList(0, limit);
	}

	@Override
	public List<TriggerEventView> listByScheduleId(String scheduleId, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		String normalizedScheduleId = normalizeScheduleId(scheduleId);
		if (normalizedScheduleId.isBlank()) {
			return List.of();
		}
		Long schedulePk = resolveSchedulePk(normalizedScheduleId);
		List<TriggerEventView> events = schedulePk == null
				? jdbcTemplate.query("""
						select trigger_event_id, job_key, decision_status, reason, requested_by, requested_at, launched_run_id, message
						from controlplane_trigger_event
						where lower(trim(schedule_id)) = ?
						order by requested_at desc, trigger_event_id desc
						""",
						(rs, rowNum) -> toView(rs),
						normalizedScheduleId
				)
				: jdbcTemplate.query("""
						select trigger_event_id, job_key, decision_status, reason, requested_by, requested_at, launched_run_id, message
						from controlplane_trigger_event
						where schedule_pk = ?
						   or (schedule_pk is null and lower(trim(schedule_id)) = ?)
						order by requested_at desc, trigger_event_id desc
						""",
						(rs, rowNum) -> toView(rs),
						schedulePk,
						normalizedScheduleId
				);
		return events.size() <= limit ? events : events.subList(0, limit);
	}

	private TriggerEventView toView(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new TriggerEventView(
				rs.getString("trigger_event_id"),
				rs.getString("job_key"),
				rs.getString("decision_status"),
				rs.getString("reason"),
				rs.getString("requested_by"),
				rs.getTimestamp("requested_at").toInstant(),
				rs.getString("launched_run_id"),
				rs.getString("message")
		);
	}

	private void pruneOverflow(String jobKey) {
		List<String> ids = jdbcTemplate.queryForList("""
				select trigger_event_id
				from controlplane_trigger_event
				where job_key = ?
				order by requested_at desc, trigger_event_id desc
				""", String.class, jobKey);
		if (ids.size() <= retentionPerJob) {
			return;
		}
		for (String id : ids.subList(retentionPerJob, ids.size())) {
			jdbcTemplate.update("delete from controlplane_trigger_event where trigger_event_id = ?", id);
		}
	}

	private void initializeSchema() {
		jdbcTemplate.execute("""
				create table if not exists controlplane_trigger_event (
					trigger_event_pk integer,
					trigger_event_id varchar(80) primary key,
					job_key varchar(200) not null,
					decision_status varchar(50) not null,
					reason varchar(200),
					requested_by varchar(200),
					requested_at timestamp not null,
					launched_run_pk integer,
					launched_run_id varchar(80),
					message varchar(2000),
					trigger_origin varchar(50),
					schedule_id varchar(80),
					schedule_pk integer,
					watcher_id varchar(80),
					external_origin_key varchar(200)
				)
				""");
		ensureColumnExists("controlplane_trigger_event", "trigger_event_pk", "integer");
		ensureColumnExists("controlplane_trigger_event", "launched_run_pk", "integer");
		ensureColumnExists("controlplane_trigger_event", "schedule_pk", "integer");
		backfillTriggerEventPk();
		backfillSchedulePk();
		backfillLaunchedRunPk();
		jdbcTemplate.execute("""
				create unique index if not exists idx_trigger_event_pk
				on controlplane_trigger_event (trigger_event_pk)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_trigger_event_job_time
				on controlplane_trigger_event (job_key, requested_at)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_trigger_event_origin
				on controlplane_trigger_event (trigger_origin, requested_at)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_trigger_event_schedule_time
				on controlplane_trigger_event (schedule_id, requested_at)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_trigger_event_schedule_pk_time
				on controlplane_trigger_event (schedule_pk, requested_at)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_trigger_event_launched_run_pk
				on controlplane_trigger_event (launched_run_pk, requested_at)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_trigger_event_launched_run_id
				on controlplane_trigger_event (launched_run_id, requested_at)
				""");
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
		try {
			jdbcTemplate.update("""
					update controlplane_trigger_event
					set schedule_pk = (
						select schedule_pk
						from controlplane_schedule
						where lower(trim(controlplane_schedule.schedule_id)) = lower(trim(controlplane_trigger_event.schedule_id))
					)
					where schedule_pk is null
					  and schedule_id is not null
					  and exists (
						select 1
						from controlplane_schedule
						where lower(trim(controlplane_schedule.schedule_id)) = lower(trim(controlplane_trigger_event.schedule_id))
					)
					""");
		} catch (org.springframework.dao.DataAccessException ignored) {
			// Keep trigger persistence available even when schedule table state is optional.
		}
	}

	private void backfillTriggerEventPk() {
		jdbcTemplate.update("""
				update controlplane_trigger_event
				set trigger_event_pk = rowid
				where trigger_event_pk is null
				""");
	}

	private void backfillLaunchedRunPk() {
		try {
			jdbcTemplate.update("""
					update controlplane_trigger_event
					set launched_run_pk = (
						select rr.run_record_pk
						from controlplane_run_record rr
						where cast(rr.job_execution_id as text) = trim(controlplane_trigger_event.launched_run_id)
					)
					where launched_run_pk is null
					  and launched_run_id is not null
					  and trim(launched_run_id) <> ''
					  and exists (
						select 1
						from controlplane_run_record rr
						where cast(rr.job_execution_id as text) = trim(controlplane_trigger_event.launched_run_id)
					  )
					""");
		} catch (org.springframework.dao.DataAccessException ignored) {
			// Keep trigger persistence available even when run-record table state is optional.
		}
	}

	private Long nextTriggerEventPk() {
		Long value = jdbcTemplate.queryForObject(
				"select coalesce(max(trigger_event_pk), 0) + 1 from controlplane_trigger_event",
				Long.class
		);
		return value == null ? 1L : value;
	}

	private Long resolveSchedulePk(String normalizedScheduleId) {
		if (normalizedScheduleId.isBlank()) {
			return null;
		}
		try {
			return jdbcTemplate.query(
					"select schedule_pk from controlplane_schedule where lower(trim(schedule_id)) = ?",
					rs -> rs.next() ? rs.getObject(1, Long.class) : null,
					normalizedScheduleId
			);
		} catch (org.springframework.dao.DataAccessException ignored) {
			// Keep trigger persistence available even when schedule table/column state is older or optional.
			return null;
		}
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private String normalizeScheduleId(String value) {
		return normalize(value).toLowerCase();
	}
}


