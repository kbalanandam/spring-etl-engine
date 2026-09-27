package com.etl.controlplane.monitoring;

import com.etl.controlplane.persistence.jpa.JpaControlPlanePkAllocator;
import com.etl.controlplane.persistence.jpa.entity.ArtifactRecord;
import com.etl.controlplane.persistence.jpa.entity.AttemptLink;
import com.etl.controlplane.persistence.jpa.entity.CheckpointAnchor;
import com.etl.controlplane.persistence.jpa.entity.LogCheckpoint;
import com.etl.controlplane.persistence.jpa.entity.RunRecord;
import com.etl.controlplane.persistence.jpa.entity.RunSummary;
import com.etl.controlplane.persistence.jpa.entity.StepRecord;
import com.etl.controlplane.persistence.jpa.entity.TriggerEvent;
import com.etl.controlplane.persistence.jpa.repository.ArtifactRecordRepository;
import com.etl.controlplane.persistence.jpa.repository.AttemptLinkRepository;
import com.etl.controlplane.persistence.jpa.repository.CheckpointAnchorRepository;
import com.etl.controlplane.persistence.jpa.repository.LogCheckpointRepository;
import com.etl.controlplane.persistence.jpa.repository.RunRecordRepository;
import com.etl.controlplane.persistence.jpa.repository.RunSummaryRepository;
import com.etl.controlplane.persistence.jpa.repository.StepRecordRepository;
import com.etl.controlplane.persistence.jpa.repository.TriggerEventRepository;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Comparator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Collectors;

/**
 * R3 slice: JPA-backed run summary registry with parity for core read/write paths.
 */
@Repository
@ConditionalOnProperty(name = "controlplane.runs.persistence.mode", havingValue = "jpa")
public class JpaRunSummaryRegistry implements RunSummaryRegistry {

	private final RunSummaryRepository runSummaryRepository;
	private final RunRecordRepository runRecordRepository;
	private final StepRecordRepository stepRecordRepository;
	private final ArtifactRecordRepository artifactRecordRepository;
	private final AttemptLinkRepository attemptLinkRepository;
	private final CheckpointAnchorRepository checkpointAnchorRepository;
	private final LogCheckpointRepository logCheckpointRepository;
	private final TriggerEventRepository triggerEventRepository;
	private final JpaControlPlanePkAllocator pkAllocator;
	private final int retention;
	private final String auditActor;

	public JpaRunSummaryRegistry(RunSummaryRepository runSummaryRepository,
	                            RunRecordRepository runRecordRepository,
	                            StepRecordRepository stepRecordRepository,
	                            ArtifactRecordRepository artifactRecordRepository,
	                            AttemptLinkRepository attemptLinkRepository,
	                            CheckpointAnchorRepository checkpointAnchorRepository,
	                            LogCheckpointRepository logCheckpointRepository,
	                            TriggerEventRepository triggerEventRepository,
	                            JpaControlPlanePkAllocator pkAllocator,
	                            @Value("${controlplane.runs.retention:5000}") int retention,
	                            @Value("${spring.application.name:spring-etl-engine}") String applicationName) {
		this.runSummaryRepository = runSummaryRepository;
		this.runRecordRepository = runRecordRepository;
		this.stepRecordRepository = stepRecordRepository;
		this.artifactRecordRepository = artifactRecordRepository;
		this.attemptLinkRepository = attemptLinkRepository;
		this.checkpointAnchorRepository = checkpointAnchorRepository;
		this.logCheckpointRepository = logCheckpointRepository;
		this.triggerEventRepository = triggerEventRepository;
		this.pkAllocator = pkAllocator;
		this.retention = Math.max(1, retention);
		String normalized = applicationName == null ? "" : applicationName.trim();
		this.auditActor = normalized.isBlank() ? "spring-etl-engine" : normalized;
	}

