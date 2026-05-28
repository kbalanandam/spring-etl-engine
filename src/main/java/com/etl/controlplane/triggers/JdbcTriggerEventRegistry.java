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
		String normalizedJobKey = normalize(jobKey);
		String normalizedReason = normalize(reason);
		String normalizedRequestedBy = normalize(requestedBy);
		Instant requestedAt = Instant.now();
		String triggerEventId = "te-" + UUID.randomUUID();

		jdbcTemplate.update("""
				insert into controlplane_trigger_event (
					trigger_event_id,
					job_key,
					decision_status,
					reason,
					requested_by,
					requested_at,
					launched_run_id,
					message,
					trigger_origin,
					schedule_id,
					watcher_id,
					external_origin_key
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				triggerEventId,
				normalizedJobKey,
				"ACCEPTED",
				normalizedReason,
				normalizedRequestedBy,
				Timestamp.from(requestedAt),
				null,
				message,
				"MANUAL",
				null,
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
				(rs, rowNum) -> new TriggerEventView(
						rs.getString("trigger_event_id"),
						rs.getString("job_key"),
						rs.getString("decision_status"),
						rs.getString("reason"),
						rs.getString("requested_by"),
						rs.getTimestamp("requested_at").toInstant(),
						rs.getString("launched_run_id"),
						rs.getString("message")
				),
				normalize(jobKey)
		);
		return events.size() <= limit ? events : events.subList(0, limit);
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
					trigger_event_id varchar(80) primary key,
					job_key varchar(200) not null,
					decision_status varchar(50) not null,
					reason varchar(200),
					requested_by varchar(200),
					requested_at timestamp not null,
					launched_run_id varchar(80),
					message varchar(2000),
					trigger_origin varchar(50),
					schedule_id varchar(80),
					watcher_id varchar(80),
					external_origin_key varchar(200)
				)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_trigger_event_job_time
				on controlplane_trigger_event (job_key, requested_at)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_trigger_event_origin
				on controlplane_trigger_event (trigger_origin, requested_at)
				""");
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}

