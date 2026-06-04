package com.etl.controlplane.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC-backed registry for durable run-summary projections.
 */
@Component
@ConditionalOnProperty(name = "controlplane.runs.persistence.mode", havingValue = "jdbc")
public class JdbcRunSummaryRegistry implements RunSummaryRegistry {
	private static final java.time.Duration TRIGGER_LOOKBACK_WINDOW = java.time.Duration.ofMinutes(30);
	private static final java.time.Duration TRIGGER_LOOKAHEAD_WINDOW = java.time.Duration.ofMinutes(5);
	private static final ObjectMapper CONTEXT_OBJECT_MAPPER = new ObjectMapper();

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
				    source_count = ?, written_count = ?, rejected_count = ?, run_mode = ?, recovery_policy = ?, log_path = ?, last_seen_at = ?
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
				runSummary.runMode(),
				runSummary.recoveryPolicy(),
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
						run_mode,
						recovery_policy,
						log_path,
						last_seen_at
					) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
					runSummary.runMode(),
					runSummary.recoveryPolicy(),
					runSummary.logPath(),
					Timestamp.valueOf(LocalDateTime.now())
			);
		}
		upsertRunRecord(runSummary);
		upsertAttemptAndCheckpointRecords(runSummary);
		upsertStepAndArtifactRecords(runSummary);
		pruneOverflow();
	}

	private void upsertAttemptAndCheckpointRecords(RunSummaryView runSummary) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null) {
			return;
		}
		String runRecordId = resolveRunRecordId(jobExecutionId);
		if (runRecordId == null || runRecordId.isBlank()) {
			return;
		}

		// Keep S4c writes best-effort so optional control-plane persistence does not block run projection writes.
		try {
			upsertAttemptLinkRecord(runSummary, runRecordId);
			upsertCheckpointAnchorRecord(runSummary, runRecordId);
		} catch (DataAccessException ignored) {
			return;
		}

		backfillAttemptLinkPk();
		backfillCheckpointAnchorPk();
	}

	private void upsertAttemptLinkRecord(RunSummaryView runSummary, String runRecordId) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null || runRecordId == null || runRecordId.isBlank()) {
			return;
		}

		String priorRunRecordId = resolvePriorRunRecordId(runSummary, runRecordId);
		String linkKind = priorRunRecordId == null || priorRunRecordId.isBlank() ? "INITIAL" : "RERUN";

		jdbcTemplate.update("""
				insert into controlplane_attempt_link (
					attempt_link_pk,
					attempt_link_id,
					run_record_id,
					prior_run_record_id,
					link_kind,
					created_at
				) values (?, ?, ?, ?, ?, ?)
				on conflict(attempt_link_id) do update set
					run_record_id = excluded.run_record_id,
					prior_run_record_id = excluded.prior_run_record_id,
					link_kind = excluded.link_kind
				""",
				nextAttemptLinkPk(),
				"al-" + jobExecutionId,
				runRecordId,
				priorRunRecordId,
				linkKind,
				Timestamp.valueOf(LocalDateTime.now())
		);
	}

	private void upsertCheckpointAnchorRecord(RunSummaryView runSummary, String runRecordId) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null || runRecordId == null || runRecordId.isBlank()) {
			return;
		}

		String anchorRef = normalize(runSummary.logPath());
		if (anchorRef.isBlank()) {
			return;
		}

		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		jdbcTemplate.update("""
				insert into controlplane_checkpoint_anchor (
					checkpoint_anchor_pk,
					checkpoint_anchor_id,
					run_record_id,
					step_record_id,
					anchor_kind,
					anchor_ref,
					anchor_status,
					created_at,
					updated_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
				on conflict(checkpoint_anchor_id) do update set
					run_record_id = excluded.run_record_id,
					step_record_id = excluded.step_record_id,
					anchor_kind = excluded.anchor_kind,
					anchor_ref = excluded.anchor_ref,
					anchor_status = excluded.anchor_status,
					updated_at = excluded.updated_at
				""",
				nextCheckpointAnchorPk(),
				"ca-log-" + jobExecutionId,
				runRecordId,
				null,
				"RUN_LOG",
				anchorRef,
				normalize(runSummary.status()),
				now,
				now
		);
	}

	private String resolvePriorRunRecordId(RunSummaryView runSummary, String currentRunRecordId) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null) {
			return null;
		}
		String selectedJobKey = normalize(runSummary.scenario());
		if (selectedJobKey.isBlank()) {
			return null;
		}
		LocalDateTime startedAt = runSummary.startTime();
		if (startedAt == null) {
			startedAt = LocalDateTime.now();
		}
		Timestamp startedAtTs = Timestamp.valueOf(startedAt);

		return jdbcTemplate.query("""
				select run_record_id
				from controlplane_run_record
				where selected_job_key = ?
				  and run_record_id <> ?
				  and (started_at is null or started_at <= ?)
				order by case when started_at is null then 1 else 0 end,
				         started_at desc,
				         job_execution_id desc
				limit 1
				""",
				rs -> rs.next() ? rs.getString(1) : null,
				selectedJobKey,
				currentRunRecordId,
				startedAtTs
		);
	}

	@Override
	public List<RunSummaryView> latestRuns(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		return jdbcTemplate.query("""
				select job_execution_id, scenario, status, start_time, end_time, duration_seconds,
				       source_count, written_count, rejected_count, run_mode, recovery_policy, log_path
				from controlplane_run_summary
				order by case when start_time is null then 1 else 0 end,
				         start_time desc,
				         job_execution_id desc
				limit ?
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
				rs.getString("run_mode"),
				rs.getString("recovery_policy"),
				rs.getString("log_path")
		), limit);
	}

	@Override
	public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
		List<RunSummaryView> matches = jdbcTemplate.query("""
				select job_execution_id, scenario, status, start_time, end_time, duration_seconds,
				       source_count, written_count, rejected_count, run_mode, recovery_policy, log_path
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
				rs.getString("run_mode"),
				rs.getString("recovery_policy"),
				rs.getString("log_path")
		), jobExecutionId);
		return matches.stream().findFirst();
	}

	@Override
	public List<RunStepRecordView> listStepRecordsByJobExecutionId(long jobExecutionId, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		String runRecordId = resolveRunRecordId(jobExecutionId);
		if (runRecordId == null || runRecordId.isBlank()) {
			return List.of();
		}
		return jdbcTemplate.query("""
				select step_record_id, run_record_id, step_name, step_status,
				       started_at, finished_at, duration_seconds,
				       read_count, write_count, filter_count,
				       skip_count, rollback_count, rejected_count
				from controlplane_step_record
				where run_record_id = ?
				order by case when started_at is null then 1 else 0 end,
				         started_at asc,
				         step_record_id asc
				limit ?
				""", (rs, rowNum) -> new RunStepRecordView(
				rs.getString("step_record_id"),
				rs.getString("run_record_id"),
				rs.getString("step_name"),
				rs.getString("step_status"),
				toLocalDateTime(rs.getTimestamp("started_at")),
				toLocalDateTime(rs.getTimestamp("finished_at")),
				nullableLong(rs, "duration_seconds"),
				nullableLong(rs, "read_count"),
				nullableLong(rs, "write_count"),
				nullableLong(rs, "filter_count"),
				nullableLong(rs, "skip_count"),
				nullableLong(rs, "rollback_count"),
				nullableLong(rs, "rejected_count")
		), runRecordId, limit);
	}

	@Override
	public List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		String runRecordId = resolveRunRecordId(jobExecutionId);
		if (runRecordId == null || runRecordId.isBlank()) {
			return List.of();
		}
		return jdbcTemplate.query("""
				select artifact_record_id, run_record_id, step_record_id, artifact_role, artifact_path, created_at
				from controlplane_artifact_record
				where run_record_id = ?
				order by case when created_at is null then 1 else 0 end,
				         created_at desc,
				         artifact_record_id desc
				limit ?
				""", (rs, rowNum) -> new RunArtifactRecordView(
				rs.getString("artifact_record_id"),
				rs.getString("run_record_id"),
				rs.getString("step_record_id"),
				rs.getString("artifact_role"),
				rs.getString("artifact_path"),
				toLocalDateTime(rs.getTimestamp("created_at"))
		), runRecordId, limit);
	}

	@Override
	public List<RunArtifactRecordView> listArtifactRecordsByStepRecordId(String stepRecordId, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		String normalizedStepRecordId = normalize(stepRecordId);
		if (normalizedStepRecordId.isBlank()) {
			return List.of();
		}
		return jdbcTemplate.query("""
				select artifact_record_id, run_record_id, step_record_id, artifact_role, artifact_path, created_at
				from controlplane_artifact_record
				where step_record_id = ?
				order by case when created_at is null then 1 else 0 end,
				         created_at desc,
				         artifact_record_id desc
				limit ?
				""", (rs, rowNum) -> new RunArtifactRecordView(
				rs.getString("artifact_record_id"),
				rs.getString("run_record_id"),
				rs.getString("step_record_id"),
				rs.getString("artifact_role"),
				rs.getString("artifact_path"),
				toLocalDateTime(rs.getTimestamp("created_at"))
		), normalizedStepRecordId, limit);
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
			deleteRunProjectionGraph(id);
		}
	}

	private void deleteRunProjectionGraph(Long jobExecutionId) {
		if (jobExecutionId == null) {
			return;
		}
		jdbcTemplate.update("""
				delete from controlplane_checkpoint_anchor
				where run_record_id in (
					select run_record_id
					from controlplane_run_record
					where job_execution_id = ?
				)
				""", jobExecutionId);
		jdbcTemplate.update("""
				delete from controlplane_attempt_link
				where run_record_id in (
					select run_record_id
					from controlplane_run_record
					where job_execution_id = ?
				)
				""", jobExecutionId);
		jdbcTemplate.update("""
				delete from controlplane_artifact_record
				where run_record_id in (
					select run_record_id
					from controlplane_run_record
					where job_execution_id = ?
				)
				""", jobExecutionId);
		jdbcTemplate.update("""
				delete from controlplane_step_record
				where run_record_id in (
					select run_record_id
					from controlplane_run_record
					where job_execution_id = ?
				)
				""", jobExecutionId);
		jdbcTemplate.update("delete from controlplane_run_record where job_execution_id = ?", jobExecutionId);
		jdbcTemplate.update("delete from controlplane_run_summary where job_execution_id = ?", jobExecutionId);
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
					run_mode varchar(80),
					recovery_policy varchar(120),
					log_path varchar(2000),
					last_seen_at timestamp not null
				)
				""");
		ensureColumnExists("controlplane_run_summary", "run_mode", "varchar(80)");
		ensureColumnExists("controlplane_run_summary", "recovery_policy", "varchar(120)");
		jdbcTemplate.execute("""
				create index if not exists idx_run_summary_start_time
				on controlplane_run_summary (start_time, job_execution_id)
				""");
		jdbcTemplate.execute("""
				create table if not exists controlplane_run_record (
					run_record_pk bigint primary key,
					run_record_id varchar(80) not null unique,
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
					run_mode varchar(80),
					recovery_policy varchar(120),
					created_at timestamp not null,
					updated_at timestamp not null
				)
				""");
		migrateLegacyRunRecordPrimaryKeyIfRequired();
		ensureColumnExists("controlplane_run_record", "run_record_pk", "bigint");
		ensureColumnExists("controlplane_run_record", "trigger_event_pk", "bigint");
		ensureColumnExists("controlplane_run_record", "run_mode", "varchar(80)");
		ensureColumnExists("controlplane_run_record", "recovery_policy", "varchar(120)");
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
		jdbcTemplate.execute("""
				create table if not exists controlplane_step_record (
					step_record_pk bigint primary key,
					step_record_id varchar(80) not null unique,
					run_record_id varchar(80) not null,
					step_name varchar(200) not null,
					step_status varchar(50) not null,
					started_at timestamp,
					finished_at timestamp,
					duration_seconds bigint,
					read_count bigint,
					write_count bigint,
					filter_count bigint,
					skip_count bigint,
					rollback_count bigint,
					rejected_count bigint,
					created_at timestamp not null,
					updated_at timestamp not null
				)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_step_record_run
				on controlplane_step_record (run_record_id, started_at)
				""");
		jdbcTemplate.execute("""
				create unique index if not exists idx_step_record_id_run
				on controlplane_step_record (step_record_id, run_record_id)
				""");
		jdbcTemplate.execute("""
				create table if not exists controlplane_artifact_record (
					artifact_record_pk bigint primary key,
					artifact_record_id varchar(80) not null unique,
					run_record_id varchar(80) not null,
					step_record_id varchar(80),
					artifact_role varchar(80) not null,
					artifact_path varchar(2000),
					created_at timestamp not null
				)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_artifact_record_run
				on controlplane_artifact_record (run_record_id, created_at)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_artifact_record_step
				on controlplane_artifact_record (step_record_id, created_at)
				""");
		jdbcTemplate.execute("""
				create table if not exists controlplane_attempt_link (
					attempt_link_pk bigint primary key,
					attempt_link_id varchar(80) not null unique,
					run_record_id varchar(80) not null,
					prior_run_record_id varchar(80),
					link_kind varchar(50) not null,
					created_at timestamp not null
				)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_attempt_link_run
				on controlplane_attempt_link (run_record_id, created_at)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_attempt_link_prior
				on controlplane_attempt_link (prior_run_record_id, created_at)
				""");
		jdbcTemplate.execute("""
				create table if not exists controlplane_checkpoint_anchor (
					checkpoint_anchor_pk bigint primary key,
					checkpoint_anchor_id varchar(80) not null unique,
					run_record_id varchar(80) not null,
					step_record_id varchar(80),
					anchor_kind varchar(80) not null,
					anchor_ref varchar(2000),
					anchor_status varchar(50),
					created_at timestamp not null,
					updated_at timestamp not null
				)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_checkpoint_anchor_run
				on controlplane_checkpoint_anchor (run_record_id, created_at)
				""");
		jdbcTemplate.execute("""
				create index if not exists idx_checkpoint_anchor_step
				on controlplane_checkpoint_anchor (step_record_id, created_at)
				""");
		createArtifactOwnershipTriggers();
		backfillRunRecordFromRunSummary();
		backfillRunRecordTriggerEventPk();
		backfillRunRecordSelectedJobKey();
		backfillRunRecordTriggerEventLinkage();
		backfillRunLogArtifactsFromRunSummary();
		backfillStepRecordsFromBatchMetadata();
		backfillStepRecordsFromRunLogs();
	}

	private void upsertStepAndArtifactRecords(RunSummaryView runSummary) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null) {
			return;
		}
		String runRecordId = resolveRunRecordId(jobExecutionId);
		if (runRecordId == null || runRecordId.isBlank()) {
			return;
		}
		upsertStepRecordsFromBatchMetadata(jobExecutionId, runRecordId);
		if (countStepRecordsByRunRecordId(runRecordId) == 0) {
			upsertStepRecordsFromStructuredLog(runSummary, runRecordId);
		}
		upsertRunLogArtifact(jobExecutionId, runRecordId, runSummary.logPath());
	}

	private long countStepRecordsByRunRecordId(String runRecordId) {
		if (runRecordId == null || runRecordId.isBlank()) {
			return 0L;
		}
		Long value = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_step_record where run_record_id = ?",
				Long.class,
				runRecordId
		);
		return value == null ? 0L : value;
	}

	private void upsertStepRecordsFromStructuredLog(RunSummaryView runSummary, String runRecordId) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null) {
			return;
		}
		String logPathValue = normalize(runSummary.logPath());
		if (logPathValue.isBlank()) {
			return;
		}
		Path logPath = Path.of(logPathValue);
		if (!Files.exists(logPath)) {
			return;
		}

		StructuredLogEventParser parser = new StructuredLogEventParser();
		Map<String, LogStepProjection> projections = new LinkedHashMap<>();
		try (var lines = Files.lines(logPath)) {
			lines.forEach(line -> parser.parse(line, logPath).ifPresent(event -> {
				if (!"STEP_EVENT".equals(event.recordType()) && !"SUBFLOW_SUMMARY".equals(event.recordType())) {
					return;
				}
				if (event.jobExecutionId() == null || !event.jobExecutionId().equals(jobExecutionId)) {
					return;
				}
				Map<String, String> fields = event.fields();
				if ("SUBFLOW_SUMMARY".equals(event.recordType())) {
					for (String stepNameFromSummary : parseStepNames(fields.get("stepNames"))) {
						String projectionKey = "name:" + stepNameFromSummary;
						LogStepProjection projection = projections.computeIfAbsent(
								projectionKey,
								ignored -> new LogStepProjection(null, stepNameFromSummary)
						);
						projection.stepName = stepNameFromSummary;
						projection.status = firstNonBlank(fields.get("status"), projection.status, "UNKNOWN");
					}
					return;
				}
				Long stepExecutionId = toLongSafe(fields.get("stepExecutionId"));
				String stepName = normalize(firstNonBlank(fields.get("stepName"), event.mdcStepName()));
				if (stepName.isBlank() && stepExecutionId == null) {
					return;
				}
				String projectionKey = stepExecutionId == null ? "name:" + stepName : "id:" + stepExecutionId;
				LogStepProjection projection = projections.computeIfAbsent(
						projectionKey,
						ignored -> new LogStepProjection(stepExecutionId, stepName)
				);
				projection.stepName = stepName.isBlank() ? projection.stepName : stepName;
				String eventType = normalize(event.event());
				if ("step_started".equalsIgnoreCase(eventType)) {
					projection.startedAt = projection.startedAt == null ? event.loggedAt() : projection.startedAt;
					projection.status = projection.status == null || projection.status.isBlank() ? "STARTED" : projection.status;
				} else if ("step_finished".equalsIgnoreCase(eventType)) {
					projection.finishedAt = projection.finishedAt == null ? event.loggedAt() : projection.finishedAt;
					projection.status = firstNonBlank(fields.get("status"), projection.status, "UNKNOWN");
					projection.readCount = toLongSafe(fields.get("readCount"));
					projection.writeCount = toLongSafe(fields.get("writeCount"));
					projection.filterCount = toLongSafe(fields.get("filterCount"));
					projection.skipCount = toLongSafe(fields.get("skipCount"));
					projection.rollbackCount = toLongSafe(fields.get("rollbackCount"));
					projection.rejectedCount = toLongSafe(fields.get("rejectedCount"));
					projection.rejectOutputPath = firstNonBlank(fields.get("rejectOutputPath"), projection.rejectOutputPath);
					projection.archivedSourcePath = firstNonBlank(fields.get("archivedSourcePath"), projection.archivedSourcePath);
				}
			}));
		} catch (IOException ignored) {
			return;
		}

		int sequence = 1;
		for (LogStepProjection projection : projections.values()) {
			String stepRecordId = projection.stepExecutionId == null
					? "sr-" + jobExecutionId + "-log-" + sequence
					: "sr-" + jobExecutionId + "-" + projection.stepExecutionId;
			Timestamp now = Timestamp.valueOf(LocalDateTime.now());
			jdbcTemplate.update("""
					insert into controlplane_step_record (
						step_record_pk,
						step_record_id,
						run_record_id,
						step_name,
						step_status,
						started_at,
						finished_at,
						duration_seconds,
						read_count,
						write_count,
						filter_count,
						skip_count,
						rollback_count,
						rejected_count,
						created_at,
						updated_at
					) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					on conflict(step_record_id) do update set
						run_record_id = excluded.run_record_id,
						step_name = excluded.step_name,
						step_status = excluded.step_status,
						started_at = excluded.started_at,
						finished_at = excluded.finished_at,
						duration_seconds = excluded.duration_seconds,
						read_count = excluded.read_count,
						write_count = excluded.write_count,
						filter_count = excluded.filter_count,
						skip_count = excluded.skip_count,
						rollback_count = excluded.rollback_count,
						rejected_count = excluded.rejected_count,
						updated_at = excluded.updated_at
					""",
					nextStepRecordPk(),
					stepRecordId,
					runRecordId,
					normalize(projection.stepName),
					normalize(firstNonBlank(projection.status, "UNKNOWN")),
					toTimestamp(projection.startedAt),
					toTimestamp(projection.finishedAt),
					calculateDurationSeconds(projection.startedAt, projection.finishedAt),
					projection.readCount,
					projection.writeCount,
					projection.filterCount,
					projection.skipCount,
					projection.rollbackCount,
					projection.rejectedCount,
					now,
					now
			);
			upsertStepArtifact(runRecordId, stepRecordId, "STEP_REJECT_OUTPUT", "ar-step-reject-" + stepRecordId, projection.rejectOutputPath);
			upsertStepArtifact(runRecordId, stepRecordId, "STEP_ARCHIVED_SOURCE", "ar-step-archive-" + stepRecordId, projection.archivedSourcePath);
			sequence++;
		}
		backfillStepRecordPk();
		backfillArtifactRecordPk();
	}

	private Long toLongSafe(String value) {
		String normalized = normalize(value);
		if (normalized.isBlank() || "n/a".equalsIgnoreCase(normalized) || "unknown".equalsIgnoreCase(normalized)) {
			return null;
		}
		try {
			return Long.parseLong(normalized);
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private String firstNonBlank(String... values) {
		if (values == null) {
			return "";
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return "";
	}

	private List<String> parseStepNames(String rawStepNames) {
		String normalized = normalize(rawStepNames);
		if (normalized.isBlank() || "none".equalsIgnoreCase(normalized)) {
			return List.of();
		}
		return java.util.Arrays.stream(normalized.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.toList();
	}

	private void backfillRunLogArtifactsFromRunSummary() {
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		jdbcTemplate.update("""
				insert into controlplane_artifact_record (
					artifact_record_pk,
					artifact_record_id,
					run_record_id,
					step_record_id,
					artifact_role,
					artifact_path,
					created_at
				)
				select
					null,
					'ar-log-' || cast(rs.job_execution_id as text),
					rr.run_record_id,
					null,
					'RUN_LOG',
					rs.log_path,
					?
				from controlplane_run_summary rs
				join controlplane_run_record rr on rr.job_execution_id = rs.job_execution_id
				where rs.log_path is not null
				  and trim(rs.log_path) <> ''
				  and not exists (
					select 1
					from controlplane_artifact_record ar
					where ar.artifact_record_id = 'ar-log-' || cast(rs.job_execution_id as text)
				  )
				""", now);
		backfillArtifactRecordPk();
	}

	private void backfillStepRecordsFromBatchMetadata() {
		List<Long> jobExecutionIds;
		try {
			jobExecutionIds = jdbcTemplate.queryForList(
					"select job_execution_id from controlplane_run_record",
					Long.class
			);
		} catch (DataAccessException ignored) {
			return;
		}
		for (Long jobExecutionId : jobExecutionIds) {
			if (jobExecutionId == null) {
				continue;
			}
			String runRecordId = resolveRunRecordId(jobExecutionId);
			if (runRecordId == null || runRecordId.isBlank()) {
				continue;
			}
			upsertStepRecordsFromBatchMetadata(jobExecutionId, runRecordId);
		}
	}

	private void backfillStepRecordsFromRunLogs() {
		List<RunSummaryView> runSummaries;
		try {
			runSummaries = jdbcTemplate.query("""
					select rs.job_execution_id,
					       rs.scenario,
					       rs.status,
					       rs.start_time,
					       rs.end_time,
					       rs.duration_seconds,
					       rs.source_count,
					       rs.written_count,
					       rs.rejected_count,
					       rs.run_mode,
					       rs.recovery_policy,
					       rs.log_path
					from controlplane_run_summary rs
					order by rs.job_execution_id desc
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
					rs.getString("run_mode"),
					rs.getString("recovery_policy"),
					rs.getString("log_path")
			));
		} catch (DataAccessException ignored) {
			return;
		}
		for (RunSummaryView runSummary : runSummaries) {
			Long jobExecutionId = runSummary.jobExecutionId();
			if (jobExecutionId == null) {
				continue;
			}
			String runRecordId = resolveRunRecordId(jobExecutionId);
			if (runRecordId == null || runRecordId.isBlank()) {
				continue;
			}
			if (countStepRecordsByRunRecordId(runRecordId) > 0) {
				continue;
			}
			upsertStepRecordsFromStructuredLog(runSummary, runRecordId);
		}
	}

	private void upsertStepRecordsFromBatchMetadata(Long jobExecutionId, String runRecordId) {
		if (jobExecutionId == null || runRecordId == null || runRecordId.isBlank()) {
			return;
		}
		try {
			List<BatchStepProjection> steps = jdbcTemplate.query("""
					select
						step_execution_id,
						step_name,
						status,
						start_time,
						end_time,
						read_count,
						write_count,
						filter_count,
						rollback_count
					from batch_step_execution
					where job_execution_id = ?
					order by step_execution_id
					""", (rs, rowNum) -> new BatchStepProjection(
					rs.getLong("step_execution_id"),
					rs.getString("step_name"),
					rs.getString("status"),
					toLocalDateTime(rs.getTimestamp("start_time")),
					toLocalDateTime(rs.getTimestamp("end_time")),
					nullableLong(rs, "read_count"),
					nullableLong(rs, "write_count"),
					nullableLong(rs, "filter_count"),
					nullableLong(rs, "rollback_count")
			), jobExecutionId);
			for (BatchStepProjection step : steps) {
				String stepRecordId = "sr-" + jobExecutionId + "-" + step.stepExecutionId();
				Timestamp now = Timestamp.valueOf(LocalDateTime.now());
				jdbcTemplate.update("""
						insert into controlplane_step_record (
							step_record_pk,
							step_record_id,
							run_record_id,
							step_name,
							step_status,
							started_at,
							finished_at,
							duration_seconds,
							read_count,
							write_count,
							filter_count,
							skip_count,
							rollback_count,
							rejected_count,
							created_at,
							updated_at
						) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						on conflict(step_record_id) do update set
							run_record_id = excluded.run_record_id,
							step_name = excluded.step_name,
							step_status = excluded.step_status,
							started_at = excluded.started_at,
							finished_at = excluded.finished_at,
							duration_seconds = excluded.duration_seconds,
							read_count = excluded.read_count,
							write_count = excluded.write_count,
							filter_count = excluded.filter_count,
							rollback_count = excluded.rollback_count,
							updated_at = excluded.updated_at
						""",
						nextStepRecordPk(),
						stepRecordId,
						runRecordId,
						normalize(step.stepName()),
						normalize(step.status()),
						toTimestamp(step.startTime()),
						toTimestamp(step.endTime()),
						calculateDurationSeconds(step.startTime(), step.endTime()),
						step.readCount(),
						step.writeCount(),
						step.filterCount(),
						null,
						step.rollbackCount(),
						null,
						now,
						now
				);
				upsertStepArtifactsFromExecutionContext(step.stepExecutionId(), runRecordId, stepRecordId);
			}
			backfillStepRecordPk();
		} catch (DataAccessException ignored) {
			// Keep run-summary persistence available when batch step metadata is absent.
		}
	}

	private void upsertStepArtifactsFromExecutionContext(Long stepExecutionId, String runRecordId, String stepRecordId) {
		if (stepExecutionId == null || runRecordId == null || runRecordId.isBlank() || stepRecordId == null || stepRecordId.isBlank()) {
			return;
		}
		try {
			String shortContext = jdbcTemplate.query(
					"select short_context from batch_step_execution_context where step_execution_id = ?",
					rs -> rs.next() ? rs.getString(1) : null,
					stepExecutionId
			);
			if (shortContext == null || shortContext.isBlank()) {
				return;
			}
			String rejectOutputPath = extractContextValue(shortContext, "rejectOutputPath");
			String archivedSourcePath = extractContextValue(shortContext, "archivedSourcePath");
			upsertStepArtifact(runRecordId, stepRecordId, "STEP_REJECT_OUTPUT", "ar-step-reject-" + stepRecordId, rejectOutputPath);
			upsertStepArtifact(runRecordId, stepRecordId, "STEP_ARCHIVED_SOURCE", "ar-step-archive-" + stepRecordId, archivedSourcePath);
			backfillArtifactRecordPk();
		} catch (DataAccessException ignored) {
			// Keep run-summary persistence available when step execution context metadata is absent.
		}
	}

	private void upsertStepArtifact(String runRecordId,
	                               String stepRecordId,
	                               String artifactRole,
	                               String artifactRecordId,
	                               String artifactPath) {
		String normalizedPath = normalize(artifactPath);
		if (normalizedPath.isBlank()) {
			return;
		}
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		jdbcTemplate.update("""
				insert into controlplane_artifact_record (
					artifact_record_pk,
					artifact_record_id,
					run_record_id,
					step_record_id,
					artifact_role,
					artifact_path,
					created_at
				) values (?, ?, ?, ?, ?, ?, ?)
				on conflict(artifact_record_id) do update set
					run_record_id = excluded.run_record_id,
					step_record_id = excluded.step_record_id,
					artifact_role = excluded.artifact_role,
					artifact_path = excluded.artifact_path
				""",
				nextArtifactRecordPk(),
				artifactRecordId,
				runRecordId,
				stepRecordId,
				artifactRole,
				normalizedPath,
				now
		);
	}

	private String extractContextValue(String context, String key) {
		if (context == null || context.isBlank() || key == null || key.isBlank()) {
			return "";
		}
		try {
			JsonNode root = CONTEXT_OBJECT_MAPPER.readTree(context);
			JsonNode node = root.path(key);
			if (node.isMissingNode() || node.isNull()) {
				return "";
			}
			return node.isTextual() ? node.asText() : String.valueOf(node);
		} catch (Exception ignored) {
			// Keep projection writes available when step context payloads are truncated or malformed.
			return "";
		}
	}

	private void upsertRunLogArtifact(Long jobExecutionId, String runRecordId, String logPath) {
		if (jobExecutionId == null || runRecordId == null || runRecordId.isBlank()) {
			return;
		}
		String normalizedLogPath = normalize(logPath);
		if (normalizedLogPath.isBlank()) {
			return;
		}
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		jdbcTemplate.update("""
				insert into controlplane_artifact_record (
					artifact_record_pk,
					artifact_record_id,
					run_record_id,
					step_record_id,
					artifact_role,
					artifact_path,
					created_at
				) values (?, ?, ?, ?, ?, ?, ?)
				on conflict(artifact_record_id) do update set
					run_record_id = excluded.run_record_id,
					artifact_path = excluded.artifact_path
				""",
				nextArtifactRecordPk(),
				"ar-log-" + jobExecutionId,
				runRecordId,
				null,
				"RUN_LOG",
				normalizedLogPath,
				now
		);
		backfillArtifactRecordPk();
	}

	private void backfillStepRecordPk() {
		jdbcTemplate.update("""
				update controlplane_step_record
				set step_record_pk = rowid
				where step_record_pk is null
				""");
	}

	private void backfillArtifactRecordPk() {
		jdbcTemplate.update("""
				update controlplane_artifact_record
				set artifact_record_pk = rowid
				where artifact_record_pk is null
				""");
	}

	private Long nextStepRecordPk() {
		Long value = jdbcTemplate.queryForObject(
				"select coalesce(max(step_record_pk), 0) + 1 from controlplane_step_record",
				Long.class
		);
		return value == null ? 1L : value;
	}

	private Long nextArtifactRecordPk() {
		Long value = jdbcTemplate.queryForObject(
				"select coalesce(max(artifact_record_pk), 0) + 1 from controlplane_artifact_record",
				Long.class
		);
		return value == null ? 1L : value;
	}

	private void backfillAttemptLinkPk() {
		jdbcTemplate.update("""
				update controlplane_attempt_link
				set attempt_link_pk = rowid
				where attempt_link_pk is null
				""");
	}

	private Long nextAttemptLinkPk() {
		Long value = jdbcTemplate.queryForObject(
				"select coalesce(max(attempt_link_pk), 0) + 1 from controlplane_attempt_link",
				Long.class
		);
		return value == null ? 1L : value;
	}

	private void backfillCheckpointAnchorPk() {
		jdbcTemplate.update("""
				update controlplane_checkpoint_anchor
				set checkpoint_anchor_pk = rowid
				where checkpoint_anchor_pk is null
				""");
	}

	private Long nextCheckpointAnchorPk() {
		Long value = jdbcTemplate.queryForObject(
				"select coalesce(max(checkpoint_anchor_pk), 0) + 1 from controlplane_checkpoint_anchor",
				Long.class
		);
		return value == null ? 1L : value;
	}

	private String resolveRunRecordId(Long jobExecutionId) {
		if (jobExecutionId == null) {
			return null;
		}
		return jdbcTemplate.query(
				"select run_record_id from controlplane_run_record where job_execution_id = ?",
				rs -> rs.next() ? rs.getString(1) : null,
				jobExecutionId
		);
	}

	private Long calculateDurationSeconds(LocalDateTime startedAt, LocalDateTime finishedAt) {
		if (startedAt == null || finishedAt == null) {
			return null;
		}
		long seconds = java.time.Duration.between(startedAt, finishedAt).getSeconds();
		return Math.max(0L, seconds);
	}

	private void createArtifactOwnershipTriggers() {
		jdbcTemplate.execute("""
				create trigger if not exists trg_artifact_record_insert_step_lineage
				before insert on controlplane_artifact_record
				when new.step_record_id is not null
				 and not exists (
					select 1
					from controlplane_step_record sr
					where sr.step_record_id = new.step_record_id
					  and sr.run_record_id = new.run_record_id
				 )
				begin
					select raise(abort, 'artifact step lineage mismatch');
				end
				""");
		jdbcTemplate.execute("""
				create trigger if not exists trg_artifact_record_update_step_lineage
				before update of run_record_id, step_record_id on controlplane_artifact_record
				when new.step_record_id is not null
				 and not exists (
					select 1
					from controlplane_step_record sr
					where sr.step_record_id = new.step_record_id
					  and sr.run_record_id = new.run_record_id
				 )
				begin
					select raise(abort, 'artifact step lineage mismatch');
				end
				""");
	}

	private void migrateLegacyRunRecordPrimaryKeyIfRequired() {
		List<String> primaryKeyColumns = jdbcTemplate.query(
				"select lower(name) from pragma_table_info('controlplane_run_record') where pk > 0 order by pk",
				(rs, rowNum) -> rs.getString(1)
		);
		if (primaryKeyColumns.size() == 1 && "run_record_pk".equals(primaryKeyColumns.get(0))) {
			return;
		}

		jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
			boolean originalAutoCommit = connection.getAutoCommit();
			connection.setAutoCommit(false);
			try (java.sql.Statement statement = connection.createStatement()) {
				statement.execute("""
						create table controlplane_run_record_new (
							run_record_pk bigint primary key,
							run_record_id varchar(80) not null unique,
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
							run_mode varchar(80),
							recovery_policy varchar(120),
							created_at timestamp not null,
							updated_at timestamp not null
						)
						""");
				statement.execute("""
						insert into controlplane_run_record_new (
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
							run_mode,
							recovery_policy,
							created_at,
							updated_at
						)
						select
							coalesce(run_record_pk, rowid),
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
							null,
							null,
							created_at,
							updated_at
						from controlplane_run_record
						""");
				statement.execute("drop table controlplane_run_record");
				statement.execute("alter table controlplane_run_record_new rename to controlplane_run_record");
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
					run_mode,
					recovery_policy,
					created_at,
					updated_at
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
					run_mode = excluded.run_mode,
					recovery_policy = excluded.recovery_policy,
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
				runSummary.runMode(),
				runSummary.recoveryPolicy(),
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
					run_mode,
					recovery_policy,
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
					rs.run_mode,
					rs.recovery_policy,
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

	private record BatchStepProjection(
			long stepExecutionId,
			String stepName,
			String status,
			LocalDateTime startTime,
			LocalDateTime endTime,
			Long readCount,
			Long writeCount,
			Long filterCount,
			Long rollbackCount
	) {
	}

	private static final class LogStepProjection {
		private final Long stepExecutionId;
		private String stepName;
		private String status;
		private LocalDateTime startedAt;
		private LocalDateTime finishedAt;
		private Long readCount;
		private Long writeCount;
		private Long filterCount;
		private Long skipCount;
		private Long rollbackCount;
		private Long rejectedCount;
		private String rejectOutputPath;
		private String archivedSourcePath;

		private LogStepProjection(Long stepExecutionId, String stepName) {
			this.stepExecutionId = stepExecutionId;
			this.stepName = stepName;
		}
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

