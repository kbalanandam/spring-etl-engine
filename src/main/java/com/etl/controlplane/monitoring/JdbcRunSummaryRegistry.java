package com.etl.controlplane.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.batch.core.StepExecution;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * JDBC-backed registry for durable run-summary projections.
 */
@Repository
@ConditionalOnProperty(name = "controlplane.runs.persistence.mode", havingValue = "jdbc")
public class JdbcRunSummaryRegistry implements RunSummaryRegistry {
	private static final Logger logger = LoggerFactory.getLogger(JdbcRunSummaryRegistry.class);
	private static final java.time.Duration TRIGGER_LOOKBACK_WINDOW = java.time.Duration.ofMinutes(30);
	private static final java.time.Duration TRIGGER_LOOKAHEAD_WINDOW = java.time.Duration.ofMinutes(5);
	private static final java.time.Duration STEP_WINDOW_TOLERANCE = java.time.Duration.ofMinutes(5);
	private static final ObjectMapper CONTEXT_OBJECT_MAPPER = new ObjectMapper();

	private final JdbcTemplate jdbcTemplate;
	private final int retention;
	private final Map<String, Boolean> optionalColumnPresence = new HashMap<>();
	private final boolean sqlServerDialect;
	private final String auditActor;

	@Autowired
	public JdbcRunSummaryRegistry(JdbcTemplate jdbcTemplate,
	                              @Value("${controlplane.runs.retention:5000}") int retention,
	                              @Value("${controlplane.db.vendor:mysql}") String dbVendor,
	                              @Value("${spring.application.name:spring-etl-engine}") String applicationName) {
		this.jdbcTemplate = jdbcTemplate;
		this.retention = Math.max(1, retention);
		this.sqlServerDialect = isSqlServerVendor(dbVendor);
		this.auditActor = resolveAuditActor(applicationName);
		runStartupPhase("registry_startup_initialize_schema", this::initializeSchema);
	}

	JdbcRunSummaryRegistry(JdbcTemplate jdbcTemplate, int retention) {
		this(jdbcTemplate, retention, "mysql", "spring-etl-engine");
	}