	@Override
	@Transactional
	public void upsert(RunSummaryView runSummary) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null) {
			return;
		}

		java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
		RunRecord runRecord = runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(jobExecutionId)
				.orElseGet(RunRecord::new);
		if (runRecord.getRunRecordPk() == null) {
			runRecord.setRunRecordPk(pkAllocator.nextPk("controlplane_run_record_pk"));
			runRecord.setRunRecordId("rr-" + jobExecutionId);
			runRecord.setJobExecutionId(jobExecutionId);
			runRecord.setCreatedAt(now);
			runRecord.setCreatedBy(auditActor);
		}

		runRecord.setTriggerEventId(normalize(runSummary.triggerEventId()).isBlank() ? runRecord.getTriggerEventId() : normalize(runSummary.triggerEventId()));
		runRecord.setTriggerEventPk(resolveTriggerEventPk(runRecord.getTriggerEventId()));
		runRecord.setSelectedJobKey(normalize(runSummary.scenario()).isBlank() ? runRecord.getSelectedJobKey() : normalize(runSummary.scenario()));
		runRecord.setScenario(requiredValue(runSummary.scenario(), runRecord.getScenario(), "unknown"));
		runRecord.setRunStatus(requiredValue(runSummary.status(), runRecord.getRunStatus(), "UNKNOWN"));
		runRecord.setStartedAt(runSummary.startTime());
		runRecord.setFinishedAt(runSummary.endTime());
		runRecord.setDurationSeconds(runSummary.durationSeconds());
		runRecord.setSourceCount(runSummary.sourceCount());
		runRecord.setWrittenCount(runSummary.writtenCount());
		runRecord.setRejectedCount(runSummary.rejectedCount());
		runRecord.setRunMode(runSummary.runMode());
		runRecord.setRecoveryPolicy(runSummary.recoveryPolicy());
		runRecord.setUpdatedAt(now);
		runRecord.setUpdatedBy(auditActor);
		runRecordRepository.save(runRecord);

		RunSummary summaryEntity = runSummaryRepository.findByJobExecutionId(jobExecutionId).orElseGet(RunSummary::new);
		if (summaryEntity.getRunSummaryPk() == null) {
			summaryEntity.setRunSummaryPk(pkAllocator.nextPk("controlplane_run_summary_pk"));
			summaryEntity.setCreatedBy(auditActor);
		}
		summaryEntity.setRunRecordPk(runRecord.getRunRecordPk());
		summaryEntity.setJobExecutionId(jobExecutionId);
		summaryEntity.setScenario(requiredValue(runSummary.scenario(), summaryEntity.getScenario(), "unknown"));
		summaryEntity.setStatus(requiredValue(runSummary.status(), summaryEntity.getStatus(), "UNKNOWN"));
		summaryEntity.setStartTime(runSummary.startTime());
		summaryEntity.setEndTime(runSummary.endTime());
		summaryEntity.setDurationSeconds(runSummary.durationSeconds());
		summaryEntity.setSourceCount(runSummary.sourceCount());
		summaryEntity.setWrittenCount(runSummary.writtenCount());
		summaryEntity.setRejectedCount(runSummary.rejectedCount());
		summaryEntity.setRunMode(runSummary.runMode());
		summaryEntity.setRecoveryPolicy(runSummary.recoveryPolicy());
		summaryEntity.setLogPath(runSummary.logPath());
		summaryEntity.setLastSeenAt(now);
		summaryEntity.setUpdatedAt(now);
		summaryEntity.setUpdatedBy(auditActor);
		runSummaryRepository.save(summaryEntity);

		try {
			backfillLaunchedRunLink(runRecord, now);
			upsertAttemptLinkRecord(runRecord, now);
			upsertCheckpointAnchorRecord(runRecord, summaryEntity, now);
		} catch (RuntimeException ignored) {
			// Keep control-plane projection writes additive even when advisory lineage rows cannot be updated.
		}

		pruneOverflow();
	}

	@Override
	@Transactional
	public void upsertStepSnapshots(long jobExecutionId, Collection<? extends StepExecution> stepExecutions) {
		if (jobExecutionId <= 0L || stepExecutions == null || stepExecutions.isEmpty()) {
			return;
		}
		Optional<RunRecord> runRecordOptional = runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(jobExecutionId);
		if (runRecordOptional.isEmpty()) {
			return;
		}
		RunRecord runRecord = runRecordOptional.get();
		java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);

		stepExecutions.stream()
				.filter(java.util.Objects::nonNull)
				.sorted(Comparator.comparing(StepExecution::getId, Comparator.nullsLast(Long::compareTo)))
				.forEach(stepExecution -> {
					String stepRecordId = toStepRecordId(runRecord.getRunRecordPk(), jobExecutionId, stepExecution);
					if (stepRecordId.isBlank()) {
						return;
					}
					StepRecord stepRecord = stepRecordRepository.findByStepRecordId(stepRecordId).orElseGet(StepRecord::new);
					if (stepRecord.getStepRecordPk() == null) {
						stepRecord.setStepRecordPk(pkAllocator.nextPk("controlplane_step_record_pk"));
						stepRecord.setStepRecordId(stepRecordId);
						stepRecord.setRunRecordPk(runRecord.getRunRecordPk());
						stepRecord.setCreatedAt(now);
						stepRecord.setCreatedBy(auditActor);
					}
					stepRecord.setStepName(requiredValue(stepExecution.getStepName(), stepRecord.getStepName(), "unknown-step"));
					stepRecord.setStepStatus(stepExecution.getStatus() == null ? "UNKNOWN" : requiredValue(stepExecution.getStatus().toString(), stepRecord.getStepStatus(), "UNKNOWN"));
					stepRecord.setStartedAt(stepExecution.getStartTime());
					stepRecord.setFinishedAt(stepExecution.getEndTime());
					stepRecord.setDurationSeconds(calculateDurationSeconds(stepExecution.getStartTime(), stepExecution.getEndTime()));
					stepRecord.setReadCount((long) stepExecution.getReadCount());
					stepRecord.setWriteCount((long) stepExecution.getWriteCount());
					stepRecord.setFilterCount((long) stepExecution.getFilterCount());
					stepRecord.setSkipCount(null);
					stepRecord.setRollbackCount((long) stepExecution.getRollbackCount());
					stepRecord.setRejectedCount(null);
					stepRecord.setUpdatedAt(now);
					stepRecord.setUpdatedBy(auditActor);
					stepRecordRepository.save(stepRecord);
				});
	}

	@Override
	public Optional<LogReadCheckpoint> findLogCheckpoint(String logPath) {
		String normalizedLogPath = normalize(logPath);
		if (normalizedLogPath.isBlank()) {
			return Optional.empty();
		}
		return logCheckpointRepository.findById(normalizedLogPath)
				.map(entity -> new LogReadCheckpoint(
						entity.getLogPath(),
						coalesce(entity.getLastOffsetBytes()),
						coalesce(entity.getFileSizeAtCheckpoint()),
						coalesce(entity.getFileMtimeAtCheckpoint())
				));
	}

	@Override
	@Transactional
	public void upsertLogCheckpoint(String logPath, long offsetBytes, long fileSizeBytes, long fileLastModifiedMillis) {
		String normalizedLogPath = normalize(logPath);
		if (normalizedLogPath.isBlank()) {
			return;
		}
		java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
		LogCheckpoint checkpoint = logCheckpointRepository.findById(normalizedLogPath).orElseGet(LogCheckpoint::new);
		if (normalize(checkpoint.getLogPath()).isBlank()) {
			checkpoint.setLogPath(normalizedLogPath);
			checkpoint.setLogPathKey(toLogPathKey(normalizedLogPath));
			checkpoint.setCreatedBy(auditActor);
		}
		checkpoint.setLastOffsetBytes(offsetBytes);
		checkpoint.setFileSizeAtCheckpoint(fileSizeBytes);
		checkpoint.setFileMtimeAtCheckpoint(fileLastModifiedMillis);
		checkpoint.setUpdatedAt(now);
		checkpoint.setUpdatedBy(auditActor);
		logCheckpointRepository.save(checkpoint);
	}

	@Override
	public List<RunSummaryView> latestRuns(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		// Spring Data JPA criteria sorting does not support explicit null precedence in this path.
		Sort sort = Sort.by(
				Sort.Order.desc("startTime"),
				Sort.Order.desc("jobExecutionId")
		);
		return runSummaryRepository.findAll(sort)
				.stream()
				.limit(limit)
				.map(this::toRunSummaryView)
				.toList();
	}

	@Override
	public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
		return runSummaryRepository.findByJobExecutionId(jobExecutionId).map(this::toRunSummaryView);
	}

	@Override
	public Optional<RunRecoveryView> findRecoveryByJobExecutionId(long jobExecutionId) {
		Optional<RunRecord> runRecord = runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(jobExecutionId);
		if (runRecord.isEmpty()) {
			return Optional.empty();
		}
		List<RunCheckpointAnchorView> checkpointAnchors = listCheckpointAnchorsByRunRecordPk(runRecord.get().getRunRecordPk());
		List<AttemptLink> attemptLinks = attemptLinkRepository.findByRunRecordPkOrderByCreatedAtDescAttemptLinkPkDesc(runRecord.get().getRunRecordPk());
		if (!attemptLinks.isEmpty()) {
			AttemptLink latestLink = attemptLinks.get(0);
			RunRecord priorRun = latestLink.getPriorRunRecordPk() == null
					? null
					: runRecordRepository.findById(latestLink.getPriorRunRecordPk()).orElse(null);
			return Optional.of(RunRecoveryView.advisoryResumeNotSupported(
					jobExecutionId,
					runRecord.get().getRunRecordId(),
					latestLink.getAttemptLinkId(),
					latestLink.getLinkKind(),
					priorRun == null ? null : priorRun.getRunRecordId(),
					priorRun == null ? null : priorRun.getJobExecutionId(),
					checkpointAnchors
			));
		}
		return Optional.of(RunRecoveryView.advisoryResumeNotSupported(
				jobExecutionId,
				runRecord.get().getRunRecordId(),
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
		Optional<RunRecord> runRecord = runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(jobExecutionId);
		if (runRecord.isEmpty()) {
			return List.of();
		}
		List<RunStepRecordView> records = stepRecordRepository.findByRunRecordPkOrderByStartedAtAscStepRecordIdAsc(runRecord.get().getRunRecordPk())
				.stream()
				.map(step -> toRunStepRecordView(step, runRecord.get().getRunRecordId()))
				.toList();
		return records.size() <= limit ? records : records.subList(0, limit);
	}

	@Override
	public List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		Optional<RunRecord> runRecord = runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(jobExecutionId);
		if (runRecord.isEmpty()) {
			return List.of();
		}
		List<RunArtifactRecordView> records = artifactRecordRepository.findByRunRecordPkOrderByCreatedAtDescArtifactRecordIdDesc(runRecord.get().getRunRecordPk())
				.stream()
				.map(artifact -> toRunArtifactRecordView(artifact, runRecord.get().getRunRecordId()))
				.toList();
		return records.size() <= limit ? records : records.subList(0, limit);
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
		List<ArtifactRecord> artifacts = artifactRecordRepository.findByStepRecordIdOrderByCreatedAtDescArtifactRecordIdDesc(normalizedStepRecordId);
		Map<Long, String> runRecordIdByPk = resolveRunRecordIds(artifacts);
		List<RunArtifactRecordView> records = artifacts.stream()
				.map(artifact -> toRunArtifactRecordView(artifact, runRecordIdByPk.getOrDefault(artifact.getRunRecordPk(), null)))
				.toList();
		return records.size() <= limit ? records : records.subList(0, limit);
	}

	private RunSummaryView toRunSummaryView(RunSummary summary) {
		Optional<RunRecord> runRecord = runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(summary.getJobExecutionId());
		String triggerEventId = runRecord.map(RunRecord::getTriggerEventId).orElse(null);
		String triggerOrigin = resolveTriggerOrigin(runRecord.orElse(null));
		return new RunSummaryView(
				summary.getScenario(),
				summary.getJobExecutionId(),
				summary.getStatus(),
				summary.getStartTime(),
				summary.getEndTime(),
				summary.getDurationSeconds(),
				summary.getSourceCount(),
				summary.getWrittenCount(),
				summary.getRejectedCount(),
				summary.getRunMode(),
				summary.getRecoveryPolicy(),
				triggerEventId,
				triggerOrigin,
				summary.getLogPath()
		);
	}

	private RunStepRecordView toRunStepRecordView(StepRecord record, String runRecordId) {
		return new RunStepRecordView(
				record.getStepRecordId(),
				runRecordId,
				record.getStepName(),
				record.getStepStatus(),
				record.getStartedAt(),
				record.getFinishedAt(),
				record.getDurationSeconds(),
				record.getReadCount(),
				record.getWriteCount(),
				record.getFilterCount(),
				record.getSkipCount(),
				record.getRollbackCount(),
				record.getRejectedCount()
		);
	}

	private RunArtifactRecordView toRunArtifactRecordView(ArtifactRecord record, String runRecordId) {
		return new RunArtifactRecordView(
				record.getArtifactRecordId(),
				runRecordId,
				record.getStepRecordId(),
				record.getArtifactRole(),
				record.getArtifactPath(),
				record.getCreatedAt()
		);
	}

	private Map<Long, String> resolveRunRecordIds(List<ArtifactRecord> artifacts) {
		Map<Long, String> runRecordIdByPk = new HashMap<>();
		for (Long runRecordPk : artifacts.stream().map(ArtifactRecord::getRunRecordPk).collect(Collectors.toSet())) {
			if (runRecordPk == null) {
				continue;
			}
			runRecordRepository.findById(runRecordPk).ifPresent(runRecord -> runRecordIdByPk.put(runRecordPk, runRecord.getRunRecordId()));
		}
		return runRecordIdByPk;
	}

	private String resolveTriggerOrigin(RunRecord runRecord) {
		if (runRecord == null) {
			return "MANUAL";
		}
		TriggerEvent triggerEvent = null;
		if (runRecord.getTriggerEventPk() != null) {
			triggerEvent = triggerEventRepository.findById(runRecord.getTriggerEventPk()).orElse(null);
		}
		if (triggerEvent == null && !normalize(runRecord.getTriggerEventId()).isBlank()) {
			triggerEvent = triggerEventRepository.findByTriggerEventId(runRecord.getTriggerEventId()).orElse(null);
		}
		if (triggerEvent == null) {
			return "MANUAL";
		}
		String triggerOrigin = normalize(triggerEvent.getTriggerOrigin()).toUpperCase(Locale.ROOT);
		if ("SCHEDULE".equals(triggerOrigin) || "EVENT".equals(triggerOrigin) || "MANUAL".equals(triggerOrigin)) {
			return triggerOrigin;
		}
		if (triggerEvent.getSchedulePk() != null) {
			return "SCHEDULE";
		}
		if (!normalize(triggerEvent.getExternalOriginKey()).isBlank()) {
			return "EVENT";
		}
		return "MANUAL";
	}

	private Long resolveTriggerEventPk(String triggerEventId) {
		String normalizedTriggerEventId = normalize(triggerEventId);
		if (normalizedTriggerEventId.isBlank()) {
			return null;
		}
		return triggerEventRepository.findByTriggerEventId(normalizedTriggerEventId)
				.map(TriggerEvent::getTriggerEventPk)
				.orElse(null);
	}

	private void backfillLaunchedRunLink(RunRecord runRecord, java.time.LocalDateTime now) {
		if (runRecord == null || runRecord.getJobExecutionId() == null || runRecord.getRunRecordPk() == null) {
			return;
		}
		String normalizedTriggerEventId = normalize(runRecord.getTriggerEventId());
		Long triggerEventPk = runRecord.getTriggerEventPk();
		if (triggerEventPk == null && normalizedTriggerEventId.isBlank()) {
			return;
		}
		Optional<TriggerEvent> triggerEvent = triggerEventPk == null
				? Optional.empty()
				: triggerEventRepository.findById(triggerEventPk);
		if (triggerEvent.isEmpty() && !normalizedTriggerEventId.isBlank()) {
			triggerEvent = triggerEventRepository.findByTriggerEventId(normalizedTriggerEventId);
		}
		if (triggerEvent.isEmpty()) {
			return;
		}
		TriggerEvent existing = triggerEvent.get();
		boolean updated = false;
		String normalizedJobExecutionId = String.valueOf(runRecord.getJobExecutionId());
		if (normalize(existing.getLaunchedRunId()).isBlank()) {
			existing.setLaunchedRunId(normalizedJobExecutionId);
			updated = true;
		}
		if (existing.getLaunchedRunPk() == null) {
			existing.setLaunchedRunPk(runRecord.getRunRecordPk());
			updated = true;
		}
		if (!updated) {
			return;
		}
		existing.setUpdatedAt(now);
		existing.setUpdatedBy(auditActor);
		triggerEventRepository.save(existing);
	}

	private void pruneOverflow() {
		Sort sort = Sort.by(
				Sort.Order.desc("startTime"),
				Sort.Order.desc("jobExecutionId")
		);
		List<RunSummary> summaries = runSummaryRepository.findAll(sort);
		if (summaries.size() <= retention) {
			return;
		}
		for (RunSummary stale : summaries.subList(retention, summaries.size())) {
			Long staleRunRecordPk = stale.getRunRecordPk();
			if (staleRunRecordPk != null) {
				checkpointAnchorRepository.findByRunRecordPkOrderByCreatedAtDescCheckpointAnchorPkDesc(staleRunRecordPk)
						.forEach(checkpointAnchorRepository::delete);
				attemptLinkRepository.findByRunRecordPkOrderByCreatedAtDescAttemptLinkPkDesc(staleRunRecordPk)
						.forEach(attemptLinkRepository::delete);
				artifactRecordRepository.findByRunRecordPkOrderByCreatedAtDescArtifactRecordIdDesc(staleRunRecordPk)
						.forEach(artifactRecordRepository::delete);
				stepRecordRepository.findByRunRecordPkOrderByStartedAtAscStepRecordIdAsc(staleRunRecordPk)
						.forEach(stepRecordRepository::delete);
			}
			runSummaryRepository.delete(stale);
			runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(stale.getJobExecutionId())
					.ifPresent(runRecordRepository::delete);
		}
	}

	private void upsertAttemptLinkRecord(RunRecord runRecord, java.time.LocalDateTime now) {
		if (runRecord == null || runRecord.getRunRecordPk() == null || runRecord.getJobExecutionId() == null) {
			return;
		}
		RunRecord priorRunRecord = resolvePriorRunRecord(runRecord);
		String attemptLinkId = "al-" + runRecord.getRunRecordPk();
		AttemptLink attemptLink = attemptLinkRepository.findByAttemptLinkId(attemptLinkId).orElseGet(AttemptLink::new);
		if (attemptLink.getAttemptLinkPk() == null) {
			attemptLink.setAttemptLinkPk(pkAllocator.nextPk("controlplane_attempt_link_pk"));
			attemptLink.setAttemptLinkId(attemptLinkId);
			attemptLink.setCreatedAt(now);
			attemptLink.setCreatedBy(auditActor);
		}
		attemptLink.setRunRecordPk(runRecord.getRunRecordPk());
		attemptLink.setPriorRunRecordPk(priorRunRecord == null ? null : priorRunRecord.getRunRecordPk());
		attemptLink.setLinkKind(priorRunRecord == null ? "INITIAL" : "RERUN");
		attemptLink.setUpdatedAt(now);
		attemptLink.setUpdatedBy(auditActor);
		attemptLinkRepository.save(attemptLink);
	}

	private void upsertCheckpointAnchorRecord(RunRecord runRecord, RunSummary summaryEntity, java.time.LocalDateTime now) {
		if (runRecord == null || runRecord.getRunRecordPk() == null || summaryEntity == null) {
			return;
		}
		String anchorRef = normalize(summaryEntity.getLogPath());
		if (anchorRef.isBlank()) {
			return;
		}
		String checkpointAnchorId = "ca-log-" + runRecord.getRunRecordPk();
		CheckpointAnchor checkpointAnchor = checkpointAnchorRepository.findByCheckpointAnchorId(checkpointAnchorId).orElseGet(CheckpointAnchor::new);
		if (checkpointAnchor.getCheckpointAnchorPk() == null) {
			checkpointAnchor.setCheckpointAnchorPk(pkAllocator.nextPk("controlplane_checkpoint_anchor_pk"));
			checkpointAnchor.setCheckpointAnchorId(checkpointAnchorId);
			checkpointAnchor.setCreatedAt(now);
			checkpointAnchor.setCreatedBy(auditActor);
		}
		checkpointAnchor.setRunRecordPk(runRecord.getRunRecordPk());
		checkpointAnchor.setStepRecordPk(null);
		checkpointAnchor.setStepRecordId(null);
		checkpointAnchor.setAnchorKind("RUN_LOG");
		checkpointAnchor.setAnchorRef(anchorRef);
		checkpointAnchor.setAnchorStatus(requiredValue(summaryEntity.getStatus(), checkpointAnchor.getAnchorStatus(), "UNKNOWN"));
		checkpointAnchor.setUpdatedAt(now);
		checkpointAnchor.setUpdatedBy(auditActor);
		checkpointAnchorRepository.save(checkpointAnchor);
	}

	private RunRecord resolvePriorRunRecord(RunRecord runRecord) {
		String selectedJobKey = normalize(runRecord.getSelectedJobKey());
		if (selectedJobKey.isBlank()) {
			selectedJobKey = normalize(runRecord.getScenario());
		}
		if (selectedJobKey.isBlank()) {
			return null;
		}
		java.time.LocalDateTime currentStartedAt = runRecord.getStartedAt();
		for (RunRecord candidate : runRecordRepository.findBySelectedJobKeyOrderByStartedAtDescJobExecutionIdDesc(selectedJobKey)) {
			if (candidate == null || candidate.getRunRecordPk() == null || candidate.getRunRecordPk().equals(runRecord.getRunRecordPk())) {
				continue;
			}
			if (currentStartedAt == null || candidate.getStartedAt() == null || !candidate.getStartedAt().isAfter(currentStartedAt)) {
				return candidate;
			}
		}
		return null;
	}

	private List<RunCheckpointAnchorView> listCheckpointAnchorsByRunRecordPk(Long runRecordPk) {
		if (runRecordPk == null) {
			return List.of();
		}
		return checkpointAnchorRepository.findByRunRecordPkOrderByCreatedAtDescCheckpointAnchorPkDesc(runRecordPk)
				.stream()
				.map(anchor -> new RunCheckpointAnchorView(
						anchor.getCheckpointAnchorId(),
						anchor.getStepRecordId(),
						anchor.getAnchorKind(),
						anchor.getAnchorRef(),
						anchor.getAnchorStatus(),
						anchor.getCreatedAt(),
						anchor.getUpdatedAt()
				))
				.toList();
	}

	private String requiredValue(String preferred, String existing, String fallback) {
		String normalizedPreferred = normalize(preferred);
		if (!normalizedPreferred.isBlank()) {
			return normalizedPreferred;
		}
		String normalizedExisting = normalize(existing);
		if (!normalizedExisting.isBlank()) {
			return normalizedExisting;
		}
		return fallback;
	}

	private String toStepRecordId(Long runRecordPk, long jobExecutionId, StepExecution stepExecution) {
		String runIdentity = runRecordPk == null ? String.valueOf(jobExecutionId) : String.valueOf(runRecordPk);
		if (stepExecution.getId() != null) {
			return "sr-" + runIdentity + "-" + stepExecution.getId();
		}
		String stepNameKey = normalize(stepExecution.getStepName()).toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
		if (stepNameKey.isBlank()) {
			return "";
		}
		return "sr-" + runIdentity + "-name-" + stepNameKey;
	}

	private Long calculateDurationSeconds(java.time.LocalDateTime startedAt, java.time.LocalDateTime finishedAt) {
		if (startedAt == null || finishedAt == null) {
			return null;
		}
		long seconds = java.time.Duration.between(startedAt, finishedAt).getSeconds();
		return Math.max(0L, seconds);
	}

	private long coalesce(Long value) {
		return value == null ? 0L : value;
	}

	private String toLogPathKey(String normalizedLogPath) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(normalizedLogPath.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder(hash.length * 2);
			for (byte b : hash) {
				builder.append(String.format(Locale.ROOT, "%02x", b));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is unavailable for log checkpoint key derivation.", ex);
		}
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}






