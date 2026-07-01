package com.etl.controlplane.triggers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * JDBC-backed trigger-event registry for durable control-plane trigger history.
 */
@Repository
@ConditionalOnProperty(name = "controlplane.triggers.persistence.mode", havingValue = "jdbc")
public class JdbcTriggerEventRegistry implements TriggerEventRegistry {
	private static final List<TriggerSourceSeed> TRIGGER_SOURCE_SEEDS = List.of(
			new TriggerSourceSeed(1L, "MANUAL", "Manual", "Ad hoc operator or API-triggered launch"),
			new TriggerSourceSeed(2L, "SCHEDULE", "Schedule", "Native scheduler-origin launch"),
			new TriggerSourceSeed(3L, "EVENT", "Event", "File watcher or external event-origin launch")
	);

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
		Long schedulePk = resolveSchedulePk(normalizedScheduleId);
		String normalizedTriggerSourceCode = normalizeTriggerSourceCode(triggerOrigin, schedulePk, null);
		Long triggerSourcePk = resolveTriggerSourcePk(normalizedTriggerSourceCode);
		Long triggerEventPk = nextTriggerEventPk();
		Instant requestedAt = Instant.now();
		String triggerEventId = "te-" + UUID.randomUUID();

		jdbcTemplate.update("""
				insert into controlplane_trigger_event (
					trigger_event_pk,
					trigger_source_pk,
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
					schedule_pk,
					external_origin_key
				) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				triggerEventPk,
				triggerSourcePk,
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
				schedulePk,
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
				message,
				triggerOrigin
		);
	}

	@Override
	public List<TriggerEventView> listByJobKey(String jobKey, int limit) {
		return listByJobKey(jobKey, 0, limit);
	}

	@Override
	public List<TriggerEventView> listByJobKey(String jobKey, int offset, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		int safeOffset = Math.max(0, offset);
		return jdbcTemplate.query("""
				select trigger_event_id, job_key, decision_status, reason, requested_by, requested_at, launched_run_id, message,
				       trigger_origin, schedule_pk, external_origin_key,
				       (select source_code from controlplane_trigger_source ts where ts.trigger_source_pk = controlplane_trigger_event.trigger_source_pk) as trigger_source_code
				from controlplane_trigger_event
				where job_key = ?
				order by requested_at desc, trigger_event_id desc
				limit ? offset ?
				""",
				(rs, rowNum) -> toView(rs),
				normalize(jobKey),
				limit,
				safeOffset
		);
	}

	@Override
	public long countByJobKey(String jobKey) {
		Long value = jdbcTemplate.queryForObject(
				"select count(*) from controlplane_trigger_event where job_key = ?",
				Long.class,
				normalize(jobKey)
		);
		return value == null ? 0L : value;
	}

	@Override
	public List<TriggerEventView> listByScheduleId(String scheduleId, int limit) {
		return listByScheduleId(scheduleId, 0, limit);
	}

	@Override
	public List<TriggerEventView> listByScheduleId(String scheduleId, int offset, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		String normalizedScheduleId = normalizeScheduleId(scheduleId);
		if (normalizedScheduleId.isBlank()) {
			return List.of();
		}
		int safeOffset = Math.max(0, offset);
		Long schedulePk = resolveSchedulePk(normalizedScheduleId);
		if (schedulePk == null) {
			return List.of();
		}
		return jdbcTemplate.query("""
						select trigger_event_id, job_key, decision_status, reason, requested_by, requested_at, launched_run_id, message,
						       trigger_origin, schedule_pk, external_origin_key,
						       (select source_code from controlplane_trigger_source ts where ts.trigger_source_pk = controlplane_trigger_event.trigger_source_pk) as trigger_source_code
						from controlplane_trigger_event
						where schedule_pk = ?
						order by requested_at desc, trigger_event_id desc
						limit ? offset ?
						""",
						(rs, rowNum) -> toView(rs),
						schedulePk,
						limit,
						safeOffset
		);
	}

	@Override
	public long countByScheduleId(String scheduleId) {
		String normalizedScheduleId = normalizeScheduleId(scheduleId);
		if (normalizedScheduleId.isBlank()) {
			return 0L;
		}
		Long schedulePk = resolveSchedulePk(normalizedScheduleId);
		if (schedulePk == null) {
			return 0L;
		}
		Long value = jdbcTemplate.queryForObject(
				"""
				select count(*)
				from controlplane_trigger_event
				where schedule_pk = ?
				""",
				Long.class,
				schedulePk
		);
		return value == null ? 0L : value;
	}

	private TriggerEventView toView(java.sql.ResultSet rs) throws java.sql.SQLException {
		String normalizedTriggerOrigin = normalizeTriggerOrigin(
				rs.getString("trigger_origin"),
				rs.getString("trigger_source_code"),
				rs.getObject("schedule_pk", Long.class),
				rs.getString("external_origin_key")
		);
		return new TriggerEventView(
				rs.getString("trigger_event_id"),
				rs.getString("job_key"),
				rs.getString("decision_status"),
				rs.getString("reason"),
				rs.getString("requested_by"),
				rs.getTimestamp("requested_at").toInstant(),
				rs.getString("launched_run_id"),
				rs.getString("message"),
				normalizedTriggerOrigin
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
				create table if not exists controlplane_trigger_source (
					trigger_source_pk bigint primary key,
					source_code varchar(50) not null unique,
					display_name varchar(100) not null,
					description varchar(300),
					is_active boolean not null,
					created_at timestamp not null,
					updated_at timestamp not null
				)
				""");
		seedTriggerSourceMaster();
		jdbcTemplate.execute("""
				create table if not exists controlplane_trigger_event (
					trigger_event_pk bigint primary key,
					trigger_source_pk bigint,
					trigger_event_id varchar(80) not null unique,
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
		ensureColumnExists("controlplane_trigger_event", "trigger_source_pk", "bigint");
		ensureColumnExists("controlplane_trigger_event", "trigger_event_pk", "bigint");
		ensureColumnExists("controlplane_trigger_event", "launched_run_pk", "bigint");
		ensureColumnExists("controlplane_trigger_event", "schedule_pk", "bigint");
		ensureColumnExists("controlplane_trigger_event", "trigger_origin", "varchar(50)");
		backfillTriggerOrigin();
		backfillTriggerSourcePk();
		backfillLaunchedRunPk();
		createIndexIfMissing("controlplane_trigger_event", "idx_trigger_event_pk",
				"create unique index idx_trigger_event_pk on controlplane_trigger_event (trigger_event_pk)");
		createIndexIfMissing("controlplane_trigger_event", "idx_trigger_event_job_time",
				"create index idx_trigger_event_job_time on controlplane_trigger_event (job_key, requested_at)");
		createIndexIfMissing("controlplane_trigger_event", "idx_trigger_event_origin",
				"create index idx_trigger_event_origin on controlplane_trigger_event (trigger_origin, requested_at)");
		createIndexIfMissing("controlplane_trigger_event", "idx_trigger_event_source_pk",
				"create index idx_trigger_event_source_pk on controlplane_trigger_event (trigger_source_pk, requested_at)");
		createIndexIfMissing("controlplane_trigger_event", "idx_trigger_event_schedule_pk_time",
				"create index idx_trigger_event_schedule_pk_time on controlplane_trigger_event (schedule_pk, requested_at)");
		dropIndexIfPresent("controlplane_trigger_event", "idx_trigger_event_schedule_time");
		createIndexIfMissing("controlplane_trigger_event", "idx_trigger_event_launched_run_pk",
				"create index idx_trigger_event_launched_run_pk on controlplane_trigger_event (launched_run_pk, requested_at)");
		createIndexIfMissing("controlplane_trigger_event", "idx_trigger_event_launched_run_id",
				"create index idx_trigger_event_launched_run_id on controlplane_trigger_event (launched_run_id, requested_at)");
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
			jdbcTemplate.execute(createIndexSql);
		}
	}

	private void dropIndexIfPresent(String tableName, String indexName) {
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
			return;
		}
		try {
			jdbcTemplate.execute("drop index " + indexName + " on " + tableName);
		} catch (org.springframework.dao.DataAccessException ignored) {
			// Keep startup resilient across dialect differences during bridge cleanup.
		}
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

	// SQLite bridge migrations were removed; active schema lifecycle is MySQL/SQL Server focused.

	private void backfillTriggerOrigin() {
		jdbcTemplate.update("""
				update controlplane_trigger_event
				set trigger_origin = 'SCHEDULE'
				where (trigger_origin is null or trim(trigger_origin) = '')
				  and schedule_pk is not null
				""");
		jdbcTemplate.update("""
				update controlplane_trigger_event
				set trigger_origin = 'EVENT'
				where (trigger_origin is null or trim(trigger_origin) = '')
				  and (external_origin_key is not null and trim(external_origin_key) <> '')
				""");
		jdbcTemplate.update("""
				update controlplane_trigger_event
				set trigger_origin = 'MANUAL'
				where trigger_origin is null or trim(trigger_origin) = ''
				""");
	}

	private void backfillTriggerSourcePk() {
		jdbcTemplate.update("""
				update controlplane_trigger_event
				set trigger_source_pk = (
					select ts.trigger_source_pk
					from controlplane_trigger_source ts
					where ts.source_code = upper(trim(controlplane_trigger_event.trigger_origin))
				)
				where trigger_source_pk is null
				  and trigger_origin is not null
				  and trim(trigger_origin) <> ''
				  and exists (
					select 1
					from controlplane_trigger_source ts
					where ts.source_code = upper(trim(controlplane_trigger_event.trigger_origin))
				  )
				""");
		jdbcTemplate.update("""
				update controlplane_trigger_event
				set trigger_source_pk = (
					select ts.trigger_source_pk
					from controlplane_trigger_source ts
					where ts.source_code = (
						case
							when schedule_pk is not null then 'SCHEDULE'
							when (external_origin_key is not null and trim(external_origin_key) <> '') then 'EVENT'
							else 'MANUAL'
						end
					)
				)
				where trigger_source_pk is null
				""");
	}
	private void seedTriggerSourceMaster() {
		Timestamp now = Timestamp.from(Instant.now());
		for (TriggerSourceSeed seed : TRIGGER_SOURCE_SEEDS) {
			int updated = jdbcTemplate.update("""
					update controlplane_trigger_source
					set display_name = ?,
					    description = ?,
					    is_active = ?,
					    updated_at = ?
					where source_code = ?
					""",
					seed.displayName(),
					seed.description(),
					1,
					now,
					seed.sourceCode()
			);
			if (updated > 0) {
				continue;
			}
			try {
				jdbcTemplate.update("""
						insert into controlplane_trigger_source (
							trigger_source_pk,
							source_code,
							display_name,
							description,
							is_active,
							created_at,
							updated_at
						) values (?, ?, ?, ?, ?, ?, ?)
						""",
						seed.triggerSourcePk(),
						seed.sourceCode(),
						seed.displayName(),
						seed.description(),
						1,
						now,
						now
				);
			} catch (DuplicateKeyException ignored) {
				jdbcTemplate.update("""
						update controlplane_trigger_source
						set display_name = ?,
						    description = ?,
						    is_active = ?,
						    updated_at = ?
						where source_code = ?
						""",
						seed.displayName(),
						seed.description(),
						1,
						now,
						seed.sourceCode()
				);
			}
		}
	}

	private Long resolveTriggerSourcePk(String triggerSourceCode) {
		String normalizedCode = normalize(triggerSourceCode).toUpperCase(Locale.ROOT);
		if (normalizedCode.isBlank()) {
			return null;
		}
		try {
			return jdbcTemplate.query(
					"select trigger_source_pk from controlplane_trigger_source where source_code = ?",
					rs -> rs.next() ? rs.getObject(1, Long.class) : null,
					normalizedCode
			);
		} catch (org.springframework.dao.DataAccessException ignored) {
			return null;
		}
	}

	private void backfillLaunchedRunPk() {
		try {
			List<LaunchedRunPkBackfillCandidate> candidates = jdbcTemplate.query("""
					select trigger_event_pk, trigger_event_id, launched_run_id
					from controlplane_trigger_event
					where launched_run_pk is null
					  and launched_run_id is not null
					  and trim(launched_run_id) <> ''
					""", (rs, rowNum) -> new LaunchedRunPkBackfillCandidate(
					rs.getObject("trigger_event_pk", Long.class),
					rs.getString("trigger_event_id"),
					rs.getString("launched_run_id")
			));
			for (LaunchedRunPkBackfillCandidate candidate : candidates) {
				Long runExecutionId = parseLongSafe(candidate.launchedRunId());
				if (runExecutionId == null) {
					continue;
				}
				Long runRecordPk = jdbcTemplate.query(
						"select run_record_pk from controlplane_run_record where job_execution_id = ?",
						rs -> rs.next() ? rs.getObject(1, Long.class) : null,
						runExecutionId
				);
				if (runRecordPk == null) {
					continue;
				}
				jdbcTemplate.update(
						"update controlplane_trigger_event set launched_run_pk = ? where launched_run_pk is null and (trigger_event_pk = ? or trigger_event_id = ?)",
						runRecordPk,
						candidate.triggerEventPk(),
						candidate.triggerEventId()
				);
			}
		} catch (org.springframework.dao.DataAccessException ignored) {
			// Keep trigger persistence available even when run-record table state is optional.
		}
	}

	private Long parseLongSafe(String value) {
		String normalized = normalize(value);
		if (normalized.isBlank()) {
			return null;
		}
		try {
			return Long.parseLong(normalized);
		} catch (NumberFormatException ignored) {
			return null;
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

	private String normalizeTriggerOrigin(String triggerOrigin,
	                                     String triggerSourceCode,
	                                     Long schedulePk,
	                                     String externalOriginKey) {
		String normalizedMasterCode = normalize(triggerSourceCode).toUpperCase(Locale.ROOT);
		if ("SCHEDULE".equals(normalizedMasterCode) || "EVENT".equals(normalizedMasterCode) || "MANUAL".equals(normalizedMasterCode)) {
			return normalizedMasterCode;
		}
		String normalizedOrigin = normalize(triggerOrigin).toUpperCase();
		if ("SCHEDULE".equals(normalizedOrigin) || "EVENT".equals(normalizedOrigin) || "MANUAL".equals(normalizedOrigin)) {
			return normalizedOrigin;
		}
		if (schedulePk != null) {
			return "SCHEDULE";
		}
		if (!normalize(externalOriginKey).isBlank()) {
			return "EVENT";
		}
		return "MANUAL";
	}

	private String normalizeTriggerSourceCode(String triggerOrigin,
	                                        Long schedulePk,
	                                        String externalOriginKey) {
		return normalizeTriggerOrigin(triggerOrigin, null, schedulePk, externalOriginKey);
	}

	private record TriggerSourceSeed(Long triggerSourcePk, String sourceCode, String displayName, String description) {
	}

	private record LaunchedRunPkBackfillCandidate(Long triggerEventPk, String triggerEventId, String launchedRunId) {
	}
}


