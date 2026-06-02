package com.etl.controlplane.monitoring;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JDBC-backed registry for durable run-summary projections.
 */
@Component
@ConditionalOnProperty(name = "controlplane.runs.persistence.mode", havingValue = "jdbc")
public class JdbcRunSummaryRegistry implements RunSummaryRegistry {
	private static final java.time.Duration TRIGGER_LOOKBACK_WINDOW = java.time.Duration.ofMinutes(30);
	private static final java.time.Duration TRIGGER_LOOKAHEAD_WINDOW = java.time.Duration.ofMinutes(5);

	private final JdbcTemplate jdbcTemplate;
	private final int retention;

	public JdbcRunSummaryRegistry(JdbcTemplate jdbcTemplate,
	                              @Value("${controlplane.runs.retention:5000}") int retention) {
		this.jdbcTemplate = jdbcTemplate;
		this.retention = Math.max(1, retention);
		initializeSchema();
	}

	@Override
	public void upsert(RunSummaryView runSummary) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null) {
			return;
		}
		int updated = jdbcTemplate.update("""
				update controlplane_run_summary
				set scenario = ?, status = ?, start_time = ?, end_time = ?, duration_seconds = ?,
				    source_count = ?, written_count = ?, rejected_count = ?, log_path = ?, last_seen_at = ?
				where job_execution_id = ?
				""",
				runSummary.scenario(),
				runSummary.status(),
				toTimestamp(runSummary.startTime()),
				toTimestamp(runSummary.endTime()),
				runSummary.durationSeconds(),
				runSummary.sourceCount(),
				runSummary.writtenCount(),
				runSummary.rejectedCount(),
				runSummary.logPath(),
				Timestamp.valueOf(LocalDateTime.now()),
				jobExecutionId
		);
		if (updated == 0) {
			jdbcTemplate.update("""
					insert into controlplane_run_summary (
						job_execution_id,
						scenario,
						status,
						start_time,
						end_time,
						duration_seconds,
						source_count,
						written_count,
						rejected_count,
						log_path,
						last_seen_at
					) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					jobExecutionId,
					runSummary.scenario(),
					runSummary.status(),
					toTimestamp(runSummary.startTime()),
					toTimestamp(runSummary.endTime()),
					runSummary.durationSeconds(),
					runSummary.sourceCount(),
					runSummary.writtenCount(),
					runSummary.rejectedCount(),
					runSummary.logPath(),
					Timestamp.valueOf(LocalDateTime.now())
			);
		}
		upsertRunRecord(runSummary);
		pruneOverflow();
	}

	@Override
	public List<RunSummaryView> latestRuns(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		List<RunSummaryView> runs = jdbcTemplate.query("""
				select job_execution_id, scenario, status, start_time, end_time, duration_seconds,
				       source_count, written_count, rejected_count, log_path
				from controlplane_run_summary
				order by case when start_time is null then 1 else 0 end,
				         start_time desc,
				         job_execution_id desc
				""", (rs, rowNum) -> new RunSummaryView(
				rs.getString("scenario"),
				rs.getLong("job_execution_id"),
				rs.getString("status"),
				toLocalDateTime(rs.getTimestamp("start_time")),
				toLocalDateTime(rs.getTimestamp("end_time")),
				nullableLong(rs, "duration_seconds"),
				nullableLong(rs, "source_count"),
				nullableLong(rs, "written_count"),
				nullableLong(rs, "rejected_count"),
				rs.getString("log_path")
		));
		return runs.size() <= limit ? runs : runs.subList(0, limit);
	}

	@Override
	public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
		List<RunSummaryView> matches = jdbcTemplate.query("""
				select job_execution_id, scenario, status, start_time, end_time, duration_seconds,
				       source_count, written_count, rejected_count, log_path
				from controlplane_run_summary
				where job_execution_id = ?
				""", (rs, rowNum) -> new RunSummaryView(
				rs.getString("scenario"),
				rs.getLong("job_execution_id"),
				rs.getString("status"),
				toLocalDateTime(rs.getTimestamp("start_time")),
				toLocalDateTime(rs.getTimestamp("end_time")),
				nullableLong(rs, "duration_seconds"),
				nullableLong(rs, "source_count"),
				nullableLong(rs, "written_count"),
				nullableLong(rs, "rejected_count"),
				rs.getString("log_path")
		), jobExecutionId);
		return matches.stream().findFirst();
	}

	private void pruneOverflow() {
		List<Long> ids = jdbcTemplate.queryForList("""
				select job_execution_id
				from controlplane_run_summary
				order by case when start_time is null then 1 else 0 end,
				         start_time desc,
				         job_execution_id desc
				""", Long.class);
		if (ids.size() <= retention) {
			return;
		}
		for (Long id : ids.subList(retention, ids.size())) {
			jdbcTemplate.update("delete from controlplane_run_summary where job_execution_id = ?", id);
		}
	}

	private void initializeSchema() {
		jdbcTemplate.execute("""
				create table if not exists controlplane_run_summary (
					job_execution_id bigint primary key,
					scenario varchar(200) not null,
					status varchar(50) not null,
					start_time timestamp,
					end_time timestamp,
					duration_seconds bigint,
					source_count bigint,
					written_count bigint,
					rejected_count bigint,
					log_path varchar(2000),
					last_seen_at timestamp not null
				)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_run_summary_start_time
				on controlplane_run_summary (start_time, job_execution_id)
				""");
		jdbcTemplate.execute("""
				create table if not exists controlplane_run_record (
					run_record_pk bigint,
					run_record_id varchar(80) primary key,
					job_execution_id bigint not null unique,
					trigger_event_pk bigint,
					trigger_event_id varchar(80),
					selected_job_key varchar(200),
					scenario varchar(200) not null,
					run_status varchar(50) not null,
					started_at timestamp,
					finished_at timestamp,
					duration_seconds bigint,
					source_count bigint,
					written_count bigint,
					rejected_count bigint,
					created_at timestamp not null,
					updated_at timestamp not null
				)
				""");
		ensureColumnExists("controlplane_run_record", "run_record_pk", "bigint");
		ensureColumnExists("controlplane_run_record", "trigger_event_pk", "bigint");
		backfillRunRecordPk();
		jdbcTemplate.execute("""
				create index if not exists idx_run_record_started_at
				on controlplane_run_record (started_at, job_execution_id)
				""");
		jdbcTemplate.execute("""
				create unique index if not exists idx_run_record_pk
				on controlplane_run_record (run_record_pk)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_run_record_selected_job
				on controlplane_run_record (selected_job_key, started_at)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_run_record_trigger_event_pk
				on controlplane_run_record (trigger_event_pk)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_run_record_trigger_event
				on controlplane_run_record (trigger_event_id)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_run_record_job_status_time
				on controlplane_run_record (selected_job_key, run_status, started_at)
				""");
		backfillRunRecordFromRunSummary();
		backfillRunRecordTriggerEventPk();
		backfillRunRecordSelectedJobKey();
		backfillRunRecordTriggerEventLinkage();
	}

	private void upsertRunRecord(RunSummaryView runSummary) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null) {
			return;
		}
		String selectedJobKey = normalize(runSummary.scenario());
		TriggerEventLink resolvedTriggerEvent = resolveTriggerEventLinkForUpsert(runSummary);
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		jdbcTemplate.update("""
				insert into controlplane_run_record (
					run_record_pk,
					run_record_id,
					job_execution_id,
					trigger_event_pk,
					trigger_event_id,
					selected_job_key,
					scenario,
					run_status,
					started_at,
					finished_at,
					duration_seconds,
					source_count,
					written_count,
					rejected_count,
					created_at,
					updated_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				on conflict(job_execution_id) do update set
					run_record_pk = coalesce(controlplane_run_record.run_record_pk, excluded.run_record_pk),
					trigger_event_pk = coalesce(controlplane_run_record.trigger_event_pk, excluded.trigger_event_pk),
					trigger_event_id = coalesce(controlplane_run_record.trigger_event_id, excluded.trigger_event_id),
					selected_job_key = coalesce(controlplane_run_record.selected_job_key, excluded.selected_job_key),
					scenario = excluded.scenario,
					run_status = excluded.run_status,
					started_at = excluded.started_at,
					finished_at = excluded.finished_at,
					duration_seconds = excluded.duration_seconds,
					source_count = excluded.source_count,
					written_count = excluded.written_count,
					rejected_count = excluded.rejected_count,
					updated_at = excluded.updated_at
				""",
				nextRunRecordPk(),
				"rr-" + jobExecutionId,
				jobExecutionId,
				resolvedTriggerEvent.triggerEventPk(),
				resolvedTriggerEvent.triggerEventId(),
				selectedJobKey.isBlank() ? null : selectedJobKey,
				runSummary.scenario(),
				runSummary.status(),
				toTimestamp(runSummary.startTime()),
				toTimestamp(runSummary.endTime()),
				runSummary.durationSeconds(),
				runSummary.sourceCount(),
				runSummary.writtenCount(),
				runSummary.rejectedCount(),
				now,
				now
		);
		backfillLaunchedRunLink(jobExecutionId, resolvedTriggerEvent);
	}

	private void backfillRunRecordFromRunSummary() {
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		jdbcTemplate.update("""
				insert into controlplane_run_record (
					run_record_pk,
					run_record_id,
					job_execution_id,
					trigger_event_pk,
					trigger_event_id,
					selected_job_key,
					scenario,
					run_status,
					started_at,
					finished_at,
					duration_seconds,
					source_count,
					written_count,
					rejected_count,
					created_at,
					updated_at
				)
				select
					null,
					'rr-' || cast(rs.job_execution_id as text),
					rs.job_execution_id,
					null,
					null,
					nullif(trim(rs.scenario), ''),
					rs.scenario,
					rs.status,
					rs.start_time,
					rs.end_time,
					rs.duration_seconds,
					rs.source_count,
					rs.written_count,
					rs.rejected_count,
					?,
					?
				from controlplane_run_summary rs
				where not exists (
					select 1
					from controlplane_run_record rr
					where rr.job_execution_id = rs.job_execution_id
				)
				""",
				now,
				now
		);
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

	private void backfillRunRecordPk() {
		jdbcTemplate.update("""
				update controlplane_run_record
				set run_record_pk = rowid
				where run_record_pk is null
				""");
	}

	private void backfillRunRecordTriggerEventPk() {
		try {
			jdbcTemplate.update("""
					update controlplane_run_record
					set trigger_event_pk = (
						select te.trigger_event_pk
						from controlplane_trigger_event te
						where te.trigger_event_id = controlplane_run_record.trigger_event_id
					)
					where trigger_event_pk is null
					  and trigger_event_id is not null
					  and exists (
						select 1
						from controlplane_trigger_event te
						where te.trigger_event_id = controlplane_run_record.trigger_event_id
					  )
					""");
		} catch (DataAccessException ignored) {
			// Keep run-summary persistence available when trigger table state is optional.
		}
	}

	private void backfillRunRecordSelectedJobKey() {
		jdbcTemplate.update("""
				update controlplane_run_record
				set selected_job_key = nullif(trim(scenario), '')
				where selected_job_key is null
				   or trim(selected_job_key) = ''
				""");
		try {
			jdbcTemplate.update("""
					update controlplane_run_record
					set selected_job_key = (
						select nullif(trim(te.job_key), '')
						from controlplane_trigger_event te
						where te.trigger_event_id = controlplane_run_record.trigger_event_id
					)
					where (selected_job_key is null or trim(selected_job_key) = '')
					  and trigger_event_id is not null
					  and exists (
						select 1
						from controlplane_trigger_event te
						where te.trigger_event_id = controlplane_run_record.trigger_event_id
					  )
					""");
		} catch (DataAccessException ignored) {
			// Keep run-summary persistence available when trigger table state is optional.
		}
	}

	private void backfillRunRecordTriggerEventLinkage() {
		List<RunRecordLinkCandidate> candidates;
		try {
			candidates = jdbcTemplate.query("""
					select job_execution_id, scenario, started_at
					from controlplane_run_record
					where trigger_event_id is null
					order by case when started_at is null then 1 else 0 end,
					         started_at desc,
					         job_execution_id desc
					""", (rs, rowNum) -> new RunRecordLinkCandidate(
					rs.getLong("job_execution_id"),
					rs.getString("scenario"),
					toLocalDateTime(rs.getTimestamp("started_at"))
			));
		} catch (DataAccessException ignored) {
			return;
		}
		for (RunRecordLinkCandidate candidate : candidates) {
			TriggerEventLink triggerEvent = resolveTriggerEventLinkForBackfill(candidate.jobExecutionId(), candidate.scenario(), candidate.startedAt());
			if (triggerEvent.isEmpty()) {
				continue;
			}
			jdbcTemplate.update(
					"update controlplane_run_record set trigger_event_id = ?, trigger_event_pk = ? where job_execution_id = ? and trigger_event_id is null",
					triggerEvent.triggerEventId(),
					triggerEvent.triggerEventPk(),
					candidate.jobExecutionId()
			);
			backfillLaunchedRunLink(candidate.jobExecutionId(), triggerEvent);
		}
	}

	private TriggerEventLink resolveTriggerEventLinkForUpsert(RunSummaryView runSummary) {
		return resolveTriggerEventLink(runSummary.jobExecutionId(), runSummary.scenario(), runSummary.startTime(), false);
	}

	private TriggerEventLink resolveTriggerEventLinkForBackfill(Long jobExecutionId, String scenario, LocalDateTime startedAt) {
		return resolveTriggerEventLink(jobExecutionId, scenario, startedAt, true);
	}

	private TriggerEventLink resolveTriggerEventLink(Long jobExecutionId, String scenario, LocalDateTime startedAt, boolean allowTimeWindowFallback) {
		if (jobExecutionId == null) {
			return TriggerEventLink.empty();
		}
		try {
			Long runRecordPk = resolveRunRecordPk(jobExecutionId);
			if (runRecordPk != null) {
				TriggerEventLink pkMatch = Optional.ofNullable(jdbcTemplate.query("""
						select trigger_event_id, trigger_event_pk
						from controlplane_trigger_event
						where launched_run_pk = ?
						order by requested_at desc, trigger_event_pk desc, trigger_event_id desc
						limit 1
						""", rs -> rs.next()
							? new TriggerEventLink(rs.getString("trigger_event_id"), nullableLong(rs, "trigger_event_pk"))
							: TriggerEventLink.empty(), runRecordPk)).orElse(TriggerEventLink.empty());
				if (!pkMatch.isEmpty()) {
					return pkMatch;
				}
			}
			TriggerEventLink directMatch = Optional.ofNullable(jdbcTemplate.query("""
					select trigger_event_id, trigger_event_pk
					from controlplane_trigger_event
					where launched_run_id = ?
					order by requested_at desc, trigger_event_pk desc, trigger_event_id desc
					limit 1
					""", rs -> rs.next()
						? new TriggerEventLink(rs.getString("trigger_event_id"), nullableLong(rs, "trigger_event_pk"))
						: TriggerEventLink.empty(), String.valueOf(jobExecutionId))).orElse(TriggerEventLink.empty());
			if (!directMatch.isEmpty()) {
				return directMatch;
			}
			if (!allowTimeWindowFallback) {
				return TriggerEventLink.empty();
			}
			if (startedAt == null) {
				return TriggerEventLink.empty();
			}
			String normalizedScenario = normalize(scenario);
			if (normalizedScenario.isBlank()) {
				return TriggerEventLink.empty();
			}
			Timestamp lowerBound = Timestamp.valueOf(startedAt.minus(TRIGGER_LOOKBACK_WINDOW));
			Timestamp upperBound = Timestamp.valueOf(startedAt.plus(TRIGGER_LOOKAHEAD_WINDOW));
				List<TriggerEventLink> candidates = jdbcTemplate.query("""
					select te.trigger_event_id, te.trigger_event_pk
					from controlplane_trigger_event te
					where te.job_key = ?
					  and te.decision_status = 'ACCEPTED'
					  and te.requested_at between ? and ?
					  and not exists (
						select 1
						from controlplane_run_record rr
										where (
											(rr.trigger_event_pk is not null and te.trigger_event_pk is not null and rr.trigger_event_pk = te.trigger_event_pk)
											or (rr.trigger_event_pk is null and rr.trigger_event_id = te.trigger_event_id)
										)
						  and rr.job_execution_id <> ?
					  )
									order by te.requested_at desc, te.trigger_event_pk desc, te.trigger_event_id desc
					limit 2
					""", (rs, rowNum) -> new TriggerEventLink(
						rs.getString("trigger_event_id"),
						nullableLong(rs, "trigger_event_pk")
					), normalizedScenario, lowerBound, upperBound, jobExecutionId);
			return candidates.size() == 1 ? candidates.get(0) : TriggerEventLink.empty();
		} catch (DataAccessException ignored) {
			// Trigger table remains optional while run-summary persistence stays available.
			return TriggerEventLink.empty();
		}
	}

	private Long resolveRunRecordPk(Long jobExecutionId) {
		if (jobExecutionId == null) {
			return null;
		}
		return jdbcTemplate.query(
				"select run_record_pk from controlplane_run_record where job_execution_id = ?",
				rs -> rs.next() ? rs.getObject(1, Long.class) : null,
				jobExecutionId
		);
	}

	private void backfillLaunchedRunLink(Long jobExecutionId, TriggerEventLink triggerEvent) {
		if (jobExecutionId == null || triggerEvent.isEmpty()) {
			return;
		}
		try {
			Long runRecordPk = resolveRunRecordPk(jobExecutionId);
			jdbcTemplate.update(
					"""
					update controlplane_trigger_event
					set launched_run_id = ?, launched_run_pk = ?
					where (trigger_event_pk = ? or trigger_event_id = ?)
					  and ((launched_run_id is null or trim(launched_run_id) = '') or launched_run_pk is null)
					""",
					String.valueOf(jobExecutionId),
					runRecordPk,
					triggerEvent.triggerEventPk(),
					triggerEvent.triggerEventId()
			);
		} catch (DataAccessException ignored) {
			// Trigger linkage is best-effort and should not block run-summary projection writes.
		}
	}

	private long nextRunRecordPk() {
		Long value = jdbcTemplate.queryForObject(
				"select coalesce(max(run_record_pk), 0) + 1 from controlplane_run_record",
				Long.class
		);
		return value == null ? 1L : value;
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private record RunRecordLinkCandidate(long jobExecutionId, String scenario, LocalDateTime startedAt) {
	}

	private record TriggerEventLink(String triggerEventId, Long triggerEventPk) {
		private static TriggerEventLink empty() {
			return new TriggerEventLink(null, null);
		}

		private boolean isEmpty() {
			return triggerEventId == null || triggerEventId.isBlank();
		}
	}

	private static Timestamp toTimestamp(LocalDateTime value) {
		return value == null ? null : Timestamp.valueOf(value);
	}

	private static LocalDateTime toLocalDateTime(Timestamp value) {
		return value == null ? null : value.toLocalDateTime();
	}

	private static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}
}