	@Override
	public void upsert(RunSummaryView runSummary) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null) {
			return;
		}
		RunRecordRef runRecord = upsertRunRecord(runSummary);
		if (runRecord == null || runRecord.isEmpty()) {
			return;
		}
		upsertRunSummaryRecord(runSummary, runRecord);
		upsertAttemptAndCheckpointRecords(runSummary, runRecord);
		upsertStepAndArtifactRecords(runSummary, runRecord);
		pruneOverflow();
	}

	private void upsertRunSummaryRecord(RunSummaryView runSummary, RunRecordRef runRecord) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null) {
			return;
		}
		Long runRecordPk = runRecord == null ? null : runRecord.runRecordPk();
		Long runSummaryPkForUpdate = resolveRunSummaryPk(runRecordPk, jobExecutionId);
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		int updated = jdbcTemplate.update("""
				update controlplane_run_summary
				set run_summary_pk = coalesce(run_summary_pk, ?),
				    run_record_pk = coalesce(run_record_pk, ?),
				    scenario = ?, status = ?, start_time = ?, end_time = ?, duration_seconds = ?,
				    source_count = ?, written_count = ?, rejected_count = ?, run_mode = ?, recovery_policy = ?, log_path = ?, last_seen_at = ?, updated_at = ?, updated_by = ?
				where (? is not null and run_record_pk = ?)
				   or (run_record_pk is null and job_execution_id = ?)
				""",
				runSummaryPkForUpdate,
				runRecordPk,
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
				now,
				now,
				auditActor,
				runRecordPk,
				runRecordPk,
				jobExecutionId
		);
		if (updated == 0) {
			long allocatedRunSummaryPk = nextRunSummaryPk();
			try {
				jdbcTemplate.update("""
						insert into controlplane_run_summary (
							run_summary_pk,
							run_record_pk,
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
							last_seen_at,
							updated_at,
							created_by,
							updated_by
						) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""",
						allocatedRunSummaryPk,
						runRecordPk,
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
						now,
						now,
						auditActor,
						auditActor
				);
			} catch (DuplicateKeyException ignored) {
				Long retryRunSummaryPk = resolveRunSummaryPk(runRecordPk, jobExecutionId);
				jdbcTemplate.update("""
						update controlplane_run_summary
						set run_summary_pk = coalesce(run_summary_pk, ?),
						    run_record_pk = coalesce(run_record_pk, ?),
						    scenario = ?, status = ?, start_time = ?, end_time = ?, duration_seconds = ?,
						    source_count = ?, written_count = ?, rejected_count = ?, run_mode = ?, recovery_policy = ?, log_path = ?, last_seen_at = ?, updated_at = ?, updated_by = ?
						where (? is not null and run_record_pk = ?)
						   or (run_record_pk is null and job_execution_id = ?)
						""",
						retryRunSummaryPk,
						runRecordPk,
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
						now,
						now,
						auditActor,
						runRecordPk,
						runRecordPk,
						jobExecutionId
				);
			}
		}
	}

	private void upsertAttemptAndCheckpointRecords(RunSummaryView runSummary, RunRecordRef runRecord) {
		if (runRecord == null || runRecord.isEmpty()) {
			return;
		}

		// Keep S4c writes best-effort so optional control-plane persistence does not block run projection writes.
		try {
			upsertAttemptLinkRecord(runSummary, runRecord);
			upsertCheckpointAnchorRecord(runSummary, runRecord);
		} catch (DataAccessException ignored) {
			return;
		}
	}

	private void upsertAttemptLinkRecord(RunSummaryView runSummary, RunRecordRef runRecord) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null || runRecord == null || runRecord.isEmpty()) {
			return;
		}

		RunRecordRef priorRunRecord = resolvePriorRunRecord(runSummary, runRecord);
		String linkKind = priorRunRecord.isEmpty() ? "INITIAL" : "RERUN";
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		boolean legacyRunRecordIdColumn = hasOptionalColumn("controlplane_attempt_link", "run_record_id");
		boolean legacyPriorRunRecordIdColumn = hasOptionalColumn("controlplane_attempt_link", "prior_run_record_id");

		String attemptLinkId = "al-" + runRecord.runRecordPk();
		List<Object> updateParams = new ArrayList<>();
		StringBuilder updateSql = new StringBuilder("update controlplane_attempt_link set run_record_pk = ?");
		updateParams.add(runRecord.runRecordPk());
		if (legacyRunRecordIdColumn) {
			updateSql.append(", run_record_id = ?");
			updateParams.add(runRecord.runRecordId());
		}
		updateSql.append(", prior_run_record_pk = ?");
		updateParams.add(priorRunRecord.runRecordPk());
		if (legacyPriorRunRecordIdColumn) {
			updateSql.append(", prior_run_record_id = ?");
			updateParams.add(priorRunRecord.runRecordId());
		}
		updateSql.append(", link_kind = ?, created_at = ?, created_by = ?, updated_at = ?, updated_by = ? where attempt_link_id = ?");
		updateParams.add(linkKind);
		updateParams.add(now);
		updateParams.add(auditActor);
		updateParams.add(now);
		updateParams.add(auditActor);
		updateParams.add(attemptLinkId);
		int updated = jdbcTemplate.update(updateSql.toString(), updateParams.toArray());
		if (updated > 0) {
			return;
		}
		try {
			List<Object> insertParams = new ArrayList<>();
			StringBuilder insertSql = new StringBuilder("insert into controlplane_attempt_link (attempt_link_pk, attempt_link_id, run_record_pk");
			insertParams.add(nextAttemptLinkPk());
			insertParams.add(attemptLinkId);
			insertParams.add(runRecord.runRecordPk());
			if (legacyRunRecordIdColumn) {
				insertSql.append(", run_record_id");
				insertParams.add(runRecord.runRecordId());
			}
			insertSql.append(", prior_run_record_pk");
			insertParams.add(priorRunRecord.runRecordPk());
			if (legacyPriorRunRecordIdColumn) {
				insertSql.append(", prior_run_record_id");
				insertParams.add(priorRunRecord.runRecordId());
			}
			insertSql.append(", link_kind, created_at, updated_at, created_by, updated_by) values (?, ?, ?");
			if (legacyRunRecordIdColumn) {
				insertSql.append(", ?");
			}
			insertSql.append(", ?");
			if (legacyPriorRunRecordIdColumn) {
				insertSql.append(", ?");
			}
			insertSql.append(", ?, ?, ?, ?, ?)");
			insertParams.add(linkKind);
			insertParams.add(now);
			insertParams.add(now);
			insertParams.add(auditActor);
			insertParams.add(auditActor);
			jdbcTemplate.update(insertSql.toString(), insertParams.toArray());
		} catch (DuplicateKeyException ignored) {
			jdbcTemplate.update(updateSql.toString(), updateParams.toArray());
		}
	}

	private void upsertCheckpointAnchorRecord(RunSummaryView runSummary, RunRecordRef runRecord) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null || runRecord == null || runRecord.isEmpty()) {
			return;
		}

		String anchorRef = normalize(runSummary.logPath());
		if (anchorRef.isBlank()) {
			return;
		}

		String checkpointAnchorId = "ca-log-" + runRecord.runRecordPk();
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		boolean legacyRunRecordIdColumn = hasOptionalColumn("controlplane_checkpoint_anchor", "run_record_id");
		List<Object> updateParams = new ArrayList<>();
		StringBuilder updateSql = new StringBuilder("update controlplane_checkpoint_anchor set run_record_pk = ?");
		updateParams.add(runRecord.runRecordPk());
		if (legacyRunRecordIdColumn) {
			updateSql.append(", run_record_id = ?");
			updateParams.add(runRecord.runRecordId());
		}
		updateSql.append(", step_record_pk = ?, step_record_id = ?, anchor_kind = ?, anchor_ref = ?, anchor_status = ?, created_at = ?, created_by = ?, updated_at = ?, updated_by = ? where checkpoint_anchor_id = ?");
		updateParams.add(null);
		updateParams.add(null);
		updateParams.add("RUN_LOG");
		updateParams.add(anchorRef);
		updateParams.add(normalize(runSummary.status()));
		updateParams.add(now);
		updateParams.add(auditActor);
		updateParams.add(now);
		updateParams.add(auditActor);
		updateParams.add(checkpointAnchorId);
		int updated = jdbcTemplate.update(updateSql.toString(), updateParams.toArray());
		if (updated > 0) {
			return;
		}
		try {
			List<Object> insertParams = new ArrayList<>();
			StringBuilder insertSql = new StringBuilder("insert into controlplane_checkpoint_anchor (checkpoint_anchor_pk, checkpoint_anchor_id, run_record_pk");
			insertParams.add(nextCheckpointAnchorPk());
			insertParams.add(checkpointAnchorId);
			insertParams.add(runRecord.runRecordPk());
			if (legacyRunRecordIdColumn) {
				insertSql.append(", run_record_id");
				insertParams.add(runRecord.runRecordId());
			}
			insertSql.append(", step_record_pk, step_record_id, anchor_kind, anchor_ref, anchor_status, created_at, updated_at, created_by, updated_by) values (?, ?, ?");
			if (legacyRunRecordIdColumn) {
				insertSql.append(", ?");
			}
			insertSql.append(", ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			insertParams.add(null);
			insertParams.add(null);
			insertParams.add("RUN_LOG");
			insertParams.add(anchorRef);
			insertParams.add(normalize(runSummary.status()));
			insertParams.add(now);
			insertParams.add(now);
			insertParams.add(auditActor);
			insertParams.add(auditActor);
			jdbcTemplate.update(insertSql.toString(), insertParams.toArray());
		} catch (DuplicateKeyException ignored) {
			jdbcTemplate.update(updateSql.toString(), updateParams.toArray());
		}
	}

	private RunRecordRef resolvePriorRunRecord(RunSummaryView runSummary, RunRecordRef currentRunRecord) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null) {
			return RunRecordRef.empty();
		}
		String selectedJobKey = normalize(runSummary.scenario());
		if (selectedJobKey.isBlank()) {
			return RunRecordRef.empty();
		}
		LocalDateTime startedAt = runSummary.startTime();
		if (startedAt == null) {
			startedAt = LocalDateTime.now();
		}
		Timestamp startedAtTs = Timestamp.valueOf(startedAt);

		String priorRunSql = """
				select run_record_pk, run_record_id
				from controlplane_run_record
				where selected_job_key = ?
				  and run_record_pk <> ?
				  and (started_at is null or started_at <= ?)
				order by case when started_at is null then 1 else 0 end,
				         started_at desc,
				         job_execution_id desc
				""" + (sqlServerDialect ? " offset 0 rows fetch next 1 rows only" : " limit 1");

		return jdbcTemplate.query(priorRunSql,
				rs -> rs.next() ? new RunRecordRef(rs.getObject("run_record_pk", Long.class), rs.getString("run_record_id")) : RunRecordRef.empty(),
				selectedJobKey,
				currentRunRecord.runRecordPk(),
				startedAtTs
		);
	}

	@Override
	public List<RunSummaryView> latestRuns(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		if (!triggerEventTableAvailable()) {
			String latestRunsSql = """
					select job_execution_id, scenario, status, start_time, end_time, duration_seconds,
					       source_count, written_count, rejected_count, run_mode, recovery_policy, log_path
					from controlplane_run_summary
					order by case when start_time is null then 1 else 0 end,
					         start_time desc,
					         job_execution_id desc
					""" + firstRowsClause(limit);
			return jdbcTemplate.query(latestRunsSql, (rs, rowNum) -> new RunSummaryView(
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
					"MANUAL",
					rs.getString("log_path")
			));
		}
		String latestRunsWithTriggerSql = """
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
				       rs.log_path,
				       ts.source_code as trigger_source_code,
				       te.trigger_origin,
				       te.schedule_pk,
				       te.external_origin_key
				from controlplane_run_summary rs
				left join controlplane_run_record rr
				  on (rs.run_record_pk is not null and rr.run_record_pk = rs.run_record_pk)
				  or (rs.run_record_pk is null and rr.job_execution_id = rs.job_execution_id)
				left join controlplane_trigger_event te
				  on (rr.trigger_event_pk is not null and te.trigger_event_pk = rr.trigger_event_pk)
				  or (rr.trigger_event_pk is null and rr.trigger_event_id is not null and te.trigger_event_id = rr.trigger_event_id)
				left join controlplane_trigger_source ts on ts.trigger_source_pk = te.trigger_source_pk
				order by case when rs.start_time is null then 1 else 0 end,
				         rs.start_time desc,
				         rs.job_execution_id desc
				""" + firstRowsClause(limit);
		return jdbcTemplate.query(latestRunsWithTriggerSql, (rs, rowNum) -> new RunSummaryView(
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
				normalizeTriggerOriginToken(
					rs.getString("trigger_source_code"),
					rs.getString("trigger_origin"),
					rs.getObject("schedule_pk", Long.class),
					rs.getString("external_origin_key")
				),
				rs.getString("log_path")
		));
	}

	@Override
	public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
		if (!triggerEventTableAvailable()) {
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
					"MANUAL",
					rs.getString("log_path")
			), jobExecutionId);
			return matches.stream().findFirst();
		}
		List<RunSummaryView> matches = jdbcTemplate.query("""
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
				       rs.log_path,
				       ts.source_code as trigger_source_code,
				       te.trigger_origin,
				       te.schedule_pk,
				       te.external_origin_key
				from controlplane_run_summary rs
				left join controlplane_run_record rr
				  on (rs.run_record_pk is not null and rr.run_record_pk = rs.run_record_pk)
				  or (rs.run_record_pk is null and rr.job_execution_id = rs.job_execution_id)
				left join controlplane_trigger_event te
				  on (rr.trigger_event_pk is not null and te.trigger_event_pk = rr.trigger_event_pk)
				  or (rr.trigger_event_pk is null and rr.trigger_event_id is not null and te.trigger_event_id = rr.trigger_event_id)
				left join controlplane_trigger_source ts on ts.trigger_source_pk = te.trigger_source_pk
				where rs.job_execution_id = ?
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
				normalizeTriggerOriginToken(
					rs.getString("trigger_source_code"),
					rs.getString("trigger_origin"),
					rs.getObject("schedule_pk", Long.class),
					rs.getString("external_origin_key")
				),
				rs.getString("log_path")
		), jobExecutionId);
		return matches.stream().findFirst();
	}

	@Override
	public Optional<RunRecoveryView> findRecoveryByJobExecutionId(long jobExecutionId) {
		RunRecordRef runRecord = resolveRunRecordRef(jobExecutionId);
		if (runRecord.isEmpty()) {
			return Optional.empty();
		}
		List<RunCheckpointAnchorView> checkpointAnchors = listCheckpointAnchorsByRunRecordPk(runRecord.runRecordPk());

		String recoverySql = """
				select al.attempt_link_id,
				       al.link_kind,
				       rr_prior.run_record_id as prior_run_record_id,
				       rr_prior.job_execution_id as prior_job_execution_id
				from controlplane_attempt_link al
				left join controlplane_run_record rr_prior on rr_prior.run_record_pk = al.prior_run_record_pk
				where al.run_record_pk = ?
				order by al.created_at desc, al.attempt_link_pk desc
				""" + firstRowsClause(1);
		List<RunRecoveryView> matches = jdbcTemplate.query(recoverySql, (rs, rowNum) -> RunRecoveryView.advisoryResumeNotSupported(
				jobExecutionId,
				runRecord.runRecordId(),
				rs.getString("attempt_link_id"),
				rs.getString("link_kind"),
				rs.getString("prior_run_record_id"),
				nullableLong(rs, "prior_job_execution_id"),
				checkpointAnchors
		), runRecord.runRecordPk());

		if (!matches.isEmpty()) {
			return matches.stream().findFirst();
		}

		return Optional.of(RunRecoveryView.advisoryResumeNotSupported(
				jobExecutionId,
				runRecord.runRecordId(),
				null,
				null,
				null,
				null,
				checkpointAnchors
		));
	}

	@Override
	public List<RunStepRecordView> listStepRecordsByJobExecutionId(long jobExecutionId, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		RunRecordRef runRecord = resolveRunRecordRef(jobExecutionId);
		if (runRecord.isEmpty()) {
			return List.of();
		}
		String stepSql = """
				select sr.step_record_id, rr.run_record_id, sr.step_name, sr.step_status,
				       sr.started_at, sr.finished_at, sr.duration_seconds,
				       sr.read_count, sr.write_count, sr.filter_count,
				       sr.skip_count, sr.rollback_count, sr.rejected_count
				from controlplane_step_record sr
				join controlplane_run_record rr on rr.run_record_pk = sr.run_record_pk
				where sr.run_record_pk = ?
				order by case when sr.started_at is null then 1 else 0 end,
				         sr.started_at asc,
				         sr.step_record_id asc
				""" + firstRowsClause(limit);
		return jdbcTemplate.query(stepSql, (rs, rowNum) -> new RunStepRecordView(
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
		), runRecord.runRecordPk());
	}

	@Override
	public List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		RunRecordRef runRecord = resolveRunRecordRef(jobExecutionId);
		if (runRecord.isEmpty()) {
			return List.of();
		}
		String artifactByRunSql = """
				select ar.artifact_record_id, rr.run_record_id, ar.step_record_id, ar.artifact_role, ar.artifact_path, ar.created_at
				from controlplane_artifact_record ar
				join controlplane_run_record rr on rr.run_record_pk = ar.run_record_pk
				where ar.run_record_pk = ?
				order by case when ar.created_at is null then 1 else 0 end,
				         ar.created_at desc,
				         ar.artifact_record_id desc
				""" + firstRowsClause(limit);
		return jdbcTemplate.query(artifactByRunSql, (rs, rowNum) -> new RunArtifactRecordView(
				rs.getString("artifact_record_id"),
				rs.getString("run_record_id"),
				rs.getString("step_record_id"),
				rs.getString("artifact_role"),
				rs.getString("artifact_path"),
				toLocalDateTime(rs.getTimestamp("created_at"))
		), runRecord.runRecordPk());
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
		String artifactByStepSql = """
				select ar.artifact_record_id, rr.run_record_id, ar.step_record_id, ar.artifact_role, ar.artifact_path, ar.created_at
				from controlplane_artifact_record ar
				join controlplane_run_record rr on rr.run_record_pk = ar.run_record_pk
				where ar.step_record_id = ?
				order by case when ar.created_at is null then 1 else 0 end,
				         ar.created_at desc,
				         ar.artifact_record_id desc
				""" + firstRowsClause(limit);
		return jdbcTemplate.query(artifactByStepSql, (rs, rowNum) -> new RunArtifactRecordView(
				rs.getString("artifact_record_id"),
				rs.getString("run_record_id"),
				rs.getString("step_record_id"),
				rs.getString("artifact_role"),
				rs.getString("artifact_path"),
				toLocalDateTime(rs.getTimestamp("created_at"))
		), normalizedStepRecordId);
	}

	@Override
	public void upsertStepSnapshots(long jobExecutionId, java.util.Collection<? extends StepExecution> stepExecutions) {
		if (jobExecutionId <= 0L || stepExecutions == null || stepExecutions.isEmpty()) {
			return;
		}
		RunRecordRef runRecord = resolveRunRecordRef(jobExecutionId);
		if (runRecord.isEmpty()) {
			return;
		}
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		stepExecutions.stream()
				.filter(java.util.Objects::nonNull)
				.sorted(java.util.Comparator.comparing(StepExecution::getId, java.util.Comparator.nullsLast(Long::compareTo)))
				.forEach(stepExecution -> {
					String stepRecordId = toStepRecordId(runRecord, jobExecutionId, stepExecution);
					if (stepRecordId.isBlank()) {
						return;
					}
					upsertStepRecord(
							stepRecordId,
							runRecord,
							normalize(stepExecution.getStepName()),
							stepExecution.getStatus() == null ? "UNKNOWN" : normalize(stepExecution.getStatus().toString()),
							toTimestamp(stepExecution.getStartTime()),
							toTimestamp(stepExecution.getEndTime()),
							calculateDurationSeconds(stepExecution.getStartTime(), stepExecution.getEndTime()),
							(long) stepExecution.getReadCount(),
							(long) stepExecution.getWriteCount(),
							(long) stepExecution.getFilterCount(),
							(Long) null,
							(long) stepExecution.getRollbackCount(),
							(Long) null,
							now
					);
					if (stepExecution.getId() != null) {
						upsertStepArtifactsFromExecutionContext(stepExecution.getId(), runRecord, stepRecordId);
					}
				});
	}

	@Override
	public Optional<LogReadCheckpoint> findLogCheckpoint(String logPath) {
		String normalizedLogPath = normalize(logPath);
		if (normalizedLogPath.isBlank()) {
			return Optional.empty();
		}
		boolean hashedKeyShape = usesHashedLogCheckpointKey();
		List<LogReadCheckpoint> checkpoints;
		if (hashedKeyShape) {
			String logPathKey = toLogPathKey(normalizedLogPath);
			checkpoints = jdbcTemplate.query("""
					select log_path, last_offset_bytes, file_size_at_checkpoint, file_mtime_at_checkpoint
					from controlplane_log_checkpoint
					where log_path_key = ?
					  and log_path = ?
					""", (rs, rowNum) -> new LogReadCheckpoint(
					rs.getString("log_path"),
					rs.getLong("last_offset_bytes"),
					rs.getLong("file_size_at_checkpoint"),
					rs.getLong("file_mtime_at_checkpoint")
			), logPathKey, normalizedLogPath);
		} else {
			checkpoints = jdbcTemplate.query("""
					select log_path, last_offset_bytes, file_size_at_checkpoint, file_mtime_at_checkpoint
					from controlplane_log_checkpoint
					where log_path = ?
					""", (rs, rowNum) -> new LogReadCheckpoint(
					rs.getString("log_path"),
					rs.getLong("last_offset_bytes"),
					rs.getLong("file_size_at_checkpoint"),
					rs.getLong("file_mtime_at_checkpoint")
			), normalizedLogPath);
		}
		logger.info("LOG_CHECKPOINT_SYNC event=checkpoint_read dbVendor={} checkpointKeyMode={} outcome={} logPath={}",
				dbVendorLabel(),
				hashedKeyShape ? "hashed" : "direct",
				checkpoints.isEmpty() ? "missing" : "found",
				normalizedLogPath);
		return checkpoints.stream().findFirst();
	}

	@Override
	public void upsertLogCheckpoint(String logPath, long offsetBytes, long fileSizeBytes, long fileLastModifiedMillis) {
		String normalizedLogPath = normalize(logPath);
		if (normalizedLogPath.isBlank()) {
			return;
		}
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		boolean hashedKeyShape = usesHashedLogCheckpointKey();
		String logPathKey = hashedKeyShape ? toLogPathKey(normalizedLogPath) : null;
		String outcome;
		int updated = hashedKeyShape
				? jdbcTemplate.update("""
						update controlplane_log_checkpoint
						set last_offset_bytes = ?,
						    file_size_at_checkpoint = ?,
						    file_mtime_at_checkpoint = ?,
						    updated_at = ?,
						    updated_by = ?
						where log_path_key = ?
						  and log_path = ?
						""",
					offsetBytes,
					fileSizeBytes,
					fileLastModifiedMillis,
					now,
					auditActor,
					logPathKey,
					normalizedLogPath)
				: jdbcTemplate.update("""
						update controlplane_log_checkpoint
						set last_offset_bytes = ?,
						    file_size_at_checkpoint = ?,
						    file_mtime_at_checkpoint = ?,
						    updated_at = ?,
						    updated_by = ?
						where log_path = ?
						""",
					offsetBytes,
					fileSizeBytes,
					fileLastModifiedMillis,
					now,
					auditActor,
					normalizedLogPath);
		if (updated > 0) {
			outcome = "updated";
			emitCheckpointUpsertEvidence(normalizedLogPath, hashedKeyShape, outcome, offsetBytes, fileSizeBytes, fileLastModifiedMillis);
			return;
		}
		try {
			if (hashedKeyShape) {
				jdbcTemplate.update("""
						insert into controlplane_log_checkpoint (
							log_path_key,
							log_path,
							last_offset_bytes,
							file_size_at_checkpoint,
							file_mtime_at_checkpoint,
							updated_at,
							created_by,
							updated_by
						) values (?, ?, ?, ?, ?, ?, ?, ?)
						""",
						logPathKey,
						normalizedLogPath,
						offsetBytes,
						fileSizeBytes,
						fileLastModifiedMillis,
						now,
						auditActor,
						auditActor);
			} else {
				jdbcTemplate.update("""
						insert into controlplane_log_checkpoint (
							log_path,
							last_offset_bytes,
							file_size_at_checkpoint,
							file_mtime_at_checkpoint,
							updated_at,
							created_by,
							updated_by
						) values (?, ?, ?, ?, ?, ?, ?)
						""",
						normalizedLogPath,
						offsetBytes,
						fileSizeBytes,
						fileLastModifiedMillis,
						now,
						auditActor,
						auditActor);
			}
			outcome = "inserted";
		} catch (DuplicateKeyException ignored) {
			if (hashedKeyShape) {
				jdbcTemplate.update("""
						update controlplane_log_checkpoint
						set last_offset_bytes = ?,
						    file_size_at_checkpoint = ?,
						    file_mtime_at_checkpoint = ?,
						    updated_at = ?,
						    updated_by = ?
						where log_path_key = ?
						  and log_path = ?
						""",
						offsetBytes,
						fileSizeBytes,
						fileLastModifiedMillis,
						now,
						auditActor,
						logPathKey,
						normalizedLogPath);
			} else {
				jdbcTemplate.update("""
						update controlplane_log_checkpoint
						set last_offset_bytes = ?,
						    file_size_at_checkpoint = ?,
						    file_mtime_at_checkpoint = ?,
						    updated_at = ?,
						    updated_by = ?
						where log_path = ?
						""",
						offsetBytes,
						fileSizeBytes,
						fileLastModifiedMillis,
						now,
						auditActor,
						normalizedLogPath);
			}
			outcome = "updated_after_conflict";
		}
		emitCheckpointUpsertEvidence(normalizedLogPath, hashedKeyShape, outcome, offsetBytes, fileSizeBytes, fileLastModifiedMillis);
	}

	private String toStepRecordId(RunRecordRef runRecord, long jobExecutionId, StepExecution stepExecution) {
		if (stepExecution == null) {
			return "";
		}
		String runIdentity = runRecord != null && runRecord.runRecordPk() != null
				? String.valueOf(runRecord.runRecordPk())
				: String.valueOf(jobExecutionId);
		if (stepExecution.getId() != null) {
			return "sr-" + runIdentity + "-" + stepExecution.getId();
		}
		String stepNameKey = normalizeStepNameKey(stepExecution.getStepName());
		if (stepNameKey.isBlank()) {
			return "";
		}
		return "sr-" + runIdentity + "-name-" + stepNameKey;
	}

	private List<RunCheckpointAnchorView> listCheckpointAnchorsByRunRecordPk(Long runRecordPk) {
		if (runRecordPk == null) {
			return List.of();
		}
		return jdbcTemplate.query("""
				select ca.checkpoint_anchor_id,
				       coalesce(ca.step_record_id, sr.step_record_id) as step_record_id,
				       ca.anchor_kind,
				       ca.anchor_ref,
				       ca.anchor_status,
				       ca.created_at,
				       ca.updated_at
				from controlplane_checkpoint_anchor ca
				left join controlplane_step_record sr on sr.step_record_pk = ca.step_record_pk
				where ca.run_record_pk = ?
				order by case when ca.created_at is null then 1 else 0 end,
				         ca.created_at desc,
				         ca.checkpoint_anchor_pk desc
				""", (rs, rowNum) -> new RunCheckpointAnchorView(
				rs.getString("checkpoint_anchor_id"),
				rs.getString("step_record_id"),
				rs.getString("anchor_kind"),
				rs.getString("anchor_ref"),
				rs.getString("anchor_status"),
				toLocalDateTime(rs.getTimestamp("created_at")),
				toLocalDateTime(rs.getTimestamp("updated_at"))
		), runRecordPk);
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
				update controlplane_attempt_link
				set prior_run_record_pk = null
				where prior_run_record_pk in (
					select run_record_pk
					from controlplane_run_record
					where job_execution_id = ?
				)
				""", jobExecutionId);
		jdbcTemplate.update("""
				delete from controlplane_checkpoint_anchor
				where run_record_pk in (
					select run_record_pk
					from controlplane_run_record
					where job_execution_id = ?
				)
				""", jobExecutionId);
		jdbcTemplate.update("""
				delete from controlplane_attempt_link
				where run_record_pk in (
					select run_record_pk
					from controlplane_run_record
					where job_execution_id = ?
				)
				""", jobExecutionId);
		jdbcTemplate.update("""
				delete from controlplane_artifact_record
				where run_record_pk in (
					select run_record_pk
					from controlplane_run_record
					where job_execution_id = ?
				)
				""", jobExecutionId);
		jdbcTemplate.update("""
				delete from controlplane_step_record
				where run_record_pk in (
					select run_record_pk
					from controlplane_run_record
					where job_execution_id = ?
				)
				""", jobExecutionId);
		jdbcTemplate.update("delete from controlplane_run_record where job_execution_id = ?", jobExecutionId);
		jdbcTemplate.update("delete from controlplane_run_summary where job_execution_id = ?", jobExecutionId);
	}

	private void initializeSchema() {
		ensurePkSequenceTable();
		createTableIfMissing("controlplane_run_summary", """
				create table controlplane_run_summary (
					run_summary_pk bigint primary key,
					run_record_pk bigint,
					job_execution_id bigint not null unique,
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
					last_seen_at timestamp not null,
					updated_at timestamp,
					created_by varchar(200),
					updated_by varchar(200)
				)
				""");
		ensureColumnExists("controlplane_run_summary", "run_summary_pk", "bigint");
		ensureColumnExists("controlplane_run_summary", "run_record_pk", "bigint");
		ensureColumnExists("controlplane_run_summary", "run_mode", "varchar(80)");
		ensureColumnExists("controlplane_run_summary", "recovery_policy", "varchar(120)");
		ensureColumnExists("controlplane_run_summary", "updated_at", "timestamp");
		ensureColumnExists("controlplane_run_summary", "created_by", "varchar(200)");
		ensureColumnExists("controlplane_run_summary", "updated_by", "varchar(200)");
		backfillRunSummaryPk();
		createIndexIfMissing("controlplane_run_summary", "idx_run_summary_start_time",
				"create index idx_run_summary_start_time on controlplane_run_summary (start_time, job_execution_id)");
		createIndexIfMissing("controlplane_run_summary", "idx_run_summary_pk",
				"create unique index idx_run_summary_pk on controlplane_run_summary (run_summary_pk)");
		createIndexIfMissing("controlplane_run_summary", "idx_run_summary_run_record_pk",
				"create index idx_run_summary_run_record_pk on controlplane_run_summary (run_record_pk)");
		createTableIfMissing("controlplane_run_record", """
				create table controlplane_run_record (
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
					updated_at timestamp not null,
					created_by varchar(200),
					updated_by varchar(200)
				)
				""");
		migrateLegacyRunRecordPrimaryKeyIfRequired();
		ensureColumnExists("controlplane_run_record", "run_record_pk", "bigint");
		ensureColumnExists("controlplane_run_record", "trigger_event_pk", "bigint");
		ensureColumnExists("controlplane_run_record", "run_mode", "varchar(80)");
		ensureColumnExists("controlplane_run_record", "recovery_policy", "varchar(120)");
		ensureColumnExists("controlplane_run_record", "created_by", "varchar(200)");
		ensureColumnExists("controlplane_run_record", "updated_by", "varchar(200)");
		backfillRunRecordPk();
		createIndexIfMissing("controlplane_run_record", "idx_run_record_started_at",
				"create index idx_run_record_started_at on controlplane_run_record (started_at, job_execution_id)");
		createIndexIfMissing("controlplane_run_record", "idx_run_record_pk",
				"create unique index idx_run_record_pk on controlplane_run_record (run_record_pk)");
		createIndexIfMissing("controlplane_run_record", "idx_run_record_selected_job",
				"create index idx_run_record_selected_job on controlplane_run_record (selected_job_key, started_at)");
		createIndexIfMissing("controlplane_run_record", "idx_run_record_trigger_event_pk",
				"create index idx_run_record_trigger_event_pk on controlplane_run_record (trigger_event_pk)");
		createIndexIfMissing("controlplane_run_record", "idx_run_record_trigger_event",
				"create index idx_run_record_trigger_event on controlplane_run_record (trigger_event_id)");
		createIndexIfMissing("controlplane_run_record", "idx_run_record_job_status_time",
				"create index idx_run_record_job_status_time on controlplane_run_record (selected_job_key, run_status, started_at)");
		createTableIfMissing("controlplane_step_record", """
				create table controlplane_step_record (
					step_record_pk bigint primary key,
					step_record_id varchar(80) not null unique,
					run_record_pk bigint not null,
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
					updated_at timestamp not null,
					created_by varchar(200),
					updated_by varchar(200)
				)
				""");
		ensureColumnExists("controlplane_step_record", "run_record_pk", "bigint");
		ensureColumnExists("controlplane_step_record", "created_by", "varchar(200)");
		ensureColumnExists("controlplane_step_record", "updated_by", "varchar(200)");
		createIndexIfMissing("controlplane_step_record", "idx_step_record_run_pk",
				"create index idx_step_record_run_pk on controlplane_step_record (run_record_pk, started_at)");
		createIndexIfMissing("controlplane_step_record", "idx_step_record_id_run_pk",
				"create unique index idx_step_record_id_run_pk on controlplane_step_record (step_record_id, run_record_pk)");
		createTableIfMissing("controlplane_artifact_record", """
				create table controlplane_artifact_record (
					artifact_record_pk bigint primary key,
					artifact_record_id varchar(80) not null unique,
					run_record_pk bigint not null,
					step_record_id varchar(80),
					artifact_role varchar(80) not null,
					artifact_path varchar(2000),
					created_at timestamp not null,
					updated_at timestamp,
					created_by varchar(200),
					updated_by varchar(200)
				)
				""");
		ensureColumnExists("controlplane_artifact_record", "run_record_pk", "bigint");
		ensureColumnExists("controlplane_artifact_record", "updated_at", "timestamp");
		ensureColumnExists("controlplane_artifact_record", "created_by", "varchar(200)");
		ensureColumnExists("controlplane_artifact_record", "updated_by", "varchar(200)");
		createIndexIfMissing("controlplane_artifact_record", "idx_artifact_record_run_pk",
				"create index idx_artifact_record_run_pk on controlplane_artifact_record (run_record_pk, created_at)");
		createIndexIfMissing("controlplane_artifact_record", "idx_artifact_record_step",
				"create index idx_artifact_record_step on controlplane_artifact_record (step_record_id, created_at)");
		createTableIfMissing("controlplane_attempt_link", """
				create table controlplane_attempt_link (
					attempt_link_pk bigint primary key,
					attempt_link_id varchar(80) not null unique,
					run_record_pk bigint not null,
					prior_run_record_pk bigint,
					link_kind varchar(50) not null,
					created_at timestamp not null,
					updated_at timestamp,
					created_by varchar(200),
					updated_by varchar(200)
				)
				""");
		ensureColumnExists("controlplane_attempt_link", "run_record_pk", "bigint");
		ensureColumnExists("controlplane_attempt_link", "prior_run_record_pk", "bigint");
		ensureColumnExists("controlplane_attempt_link", "updated_at", "timestamp");
		ensureColumnExists("controlplane_attempt_link", "created_by", "varchar(200)");
		ensureColumnExists("controlplane_attempt_link", "updated_by", "varchar(200)");
		createIndexIfMissing("controlplane_attempt_link", "idx_attempt_link_run_pk",
				"create index idx_attempt_link_run_pk on controlplane_attempt_link (run_record_pk, created_at)");
		createIndexIfMissing("controlplane_attempt_link", "idx_attempt_link_prior_pk",
				"create index idx_attempt_link_prior_pk on controlplane_attempt_link (prior_run_record_pk, created_at)");
		createTableIfMissing("controlplane_checkpoint_anchor", """
				create table controlplane_checkpoint_anchor (
					checkpoint_anchor_pk bigint primary key,
					checkpoint_anchor_id varchar(80) not null unique,
					run_record_pk bigint not null,
					step_record_pk bigint,
					step_record_id varchar(80),
					anchor_kind varchar(80) not null,
					anchor_ref varchar(2000),
					anchor_status varchar(50),
					created_at timestamp not null,
					updated_at timestamp not null,
					created_by varchar(200),
					updated_by varchar(200)
				)
				""");
		ensureColumnExists("controlplane_checkpoint_anchor", "run_record_pk", "bigint");
		ensureColumnExists("controlplane_checkpoint_anchor", "step_record_pk", "bigint");
		ensureColumnExists("controlplane_checkpoint_anchor", "created_by", "varchar(200)");
		ensureColumnExists("controlplane_checkpoint_anchor", "updated_by", "varchar(200)");
		createIndexIfMissing("controlplane_checkpoint_anchor", "idx_checkpoint_anchor_run_pk",
				"create index idx_checkpoint_anchor_run_pk on controlplane_checkpoint_anchor (run_record_pk, created_at)");
		createIndexIfMissing("controlplane_checkpoint_anchor", "idx_checkpoint_anchor_step_pk",
				"create index idx_checkpoint_anchor_step_pk on controlplane_checkpoint_anchor (step_record_pk, created_at)");
		createIndexIfMissing("controlplane_checkpoint_anchor", "idx_checkpoint_anchor_step_id",
				"create index idx_checkpoint_anchor_step_id on controlplane_checkpoint_anchor (step_record_id, created_at)");
		createTableIfMissing("controlplane_log_checkpoint", sqlServerDialect
				? """
						create table controlplane_log_checkpoint (
							log_path varchar(2000) primary key,
							last_offset_bytes bigint not null,
							file_size_at_checkpoint bigint not null,
							file_mtime_at_checkpoint bigint not null,
							updated_at timestamp not null,
							created_by varchar(200),
							updated_by varchar(200)
						)
						"""
				: """
						create table controlplane_log_checkpoint (
							log_path_key varchar(64) primary key,
							log_path varchar(2000) not null,
							last_offset_bytes bigint not null,
							file_size_at_checkpoint bigint not null,
							file_mtime_at_checkpoint bigint not null,
							updated_at timestamp not null,
							created_by varchar(200),
							updated_by varchar(200)
						)
						""");
		ensureColumnExists("controlplane_log_checkpoint", "last_offset_bytes", "bigint");
		ensureColumnExists("controlplane_log_checkpoint", "file_size_at_checkpoint", "bigint");
		ensureColumnExists("controlplane_log_checkpoint", "file_mtime_at_checkpoint", "bigint");
		ensureColumnExists("controlplane_log_checkpoint", "updated_at", "timestamp");
		ensureColumnExists("controlplane_log_checkpoint", "created_by", "varchar(200)");
		ensureColumnExists("controlplane_log_checkpoint", "updated_by", "varchar(200)");
		createIndexIfMissing("controlplane_log_checkpoint", "idx_log_checkpoint_updated_at",
				"create index idx_log_checkpoint_updated_at on controlplane_log_checkpoint (updated_at)");
		runStartupPhase("create_artifact_ownership_triggers", this::createArtifactOwnershipTriggers);
		runStartupPhase("backfill_run_record_from_run_summary", this::backfillRunRecordFromRunSummary);
		runStartupPhase("backfill_run_summary_run_record_pk", this::backfillRunSummaryRunRecordPk);
		runStartupPhase("backfill_run_record_trigger_event_id", this::backfillRunRecordTriggerEventId);
		runStartupPhase("backfill_run_record_trigger_event_pk", this::backfillRunRecordTriggerEventPk);
		runStartupPhase("backfill_run_record_selected_job_key", this::backfillRunRecordSelectedJobKey);
		runStartupPhase("backfill_run_record_trigger_event_linkage", this::backfillRunRecordTriggerEventLinkage);
		runStartupPhase("backfill_run_log_artifacts_from_run_summary", this::backfillRunLogArtifactsFromRunSummary);
		runStartupPhase("backfill_step_records_from_batch_metadata", this::backfillStepRecordsFromBatchMetadata);
		runStartupPhase("backfill_step_records_from_run_logs", this::backfillStepRecordsFromRunLogs);
		runStartupPhase("backfill_checkpoint_anchor_step_record_pk", this::backfillCheckpointAnchorStepRecordPk);
		emitLogCheckpointBootstrapEvidence();
	}

	private void runStartupPhase(String phase, Runnable action) {
		long startedAtNanos = System.nanoTime();
		logger.info("STARTUP_SCHEMA event=phase_started phase={} dbVendor={}", phase, dbVendorLabel());
		try {
			action.run();
			long durationMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
			logger.info("STARTUP_SCHEMA event=phase_finished phase={} dbVendor={} durationMs={}", phase, dbVendorLabel(), durationMs);
		} catch (RuntimeException | Error ex) {
			long durationMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
			String message = ex.getMessage() == null ? "" : ex.getMessage().trim();
			logger.error("STARTUP_SCHEMA event=phase_failed phase={} dbVendor={} durationMs={} errorType={} message={}",
					phase,
					dbVendorLabel(),
					durationMs,
					ex.getClass().getSimpleName(),
					message,
					ex);
			throw ex;
		}
	}

	private void emitLogCheckpointBootstrapEvidence() {
		boolean hashedKeyShape = usesHashedLogCheckpointKey();
		logger.info("STARTUP_SCHEMA event=log_checkpoint_ready dbVendor={} checkpointKeyMode={} table=controlplane_log_checkpoint",
				dbVendorLabel(),
				hashedKeyShape ? "hashed" : "direct");
	}

	private void emitCheckpointUpsertEvidence(String normalizedLogPath,
	                                         boolean hashedKeyShape,
	                                         String outcome,
	                                         long offsetBytes,
	                                         long fileSizeBytes,
	                                         long fileLastModifiedMillis) {
		logger.info("LOG_CHECKPOINT_SYNC event=checkpoint_upsert dbVendor={} checkpointKeyMode={} outcome={} offsetBytes={} fileSizeBytes={} fileLastModifiedMillis={} logPath={}",
				dbVendorLabel(),
				hashedKeyShape ? "hashed" : "direct",
				outcome,
				offsetBytes,
				fileSizeBytes,
				fileLastModifiedMillis,
				normalizedLogPath);
	}

	private void backfillCheckpointAnchorStepRecordPk() {
		try {
			jdbcTemplate.update("""
					update controlplane_checkpoint_anchor ca
					set step_record_pk = (
						select sr.step_record_pk
						from controlplane_step_record sr
						where sr.run_record_pk = ca.run_record_pk
						  and sr.step_record_id = ca.step_record_id
					)
					where ca.step_record_pk is null
					  and ca.step_record_id is not null
					  and trim(ca.step_record_id) <> ''
					""");
		} catch (DataAccessException ignored) {
			// Keep startup resilient when legacy schemas temporarily miss linkage columns.
		}
	}

	private void upsertStepAndArtifactRecords(RunSummaryView runSummary, RunRecordRef runRecord) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null || runRecord == null || runRecord.isEmpty()) {
			return;
		}
		upsertStepRecordsFromBatchMetadata(jobExecutionId, runRecord, runSummary.startTime(), runSummary.endTime());
		if (countStepRecordsByRunRecordPk(runRecord.runRecordPk()) == 0) {
			upsertStepRecordsFromStructuredLog(runSummary, runRecord);
		}
		pruneStepAndArtifactRecordsOutsideRunWindow(runRecord, runSummary.startTime(), runSummary.endTime());
		upsertRunLogArtifact(jobExecutionId, runRecord, runSummary.logPath());
		logDuplicateStepNameGroupsIfPresent(runRecord, jobExecutionId);
	}

	private void pruneStepAndArtifactRecordsOutsideRunWindow(RunRecordRef runRecord,
	                                                         LocalDateTime runStart,
	                                                         LocalDateTime runEnd) {
		if (runRecord == null || runRecord.isEmpty() || runRecord.runRecordPk() == null) {
			return;
		}
		if (runStart == null || runEnd == null) {
			return;
		}
		LocalDateTime lowerBound = runStart.minus(STEP_WINDOW_TOLERANCE);
		LocalDateTime upperBound = runEnd.plus(STEP_WINDOW_TOLERANCE);
		List<String> staleStepRecordIds;
		try {
			staleStepRecordIds = jdbcTemplate.query("""
					select step_record_id
					from controlplane_step_record
					where run_record_pk = ?
					  and coalesce(started_at, finished_at) is not null
					  and (coalesce(started_at, finished_at) < ? or coalesce(started_at, finished_at) > ?)
					""", (rs, rowNum) -> rs.getString("step_record_id"),
					runRecord.runRecordPk(),
					toTimestamp(lowerBound),
					toTimestamp(upperBound));
		} catch (DataAccessException ignored) {
			return;
		}
		for (String stepRecordId : staleStepRecordIds) {
			if (stepRecordId == null || stepRecordId.isBlank()) {
				continue;
			}
			jdbcTemplate.update(
					"delete from controlplane_artifact_record where run_record_pk = ? and step_record_id = ?",
					runRecord.runRecordPk(),
					stepRecordId);
			jdbcTemplate.update(
					"delete from controlplane_step_record where run_record_pk = ? and step_record_id = ?",
					runRecord.runRecordPk(),
					stepRecordId);
		}
	}

	private void logDuplicateStepNameGroupsIfPresent(RunRecordRef runRecord, Long jobExecutionId) {
		if (runRecord == null || runRecord.isEmpty()) {
			return;
		}
		Long duplicateStepNameGroupCount = jdbcTemplate.queryForObject("""
				select count(*)
				from (
					select lower(trim(step_name)) as step_name_key
					from controlplane_step_record
					where run_record_pk = ?
					  and trim(coalesce(step_name, '')) <> ''
					group by lower(trim(step_name))
					having count(*) > 1
				) duplicate_groups
				""", Long.class, runRecord.runRecordPk());
		if (duplicateStepNameGroupCount != null && duplicateStepNameGroupCount > 0) {
			logger.warn(
					"RUN_EVENT event=duplicate_step_groups_detected jobExecutionId={} runRecordId={} duplicateStepNameGroupCount={}",
					jobExecutionId,
					runRecord.runRecordId(),
					duplicateStepNameGroupCount
			);
		}
	}

	private long countStepRecordsByRunRecordPk(Long runRecordPk) {
		if (runRecordPk == null) {
			return 0L;
		}
		Long value = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_step_record where run_record_pk = ?",
				Long.class,
				runRecordPk
		);
		return value == null ? 0L : value;
	}

	private void upsertStepRecordsFromStructuredLog(RunSummaryView runSummary, RunRecordRef runRecord) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null || runRecord == null || runRecord.isEmpty()) {
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
		Map<String, String> projectionKeyByStepName = new HashMap<>();
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
						String normalizedStepName = normalizeStepNameKey(stepNameFromSummary);
						String projectionKey = projectionKeyByStepName.getOrDefault(normalizedStepName, "name:" + normalizedStepName);
						LogStepProjection projection = projections.computeIfAbsent(
								projectionKey,
								ignored -> new LogStepProjection(null, stepNameFromSummary)
						);
						if (!normalizedStepName.isBlank()) {
							projectionKeyByStepName.putIfAbsent(normalizedStepName, projectionKey);
						}
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
				String normalizedStepName = normalizeStepNameKey(stepName);
				String projectionKey;
				if (stepExecutionId != null) {
					projectionKey = "id:" + stepExecutionId;
					LogStepProjection projection = projections.get(projectionKey);
					String existingNameKey = normalizedStepName.isBlank()
							? ""
							: projectionKeyByStepName.getOrDefault(normalizedStepName, "");
					if (projection == null && !existingNameKey.isBlank()) {
						projection = projections.remove(existingNameKey);
					}
					if (projection == null) {
						projection = new LogStepProjection(stepExecutionId, stepName);
					}
					projection.stepExecutionId = stepExecutionId;
					projections.put(projectionKey, projection);
					if (!normalizedStepName.isBlank()) {
						projectionKeyByStepName.put(normalizedStepName, projectionKey);
					}
				} else {
					projectionKey = projectionKeyByStepName.getOrDefault(normalizedStepName, "name:" + normalizedStepName);
					projections.computeIfAbsent(projectionKey, ignored -> new LogStepProjection(null, stepName));
					if (!normalizedStepName.isBlank()) {
						projectionKeyByStepName.putIfAbsent(normalizedStepName, projectionKey);
					}
				}
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
					? "sr-" + runRecord.runRecordPk() + "-log-" + sequence
					: "sr-" + runRecord.runRecordPk() + "-" + projection.stepExecutionId;
			Timestamp now = Timestamp.valueOf(LocalDateTime.now());
			upsertStepRecord(
					stepRecordId,
					runRecord,
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
					now
			);
			upsertStepArtifact(runRecord, stepRecordId, "STEP_REJECT_OUTPUT", "ar-step-reject-" + stepRecordId, projection.rejectOutputPath);
			upsertStepArtifact(runRecord, stepRecordId, "STEP_ARCHIVED_SOURCE", "ar-step-archive-" + stepRecordId, projection.archivedSourcePath);
			sequence++;
		}
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

	private String normalizeStepNameKey(String stepName) {
		String normalized = normalize(stepName);
		if (normalized.isBlank()) {
			return "";
		}
		return normalized.toLowerCase(Locale.ROOT);
	}

	private void backfillRunLogArtifactsFromRunSummary() {
		List<RunLogArtifactBackfillCandidate> candidates;
		try {
			candidates = jdbcTemplate.query("""
					select rs.job_execution_id, rr.run_record_pk, rr.run_record_id, rs.log_path
					from controlplane_run_summary rs
					join controlplane_run_record rr
					  on (rs.run_record_pk is not null and rr.run_record_pk = rs.run_record_pk)
					  or (rs.run_record_pk is null and rr.job_execution_id = rs.job_execution_id)
					where rs.log_path is not null
					  and trim(rs.log_path) <> ''
					""", (rs, rowNum) -> new RunLogArtifactBackfillCandidate(
					rs.getLong("job_execution_id"),
					rs.getObject("run_record_pk", Long.class),
					rs.getString("run_record_id"),
					rs.getString("log_path")
			));
		} catch (DataAccessException ignored) {
			return;
		}
		for (RunLogArtifactBackfillCandidate candidate : candidates) {
			upsertRunLogArtifact(candidate.jobExecutionId(), new RunRecordRef(candidate.runRecordPk(), candidate.runRecordId()), candidate.logPath());
		}
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
			RunRecordRef runRecord = resolveRunRecordRef(jobExecutionId);
			if (runRecord.isEmpty()) {
				continue;
			}
			upsertStepRecordsFromBatchMetadata(jobExecutionId, runRecord, null, null);
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
			RunRecordRef runRecord = resolveRunRecordRef(jobExecutionId);
			if (runRecord.isEmpty()) {
				continue;
			}
			if (countStepRecordsByRunRecordPk(runRecord.runRecordPk()) > 0) {
				continue;
			}
			upsertStepRecordsFromStructuredLog(runSummary, runRecord);
		}
	}

	private void upsertStepRecordsFromBatchMetadata(Long jobExecutionId,
	                                                RunRecordRef runRecord,
	                                                LocalDateTime runStart,
	                                                LocalDateTime runEnd) {
		if (jobExecutionId == null || runRecord == null || runRecord.isEmpty()) {
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
				if (!isStepWithinRunWindow(step.startTime(), step.endTime(), runStart, runEnd)) {
					continue;
				}
				String stepRecordId = "sr-" + runRecord.runRecordPk() + "-" + step.stepExecutionId();
				Timestamp now = Timestamp.valueOf(LocalDateTime.now());
				upsertStepRecord(
						stepRecordId,
						runRecord,
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
						now
				);
				upsertStepArtifactsFromExecutionContext(step.stepExecutionId(), runRecord, stepRecordId);
			}
		} catch (DataAccessException ignored) {
			// Keep run-summary persistence available when batch step metadata is absent.
		}
	}

	private boolean isStepWithinRunWindow(LocalDateTime stepStart,
	                                      LocalDateTime stepEnd,
	                                      LocalDateTime runStart,
	                                      LocalDateTime runEnd) {
		if (runStart == null || runEnd == null) {
			return true;
		}
		LocalDateTime lowerBound = runStart.minus(STEP_WINDOW_TOLERANCE);
		LocalDateTime upperBound = runEnd.plus(STEP_WINDOW_TOLERANCE);
		LocalDateTime effectivePoint = stepStart != null ? stepStart : stepEnd;
		if (effectivePoint == null) {
			return true;
		}
		return !(effectivePoint.isBefore(lowerBound) || effectivePoint.isAfter(upperBound));
	}

	private void upsertStepArtifactsFromExecutionContext(Long stepExecutionId, RunRecordRef runRecord, String stepRecordId) {
		if (stepExecutionId == null || runRecord == null || runRecord.isEmpty() || stepRecordId == null || stepRecordId.isBlank()) {
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
			upsertStepArtifact(runRecord, stepRecordId, "STEP_REJECT_OUTPUT", "ar-step-reject-" + stepRecordId, rejectOutputPath);
			upsertStepArtifact(runRecord, stepRecordId, "STEP_ARCHIVED_SOURCE", "ar-step-archive-" + stepRecordId, archivedSourcePath);
		} catch (DataAccessException ignored) {
			// Keep run-summary persistence available when step execution context metadata is absent.
		}
	}

	private void upsertStepArtifact(RunRecordRef runRecord,
	                               String stepRecordId,
	                               String artifactRole,
	                               String artifactRecordId,
	                               String artifactPath) {
		String normalizedPath = normalize(artifactPath);
		if (normalizedPath.isBlank()) {
			return;
		}
		upsertArtifactRecord(artifactRecordId, runRecord, stepRecordId, artifactRole, normalizedPath, Timestamp.valueOf(LocalDateTime.now()));
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

	private void upsertRunLogArtifact(Long jobExecutionId, RunRecordRef runRecord, String logPath) {
		if (jobExecutionId == null || runRecord == null || runRecord.isEmpty()) {
			return;
		}
		String normalizedLogPath = normalize(logPath);
		if (normalizedLogPath.isBlank()) {
			return;
		}
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		upsertArtifactRecord("ar-log-" + runRecord.runRecordPk(), runRecord, null, "RUN_LOG", normalizedLogPath, now);
	}

	private Long nextStepRecordPk() {
		return nextPk("controlplane_step_record_pk");
	}

	private Long nextArtifactRecordPk() {
		return nextPk("controlplane_artifact_record_pk");
	}

	private Long nextAttemptLinkPk() {
		return nextPk("controlplane_attempt_link_pk");
	}

	private Long nextCheckpointAnchorPk() {
		return nextPk("controlplane_checkpoint_anchor_pk");
	}

	private RunRecordRef resolveRunRecordRef(Long jobExecutionId) {
		if (jobExecutionId == null) {
			return RunRecordRef.empty();
		}
		return resolveMostRecentRunRecordByJobExecutionId(jobExecutionId);
	}

	private RunRecordRef resolveRunRecordRefForUpsert(RunSummaryView runSummary, TriggerEventLink resolvedTriggerEvent) {
		String explicitTriggerEventId = normalize(runSummary.triggerEventId());
		if (!explicitTriggerEventId.isBlank()) {
			RunRecordRef explicitMatch = resolveRunRecordRefByTriggerEventId(explicitTriggerEventId);
			if (!explicitMatch.isEmpty()) {
				return explicitMatch;
			}
			// Trigger identity is explicit for this run; avoid jobExecutionId-based reuse.
			return RunRecordRef.empty();
		}
		if (resolvedTriggerEvent != null && !resolvedTriggerEvent.isEmpty()) {
			RunRecordRef byPk = resolveRunRecordRefByTriggerEventPk(resolvedTriggerEvent.triggerEventPk());
			if (!byPk.isEmpty()) {
				return byPk;
			}
			RunRecordRef byId = resolveRunRecordRefByTriggerEventId(resolvedTriggerEvent.triggerEventId());
			if (!byId.isEmpty()) {
				return byId;
			}
			return RunRecordRef.empty();
		}
		return resolveMostRecentRunRecordByJobExecutionId(runSummary.jobExecutionId());
	}

	private RunRecordRef resolveRunRecordRefByTriggerEventId(String triggerEventId) {
		String normalizedTriggerEventId = normalize(triggerEventId);
		if (normalizedTriggerEventId.isBlank()) {
			return RunRecordRef.empty();
		}
		String sql = """
				select run_record_pk, run_record_id
				from controlplane_run_record
				where lower(trim(trigger_event_id)) = lower(trim(?))
				order by run_record_pk desc
				""" + firstRowsClause(1);
		return jdbcTemplate.query(
				sql,
				rs -> rs.next() ? new RunRecordRef(rs.getObject("run_record_pk", Long.class), rs.getString("run_record_id")) : RunRecordRef.empty(),
				normalizedTriggerEventId
		);
	}

	private RunRecordRef resolveRunRecordRefByTriggerEventPk(Long triggerEventPk) {
		if (triggerEventPk == null) {
			return RunRecordRef.empty();
		}
		String sql = """
				select run_record_pk, run_record_id
				from controlplane_run_record
				where trigger_event_pk = ?
				order by run_record_pk desc
				""" + firstRowsClause(1);
		return jdbcTemplate.query(
				sql,
				rs -> rs.next() ? new RunRecordRef(rs.getObject("run_record_pk", Long.class), rs.getString("run_record_id")) : RunRecordRef.empty(),
				triggerEventPk
		);
	}

	private RunRecordRef resolveMostRecentRunRecordByJobExecutionId(Long jobExecutionId) {
		if (jobExecutionId == null) {
			return RunRecordRef.empty();
		}
		String sql = """
				select run_record_pk, run_record_id
				from controlplane_run_record
				where job_execution_id = ?
				order by case when started_at is null then 1 else 0 end,
				         started_at desc,
				         run_record_pk desc
				""" + firstRowsClause(1);
		return jdbcTemplate.query(
				sql,
				rs -> rs.next() ? new RunRecordRef(rs.getObject("run_record_pk", Long.class), rs.getString("run_record_id")) : RunRecordRef.empty(),
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
		// SQLite-only trigger DDL removed.
	}

	private void migrateLegacyRunRecordPrimaryKeyIfRequired() {
		// No-op: legacy primary-key migration removed.
	}

	private RunRecordRef upsertRunRecord(RunSummaryView runSummary) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null) {
			return RunRecordRef.empty();
		}
		String selectedJobKey = normalize(runSummary.scenario());
		TriggerEventLink resolvedTriggerEvent = resolveCompleteTriggerEventLink(resolveTriggerEventLinkForUpsert(runSummary));
		RunRecordRef existingRunRecord = resolveRunRecordRefForUpsert(runSummary, resolvedTriggerEvent);
		boolean applyTriggerLink = !resolvedTriggerEvent.isEmpty();
		if (applyTriggerLink && shouldBlockTriggerLinkOverwrite(existingRunRecord, resolvedTriggerEvent)) {
			applyTriggerLink = false;
		}
		Long triggerEventPkForWrite = applyTriggerLink ? resolvedTriggerEvent.triggerEventPk() : null;
		String triggerEventIdForWrite = applyTriggerLink ? resolvedTriggerEvent.triggerEventId() : null;
		Long runRecordPkForUpdate = existingRunRecord.isEmpty() ? null : existingRunRecord.runRecordPk();
		String runRecordId = "rr-" + jobExecutionId;
		String resolvedSelectedJobKey = selectedJobKey.isBlank() ? null : selectedJobKey;
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		int updated = jdbcTemplate.update("""
				update controlplane_run_record
				set trigger_event_pk = case when ? = 1 then ? else trigger_event_pk end,
				    trigger_event_id = case when ? = 1 then ? else trigger_event_id end,
				    selected_job_key = coalesce(nullif(trim(?), ''), selected_job_key),
				    scenario = ?,
				    run_status = ?,
				    started_at = ?,
				    finished_at = ?,
				    duration_seconds = ?,
				    source_count = ?,
				    written_count = ?,
				    rejected_count = ?,
				    run_mode = ?,
				    recovery_policy = ?,
				    created_at = ?,
				    updated_at = ?,
				    updated_by = ?
				where ? is not null
				  and run_record_pk = ?
				""",
				applyTriggerLink ? 1 : 0,
				triggerEventPkForWrite,
				applyTriggerLink ? 1 : 0,
				triggerEventIdForWrite,
				resolvedSelectedJobKey,
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
				now,
				auditActor,
				runRecordPkForUpdate,
				runRecordPkForUpdate
		);
		if (updated == 0) {
			long allocatedRunRecordPk = nextRunRecordPk();
			try {
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
							updated_at,
							created_by,
							updated_by
						) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""",
						allocatedRunRecordPk,
						runRecordId,
						jobExecutionId,
						triggerEventPkForWrite,
						triggerEventIdForWrite,
						resolvedSelectedJobKey,
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
						now,
						auditActor,
						auditActor
				);
				return new RunRecordRef(allocatedRunRecordPk, runRecordId);
			} catch (DuplicateKeyException ignored) {
				RunRecordRef retryRunRecord = resolveMostRecentRunRecordByJobExecutionId(jobExecutionId);
				Long retryRunRecordPk = retryRunRecord.isEmpty() ? null : retryRunRecord.runRecordPk();
				boolean applyTriggerLinkOnRetry = applyTriggerLink;
				if (applyTriggerLinkOnRetry && shouldBlockTriggerLinkOverwrite(retryRunRecord, resolvedTriggerEvent)) {
					applyTriggerLinkOnRetry = false;
				}
				jdbcTemplate.update("""
						update controlplane_run_record
						set trigger_event_pk = case when ? = 1 then ? else trigger_event_pk end,
						    trigger_event_id = case when ? = 1 then ? else trigger_event_id end,
						    selected_job_key = coalesce(nullif(trim(?), ''), selected_job_key),
						    scenario = ?,
						    run_status = ?,
						    started_at = ?,
						    finished_at = ?,
						    duration_seconds = ?,
						    source_count = ?,
						    written_count = ?,
						    rejected_count = ?,
						    run_mode = ?,
						    recovery_policy = ?,
						    created_at = ?,
						    updated_at = ?,
						    updated_by = ?
						where ? is not null
						  and run_record_pk = ?
						""",
						applyTriggerLinkOnRetry ? 1 : 0,
						triggerEventPkForWrite,
						applyTriggerLinkOnRetry ? 1 : 0,
						triggerEventIdForWrite,
						resolvedSelectedJobKey,
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
						now,
						auditActor,
						retryRunRecordPk,
						retryRunRecordPk
				);
				if (!retryRunRecord.isEmpty()) {
					return retryRunRecord;
				}
			}
		}
		if (!existingRunRecord.isEmpty()) {
			return existingRunRecord;
		}
		return resolveMostRecentRunRecordByJobExecutionId(jobExecutionId);
		// Forward link in controlplane_run_record is the authoritative path.
		// Reverse trigger-event launched_run_* writes are reserved for legacy repair flows.
	}

	private boolean shouldBlockTriggerLinkOverwrite(RunRecordRef runRecord, TriggerEventLink candidate) {
		if (runRecord == null || runRecord.isEmpty() || candidate == null || candidate.isEmpty()) {
			return false;
		}
		TriggerEventLink existing = resolveRunRecordTriggerEventLink(runRecord.runRecordPk());
		if (existing == null || existing.isEmpty()) {
			return false;
		}
		TriggerEventLink existingComplete = resolveCompleteTriggerEventLink(existing);
		TriggerEventLink candidateComplete = resolveCompleteTriggerEventLink(candidate);
		if (existingComplete.isEmpty() || candidateComplete.isEmpty()) {
			return false;
		}
		boolean sameLink = sameTriggerEventLink(existingComplete, candidateComplete);
		if (sameLink) {
			return false;
		}
		logger.warn(
				"RUN_LINK_CONFLICT event=run_link_conflict runRecordPk={} existingTriggerEventId={} existingTriggerEventPk={} candidateTriggerEventId={} candidateTriggerEventPk={} action=keep_existing_link",
				runRecord.runRecordPk(),
				existingComplete.triggerEventId(),
				existingComplete.triggerEventPk(),
				candidateComplete.triggerEventId(),
				candidateComplete.triggerEventPk()
		);
		return true;
	}

	private TriggerEventLink resolveRunRecordTriggerEventLink(Long runRecordPk) {
		if (runRecordPk == null) {
			return TriggerEventLink.empty();
		}
		return Optional.ofNullable(jdbcTemplate.query(
				"select trigger_event_id, trigger_event_pk from controlplane_run_record where run_record_pk = ?",
				rs -> rs.next()
						? new TriggerEventLink(rs.getString("trigger_event_id"), nullableLong(rs, "trigger_event_pk"))
						: TriggerEventLink.empty(),
				runRecordPk
		)).orElse(TriggerEventLink.empty());
	}

	private boolean sameTriggerEventLink(TriggerEventLink first, TriggerEventLink second) {
		if (first == null || second == null || first.isEmpty() || second.isEmpty()) {
			return false;
		}
		String firstId = normalize(first.triggerEventId()).toLowerCase(Locale.ROOT);
		String secondId = normalize(second.triggerEventId()).toLowerCase(Locale.ROOT);
		if (firstId.isBlank() || secondId.isBlank() || !firstId.equals(secondId)) {
			return false;
		}
		return first.triggerEventPk() != null && first.triggerEventPk().equals(second.triggerEventPk());
	}

	private TriggerEventLink resolveCompleteTriggerEventLink(TriggerEventLink candidate) {
		if (candidate == null || candidate.isEmpty()) {
			return TriggerEventLink.empty();
		}
		boolean hasPk = candidate.triggerEventPk() != null;
		boolean hasId = !normalize(candidate.triggerEventId()).isBlank();
		if (hasPk && hasId) {
			return candidate;
		}
		if (hasId) {
			return resolveTriggerEventLinkById(candidate.triggerEventId());
		}
		if (hasPk) {
			return resolveTriggerEventLinkByPk(candidate.triggerEventPk());
		}
		return TriggerEventLink.empty();
	}

	private void backfillRunRecordFromRunSummary() {
		List<RunRecordBackfillCandidate> candidates = jdbcTemplate.query("""
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
				       rs.recovery_policy
				from controlplane_run_summary rs
				where rs.run_record_pk is null
				   or not exists (
					select 1
					from controlplane_run_record rr
					where rr.run_record_pk = rs.run_record_pk
				   )
				""", (rs, rowNum) -> new RunRecordBackfillCandidate(
				rs.getLong("job_execution_id"),
				rs.getString("scenario"),
				rs.getString("status"),
				toLocalDateTime(rs.getTimestamp("start_time")),
				toLocalDateTime(rs.getTimestamp("end_time")),
				nullableLong(rs, "duration_seconds"),
				nullableLong(rs, "source_count"),
				nullableLong(rs, "written_count"),
				nullableLong(rs, "rejected_count"),
				rs.getString("run_mode"),
				rs.getString("recovery_policy")
		));
		for (RunRecordBackfillCandidate candidate : candidates) {
			Timestamp now = Timestamp.valueOf(LocalDateTime.now());
			String selectedJobKey = normalize(candidate.scenario());
			try {
				long runRecordPk = nextRunRecordPk();
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
							updated_at,
							created_by,
							updated_by
						) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""",
						runRecordPk,
						"rr-" + candidate.jobExecutionId(),
						candidate.jobExecutionId(),
						null,
						null,
						selectedJobKey.isBlank() ? null : selectedJobKey,
						candidate.scenario(),
						candidate.status(),
						toTimestamp(candidate.startedAt()),
						toTimestamp(candidate.finishedAt()),
						candidate.durationSeconds(),
						candidate.sourceCount(),
						candidate.writtenCount(),
						candidate.rejectedCount(),
						candidate.runMode(),
						candidate.recoveryPolicy(),
						now,
						now,
						auditActor,
						auditActor
				);
			} catch (DuplicateKeyException ignored) {
				// Concurrent startup/upsert may populate this row; ignore and continue.
			}
		}
	}

	private void backfillRunSummaryPk() {
		List<Long> jobExecutionIds;
		try {
			jobExecutionIds = jdbcTemplate.queryForList(
					"select job_execution_id from controlplane_run_summary where run_summary_pk is null order by job_execution_id",
					Long.class
			);
		} catch (DataAccessException ignored) {
			return;
		}
		for (Long jobExecutionId : jobExecutionIds) {
			if (jobExecutionId == null) {
				continue;
			}
			try {
				jdbcTemplate.update(
						"update controlplane_run_summary set run_summary_pk = ? where job_execution_id = ? and run_summary_pk is null",
						nextRunSummaryPk(),
						jobExecutionId
				);
			} catch (DuplicateKeyException ignored) {
				// Concurrent startup may fill this surrogate first; ignore and continue.
			}
		}
	}

	private void backfillRunSummaryRunRecordPk() {
		try {
			jdbcTemplate.update("""
					update controlplane_run_summary
					set run_record_pk = (
						select rr.run_record_pk
						from controlplane_run_record rr
						where rr.job_execution_id = controlplane_run_summary.job_execution_id
					)
					where run_record_pk is null
					  and exists (
						select 1
						from controlplane_run_record rr
						where rr.job_execution_id = controlplane_run_summary.job_execution_id
					  )
					""");
		} catch (DataAccessException ignored) {
			// Keep startup resilient while legacy run-summary rows are bridged to run_record_pk.
		}
	}

	private void upsertStepRecord(String stepRecordId,
	                             RunRecordRef runRecord,
	                             String stepName,
	                             String stepStatus,
	                             Timestamp startedAt,
	                             Timestamp finishedAt,
	                             Long durationSeconds,
	                             Long readCount,
	                             Long writeCount,
	                             Long filterCount,
	                             Long skipCount,
	                             Long rollbackCount,
	                             Long rejectedCount,
	                             Timestamp now) {
		boolean legacyRunRecordIdColumn = hasOptionalColumn("controlplane_step_record", "run_record_id");
		List<Object> updateParams = new ArrayList<>();
		StringBuilder updateSql = new StringBuilder("update controlplane_step_record set run_record_pk = ?");
		updateParams.add(runRecord.runRecordPk());
		if (legacyRunRecordIdColumn) {
			updateSql.append(", run_record_id = ?");
			updateParams.add(runRecord.runRecordId());
		}
		updateSql.append(", step_name = ?, step_status = ?, started_at = ?, finished_at = ?, duration_seconds = ?, read_count = ?, write_count = ?, filter_count = ?, skip_count = ?, rollback_count = ?, rejected_count = ?, created_at = ?, updated_at = ?, updated_by = ? where step_record_id = ?");
		updateParams.add(stepName);
		updateParams.add(stepStatus);
		updateParams.add(startedAt);
		updateParams.add(finishedAt);
		updateParams.add(durationSeconds);
		updateParams.add(readCount);
		updateParams.add(writeCount);
		updateParams.add(filterCount);
		updateParams.add(skipCount);
		updateParams.add(rollbackCount);
		updateParams.add(rejectedCount);
		updateParams.add(now);
		updateParams.add(now);
		updateParams.add(auditActor);
		updateParams.add(stepRecordId);
		int updated = jdbcTemplate.update(updateSql.toString(), updateParams.toArray());
		if (updated > 0) {
			return;
		}
		try {
			List<Object> insertParams = new ArrayList<>();
			StringBuilder insertSql = new StringBuilder("insert into controlplane_step_record (step_record_pk, step_record_id, run_record_pk");
			insertParams.add(nextStepRecordPk());
			insertParams.add(stepRecordId);
			insertParams.add(runRecord.runRecordPk());
			if (legacyRunRecordIdColumn) {
				insertSql.append(", run_record_id");
				insertParams.add(runRecord.runRecordId());
			}
			insertSql.append(", step_name, step_status, started_at, finished_at, duration_seconds, read_count, write_count, filter_count, skip_count, rollback_count, rejected_count, created_at, updated_at, created_by, updated_by) values (?, ?, ?");
			if (legacyRunRecordIdColumn) {
				insertSql.append(", ?");
			}
			insertSql.append(", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
			insertParams.add(stepName);
			insertParams.add(stepStatus);
			insertParams.add(startedAt);
			insertParams.add(finishedAt);
			insertParams.add(durationSeconds);
			insertParams.add(readCount);
			insertParams.add(writeCount);
			insertParams.add(filterCount);
			insertParams.add(skipCount);
			insertParams.add(rollbackCount);
			insertParams.add(rejectedCount);
			insertParams.add(now);
			insertParams.add(now);
			insertParams.add(auditActor);
			insertParams.add(auditActor);
			jdbcTemplate.update(insertSql.toString(), insertParams.toArray());
		} catch (DuplicateKeyException ignored) {
			jdbcTemplate.update(updateSql.toString(), updateParams.toArray());
		}
	}

	private void upsertArtifactRecord(String artifactRecordId,
	                                 RunRecordRef runRecord,
	                                 String stepRecordId,
	                                 String artifactRole,
	                                 String artifactPath,
	                                 Timestamp now) {
		boolean legacyRunRecordIdColumn = hasOptionalColumn("controlplane_artifact_record", "run_record_id");
		List<Object> updateParams = new ArrayList<>();
		StringBuilder updateSql = new StringBuilder("update controlplane_artifact_record set run_record_pk = ?");
		updateParams.add(runRecord.runRecordPk());
		if (legacyRunRecordIdColumn) {
			updateSql.append(", run_record_id = ?");
			updateParams.add(runRecord.runRecordId());
		}
		updateSql.append(", step_record_id = ?, artifact_role = ?, artifact_path = ?, created_at = ?, updated_at = ?, updated_by = ? where artifact_record_id = ?");
		updateParams.add(stepRecordId);
		updateParams.add(artifactRole);
		updateParams.add(artifactPath);
		updateParams.add(now);
		updateParams.add(now);
		updateParams.add(auditActor);
		updateParams.add(artifactRecordId);
		int updated = jdbcTemplate.update(updateSql.toString(), updateParams.toArray());
		if (updated > 0) {
			return;
		}
		try {
			List<Object> insertParams = new ArrayList<>();
			StringBuilder insertSql = new StringBuilder("insert into controlplane_artifact_record (artifact_record_pk, artifact_record_id, run_record_pk");
			insertParams.add(nextArtifactRecordPk());
			insertParams.add(artifactRecordId);
			insertParams.add(runRecord.runRecordPk());
			if (legacyRunRecordIdColumn) {
				insertSql.append(", run_record_id");
				insertParams.add(runRecord.runRecordId());
			}
			insertSql.append(", step_record_id, artifact_role, artifact_path, created_at, updated_at, created_by, updated_by) values (?, ?, ?");
			if (legacyRunRecordIdColumn) {
				insertSql.append(", ?");
			}
			insertSql.append(", ?, ?, ?, ?, ?, ?, ?)");
			insertParams.add(stepRecordId);
			insertParams.add(artifactRole);
			insertParams.add(artifactPath);
			insertParams.add(now);
			insertParams.add(now);
			insertParams.add(auditActor);
			insertParams.add(auditActor);
			jdbcTemplate.update(insertSql.toString(), insertParams.toArray());
		} catch (DuplicateKeyException ignored) {
			jdbcTemplate.update(updateSql.toString(), updateParams.toArray());
		}
	}

	private void ensureColumnExists(String tableName, String columnName, String columnDefinition) {
		Boolean columnExists = columnExists(tableName, columnName);
		if (Boolean.FALSE.equals(columnExists)) {
			String addClause = sqlServerDialect ? " add " : " add column ";
			jdbcTemplate.execute("alter table " + tableName + addClause + columnName + " " + adaptDdlForDialect(columnDefinition));
		}
	}

	private Boolean columnExists(String tableName, String columnName) {
		return jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
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
	}

	private boolean hasOptionalColumn(String tableName, String columnName) {
		String cacheKey = tableName + "." + columnName;
		Boolean cached = optionalColumnPresence.get(cacheKey);
		if (cached != null) {
			return cached;
		}
		boolean exists = Boolean.TRUE.equals(columnExists(tableName, columnName));
		optionalColumnPresence.put(cacheKey, exists);
		return exists;
	}

	private void createIndexIfMissing(String tableName, String indexName, String createIndexSql) {
		Boolean exists = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
			try (java.sql.ResultSet indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, tableName, false, false)) {
				while (indexes.next()) {
					String existingIndexName = indexes.getString("INDEX_NAME");
					if (existingIndexName != null && indexName.equalsIgnoreCase(existingIndexName)) {
						return true;
					}
				}
				return false;
			}
		});
		if (!Boolean.TRUE.equals(exists)) {
			try {
				jdbcTemplate.execute(createIndexSql);
			} catch (DataAccessException exception) {
				if (!isExistingIndexCreationFailure(exception, indexName)) {
					throw exception;
				}
			}
		}
	}

	private boolean isExistingIndexCreationFailure(DataAccessException exception, String indexName) {
		Throwable current = exception;
		while (current != null) {
			if (current instanceof java.sql.SQLException sqlException) {
				String sqlState = sqlException.getSQLState();
				int errorCode = sqlException.getErrorCode();
				String message = sqlException.getMessage();
				if ("42S11".equals(sqlState)
						|| "42710".equals(sqlState)
						|| errorCode == 42111
						|| (message != null
						&& message.toLowerCase(Locale.ROOT).contains("already exists")
						&& message.toLowerCase(Locale.ROOT).contains(indexName.toLowerCase(Locale.ROOT)))) {
					return true;
				}
			}
			current = current.getCause();
		}
		return false;
	}

	private void createTableIfMissing(String tableName, String createTableSql) {
		Boolean exists = jdbcTemplate.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>) connection -> {
			java.sql.DatabaseMetaData metadata = connection.getMetaData();
			try (java.sql.ResultSet exact = metadata.getTables(connection.getCatalog(), null, tableName, new String[]{"TABLE"})) {
				if (exact.next()) {
					return true;
				}
			}
			try (java.sql.ResultSet scanned = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
				while (scanned.next()) {
					String existingTableName = scanned.getString("TABLE_NAME");
					if (existingTableName != null && tableName.equalsIgnoreCase(existingTableName)) {
						return true;
					}
				}
				return false;
			}
		});
		if (!Boolean.TRUE.equals(exists)) {
			jdbcTemplate.execute(adaptDdlForDialect(createTableSql));
		}
	}

	private String adaptDdlForDialect(String sql) {
		if (!sqlServerDialect) {
			return sql;
		}
		return sql
				.replaceAll("(?i)\\bboolean\\b", "bit")
				.replaceAll("(?i)\\btimestamp\\b", "datetime2");
	}

	private boolean usesHashedLogCheckpointKey() {
		return hasOptionalColumn("controlplane_log_checkpoint", "log_path_key");
	}

	private String toLogPathKey(String normalizedLogPath) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(normalizedLogPath.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(hash.length * 2);
			for (byte value : hash) {
				builder.append(String.format("%02x", value));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable for log checkpoint keying", ex);
		}
	}

	private String dbVendorLabel() {
		return sqlServerDialect ? "mssql" : "mysql";
	}

	private void backfillRunRecordPk() {
		// No-op: legacy row backfill removed.
	}

	private void backfillRunRecordTriggerEventPk() {
		try {
			Long conflictCount = jdbcTemplate.queryForObject("""
					select count(*)
					from controlplane_run_record
					where trigger_event_id is not null
					  and exists (
						select 1
						from controlplane_trigger_event te
						where te.trigger_event_id = controlplane_run_record.trigger_event_id
					  )
					  and trigger_event_pk is not null
					  and coalesce(trigger_event_pk, -1) <> coalesce((
						select te.trigger_event_pk
						from controlplane_trigger_event te
						where te.trigger_event_id = controlplane_run_record.trigger_event_id
					  ), -1)
					""", Long.class);
			if (conflictCount != null && conflictCount > 0) {
				logger.warn("RUN_LINK_CONFLICT event=startup_run_link_conflict count={} action=keep_existing_link", conflictCount);
			}
			jdbcTemplate.update("""
					update controlplane_run_record
					set trigger_event_pk = (
						select te.trigger_event_pk
						from controlplane_trigger_event te
						where te.trigger_event_id = controlplane_run_record.trigger_event_id
					)
					where trigger_event_id is not null
					  and exists (
						select 1
						from controlplane_trigger_event te
						where te.trigger_event_id = controlplane_run_record.trigger_event_id
					  )
					  and trigger_event_pk is null
					""");
		} catch (DataAccessException ignored) {
			// Keep run-summary persistence available when trigger table state is optional.
		}
	}

	private void backfillRunRecordTriggerEventId() {
		try {
			jdbcTemplate.update("""
					update controlplane_run_record
					set trigger_event_id = (
						select te.trigger_event_id
						from controlplane_trigger_event te
						where te.trigger_event_pk = controlplane_run_record.trigger_event_pk
					)
					where trigger_event_pk is not null
					  and exists (
						select 1
						from controlplane_trigger_event te
						where te.trigger_event_pk = controlplane_run_record.trigger_event_pk
					  )
					  and (trigger_event_id is null or trim(trigger_event_id) = '')
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
		}
	}

	private TriggerEventLink resolveTriggerEventLinkForUpsert(RunSummaryView runSummary) {
		return resolveTriggerEventLink(runSummary.jobExecutionId(), runSummary.scenario(), runSummary.startTime(), runSummary.triggerEventId(), false);
	}

	private TriggerEventLink resolveTriggerEventLinkForBackfill(Long jobExecutionId, String scenario, LocalDateTime startedAt) {
		return resolveTriggerEventLink(jobExecutionId, scenario, startedAt, null, true);
	}

	private TriggerEventLink resolveTriggerEventLink(Long jobExecutionId, String scenario, LocalDateTime startedAt, String triggerEventId, boolean preferExistingLaunchLink) {
		if (jobExecutionId == null) {
			return TriggerEventLink.empty();
		}
		try {
			TriggerEventLink explicitTriggerLink = resolveTriggerEventLinkById(triggerEventId);
			if (!explicitTriggerLink.isEmpty()) {
				return explicitTriggerLink;
			}
			if (preferExistingLaunchLink) {
				TriggerEventLink existingLink = resolveExistingLaunchLink(jobExecutionId, normalize(scenario));
				if (!existingLink.isEmpty()) {
					return existingLink;
				}
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
			Timestamp startedAtTs = Timestamp.valueOf(startedAt);
			String preferredPreStartSql = """
					select te.trigger_event_id, te.trigger_event_pk
					from controlplane_trigger_event te
					where te.job_key = ?
					  and te.decision_status = 'ACCEPTED'
					  and te.requested_at between ? and ?
					  and te.requested_at <= ?
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
					""" + firstRowsClause(1);
			TriggerEventLink preferredPreStart = Optional.ofNullable(jdbcTemplate.query(preferredPreStartSql, rs -> rs.next()
						? new TriggerEventLink(rs.getString("trigger_event_id"), nullableLong(rs, "trigger_event_pk"))
						: TriggerEventLink.empty(),
					normalizedScenario,
					lowerBound,
					upperBound,
					startedAtTs,
					jobExecutionId)).orElse(TriggerEventLink.empty());
			if (!preferredPreStart.isEmpty()) {
				return preferredPreStart;
			}

			String nearestFutureSql = """
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
					order by te.requested_at asc, te.trigger_event_pk asc, te.trigger_event_id asc
					""" + firstRowsClause(1);
			TriggerEventLink nearestFuture = Optional.ofNullable(jdbcTemplate.query(nearestFutureSql, rs -> rs.next()
						? new TriggerEventLink(rs.getString("trigger_event_id"), nullableLong(rs, "trigger_event_pk"))
						: TriggerEventLink.empty(),
					normalizedScenario,
					lowerBound,
					upperBound,
						jobExecutionId)).orElse(TriggerEventLink.empty());
			if (!nearestFuture.isEmpty()) {
				return nearestFuture;
			}
			return resolveExistingLaunchLink(jobExecutionId, normalizedScenario);
		} catch (DataAccessException ignored) {
			// Trigger table remains optional while run-summary persistence stays available.
			return TriggerEventLink.empty();
		}
	}

	private TriggerEventLink resolveTriggerEventLinkById(String triggerEventId) {
		String normalizedTriggerEventId = normalize(triggerEventId);
		if (normalizedTriggerEventId.isBlank()) {
			return TriggerEventLink.empty();
		}
		String sql = """
				select trigger_event_id, trigger_event_pk
				from controlplane_trigger_event
				where trigger_event_id = ?
				order by trigger_event_pk desc, trigger_event_id desc
				""" + firstRowsClause(1);
		return Optional.ofNullable(jdbcTemplate.query(sql,
				rs -> rs.next()
						? new TriggerEventLink(rs.getString("trigger_event_id"), nullableLong(rs, "trigger_event_pk"))
						: TriggerEventLink.empty(),
				normalizedTriggerEventId)).orElse(TriggerEventLink.empty());
	}

	private TriggerEventLink resolveTriggerEventLinkByPk(Long triggerEventPk) {
		if (triggerEventPk == null) {
			return TriggerEventLink.empty();
		}
		String sql = """
				select trigger_event_id, trigger_event_pk
				from controlplane_trigger_event
				where trigger_event_pk = ?
				""";
		return Optional.ofNullable(jdbcTemplate.query(sql,
				rs -> rs.next()
						? new TriggerEventLink(rs.getString("trigger_event_id"), nullableLong(rs, "trigger_event_pk"))
						: TriggerEventLink.empty(),
				triggerEventPk)).orElse(TriggerEventLink.empty());
	}

	private TriggerEventLink resolveExistingLaunchLink(Long jobExecutionId, String normalizedScenario) {
		Long runRecordPk = resolveRunRecordPk(jobExecutionId);
		String scenarioFilter = normalize(normalizedScenario);
		boolean hasScenarioFilter = !scenarioFilter.isBlank();
		if (runRecordPk != null) {
			String existingByPkSql = """
					select trigger_event_id, trigger_event_pk
					from controlplane_trigger_event
					where launched_run_pk = ?
					  and (? = 0 or lower(trim(job_key)) = ?)
					order by requested_at desc, trigger_event_pk desc, trigger_event_id desc
					""" + firstRowsClause(1);
			TriggerEventLink pkMatch = Optional.ofNullable(jdbcTemplate.query(existingByPkSql, rs -> rs.next()
						? new TriggerEventLink(rs.getString("trigger_event_id"), nullableLong(rs, "trigger_event_pk"))
						: TriggerEventLink.empty(), runRecordPk, hasScenarioFilter ? 1 : 0, scenarioFilter)).orElse(TriggerEventLink.empty());
			if (!pkMatch.isEmpty()) {
				return pkMatch;
			}
		}
		String existingByIdSql = """
				select trigger_event_id, trigger_event_pk
				from controlplane_trigger_event
				where launched_run_id = ?
				  and (? = 0 or lower(trim(job_key)) = ?)
				order by requested_at desc, trigger_event_pk desc, trigger_event_id desc
				""" + firstRowsClause(1);
		return Optional.ofNullable(jdbcTemplate.query(existingByIdSql, rs -> rs.next()
					? new TriggerEventLink(rs.getString("trigger_event_id"), nullableLong(rs, "trigger_event_pk"))
					: TriggerEventLink.empty(), String.valueOf(jobExecutionId), hasScenarioFilter ? 1 : 0, scenarioFilter)).orElse(TriggerEventLink.empty());
	}

	private Long resolveRunRecordPk(Long jobExecutionId) {
		RunRecordRef runRecordRef = resolveMostRecentRunRecordByJobExecutionId(jobExecutionId);
		return runRecordRef.isEmpty() ? null : runRecordRef.runRecordPk();
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
					set launched_run_id = ?, launched_run_pk = ?, updated_at = ?, updated_by = ?
					where (trigger_event_pk = ? or trigger_event_id = ?)
					  and ((launched_run_id is null or trim(launched_run_id) = '') or launched_run_pk is null)
					""",
					String.valueOf(jobExecutionId),
					runRecordPk,
					Timestamp.valueOf(LocalDateTime.now()),
					auditActor,
					triggerEvent.triggerEventPk(),
					triggerEvent.triggerEventId()
			);
		} catch (DataAccessException ignored) {
			// Trigger linkage is best-effort and should not block run-summary projection writes.
		}
	}

	private long nextRunRecordPk() {
		return nextPk("controlplane_run_record_pk");
	}

	private long nextRunSummaryPk() {
		return nextPk("controlplane_run_summary_pk");
	}

	private Long resolveRunSummaryPk(Long runRecordPk, Long jobExecutionId) {
		if (runRecordPk == null && jobExecutionId == null) {
			return null;
		}
		return jdbcTemplate.query(
				"select run_summary_pk from controlplane_run_summary where (? is not null and run_record_pk = ?) or (run_record_pk is null and job_execution_id = ?)",
				rs -> rs.next() ? rs.getObject(1, Long.class) : null,
				runRecordPk,
				runRecordPk,
				jobExecutionId
		);
	}

	private void ensurePkSequenceTable() {
		createTableIfMissing("controlplane_pk_sequence", """
				create table controlplane_pk_sequence (
					sequence_name varchar(120) not null primary key,
					next_value bigint not null,
					updated_at timestamp,
					created_by varchar(200),
					updated_by varchar(200)
				)
				""");
		ensureColumnExists("controlplane_pk_sequence", "updated_at", "timestamp");
		ensureColumnExists("controlplane_pk_sequence", "created_by", "varchar(200)");
		ensureColumnExists("controlplane_pk_sequence", "updated_by", "varchar(200)");
	}

	private long nextPk(String sequenceName) {
		String normalizedSequenceName = normalize(sequenceName).toLowerCase(Locale.ROOT);
		Timestamp now = Timestamp.valueOf(LocalDateTime.now());
		for (int attempt = 0; attempt < 20; attempt++) {
			Long current = jdbcTemplate.query(
					"select next_value from controlplane_pk_sequence where sequence_name = ?",
					rs -> rs.next() ? rs.getLong(1) : null,
					normalizedSequenceName
			);
			if (current == null) {
				try {
					jdbcTemplate.update(
							"insert into controlplane_pk_sequence (sequence_name, next_value, updated_at, created_by, updated_by) values (?, ?, ?, ?, ?)",
							normalizedSequenceName,
							2L,
							now,
							auditActor,
							auditActor
					);
					return 1L;
				} catch (DuplicateKeyException ignored) {
					continue;
				}
			}
			int updated = jdbcTemplate.update(
					"update controlplane_pk_sequence set next_value = ?, updated_at = ?, updated_by = ? where sequence_name = ? and next_value = ?",
					current + 1,
					now,
					auditActor,
					normalizedSequenceName,
					current
			);
			if (updated == 1) {
				return current;
			}
		}
		throw new IllegalStateException("Unable to allocate primary key for sequence '" + normalizedSequenceName + "'.");
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private String resolveAuditActor(String applicationName) {
		String normalized = applicationName == null ? "" : applicationName.trim();
		return normalized.isBlank() ? "spring-etl-engine" : normalized;
	}

	private boolean triggerEventTableAvailable() {
		try {
			jdbcTemplate.queryForObject("select count(*) from controlplane_trigger_event", Long.class);
			return true;
		} catch (DataAccessException ex) {
			return false;
		}
	}

	private String firstRowsClause(int limit) {
		if (sqlServerDialect) {
			return " offset 0 rows fetch next " + Math.max(1, limit) + " rows only";
		}
		return " limit " + Math.max(1, limit);
	}

	private boolean isSqlServerVendor(String vendor) {
		String normalized = normalize(vendor).toLowerCase(Locale.ROOT);
		return "mssql".equals(normalized) || "sqlserver".equals(normalized);
	}

	private String normalizeTriggerOriginToken(String sourceCode, String value, Long schedulePk, String externalOriginKey) {
		String sourceCodeToken = normalize(sourceCode).toUpperCase(Locale.ROOT);
		if ("SCHEDULE".equals(sourceCodeToken) || "EVENT".equals(sourceCodeToken) || "MANUAL".equals(sourceCodeToken)) {
			return sourceCodeToken;
		}
		String token = normalize(value).toUpperCase(Locale.ROOT);
		if ("SCHEDULE".equals(token)) {
			return "SCHEDULE";
		}
		if ("EVENT".equals(token)) {
			return "EVENT";
		}
		if (schedulePk != null) {
			return "SCHEDULE";
		}
		if (!normalize(externalOriginKey).isBlank()) {
			return "EVENT";
		}
		return "MANUAL";
	}

	private record RunRecordLinkCandidate(long jobExecutionId, String scenario, LocalDateTime startedAt) {
	}

	private record RunRecordBackfillCandidate(long jobExecutionId,
	                                         String scenario,
	                                         String status,
	                                         LocalDateTime startedAt,
	                                         LocalDateTime finishedAt,
	                                         Long durationSeconds,
	                                         Long sourceCount,
	                                         Long writtenCount,
	                                         Long rejectedCount,
	                                         String runMode,
	                                         String recoveryPolicy) {
	}

	private record RunLogArtifactBackfillCandidate(long jobExecutionId, Long runRecordPk, String runRecordId, String logPath) {
	}


	private record RunRecordRef(Long runRecordPk, String runRecordId) {
		private static RunRecordRef empty() {
			return new RunRecordRef(null, null);
		}

		private boolean isEmpty() {
			return runRecordPk == null || runRecordId == null || runRecordId.isBlank();
		}
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
		private Long stepExecutionId;
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

