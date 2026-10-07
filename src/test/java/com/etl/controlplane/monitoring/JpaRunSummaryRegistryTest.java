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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JpaRunSummaryRegistryTest {

	@Test
	void upsertPersistsRunRecordAndRunSummaryInJpaMode() {
		RunSummaryRepository runSummaryRepository = mock(RunSummaryRepository.class);
		RunRecordRepository runRecordRepository = mock(RunRecordRepository.class);
		StepRecordRepository stepRecordRepository = mock(StepRecordRepository.class);
		ArtifactRecordRepository artifactRecordRepository = mock(ArtifactRecordRepository.class);
		AttemptLinkRepository attemptLinkRepository = mock(AttemptLinkRepository.class);
		CheckpointAnchorRepository checkpointAnchorRepository = mock(CheckpointAnchorRepository.class);
		LogCheckpointRepository logCheckpointRepository = mock(LogCheckpointRepository.class);
		TriggerEventRepository triggerEventRepository = mock(TriggerEventRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);

		JpaRunSummaryRegistry registry = new JpaRunSummaryRegistry(
				runSummaryRepository,
				runRecordRepository,
				stepRecordRepository,
				artifactRecordRepository,
				attemptLinkRepository,
				checkpointAnchorRepository,
				logCheckpointRepository,
				triggerEventRepository,
				pkAllocator,
				100,
				"controlplane-test"
		);

		when(runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(160L)).thenReturn(Optional.empty());
		when(runRecordRepository.findBySelectedJobKeyOrderByStartedAtDescJobExecutionIdDesc("customer-load")).thenReturn(List.of());
		when(runSummaryRepository.findByJobExecutionId(160L)).thenReturn(Optional.empty());
		TriggerEvent triggerEvent = new TriggerEvent();
		triggerEvent.setTriggerEventPk(55L);
		triggerEvent.setTriggerEventId("te-123");
		when(triggerEventRepository.findByTriggerEventId("te-123")).thenReturn(Optional.of(triggerEvent));
		when(pkAllocator.nextPk("controlplane_run_record_pk")).thenReturn(11L);
		when(pkAllocator.nextPk("controlplane_run_summary_pk")).thenReturn(22L);
		when(pkAllocator.nextPk("controlplane_attempt_link_pk")).thenReturn(33L);
		when(pkAllocator.nextPk("controlplane_checkpoint_anchor_pk")).thenReturn(44L);
		when(runRecordRepository.save(any(RunRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(runSummaryRepository.save(any(RunSummary.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(attemptLinkRepository.findByAttemptLinkId("al-11")).thenReturn(Optional.empty());
		when(checkpointAnchorRepository.findByCheckpointAnchorId("ca-log-11")).thenReturn(Optional.empty());
		when(runSummaryRepository.findAll(any(Sort.class))).thenReturn(List.of());

		RunSummaryView runSummary = new RunSummaryView(
				"customer-load",
				160L,
				"COMPLETED",
				LocalDateTime.of(2026, 8, 19, 6, 0),
				LocalDateTime.of(2026, 8, 19, 6, 1),
				60L,
				10L,
				10L,
				0L,
				"explicit-job",
				"rerun-from-start",
				"te-123",
				"MANUAL",
				"C:/logs/2026-08-19/customer-load.log"
		);

		registry.upsert(runSummary);

		ArgumentCaptor<RunRecord> runRecordCaptor = ArgumentCaptor.forClass(RunRecord.class);
		verify(runRecordRepository).save(runRecordCaptor.capture());
		RunRecord savedRunRecord = runRecordCaptor.getValue();
		assertEquals(11L, savedRunRecord.getRunRecordPk());
		assertEquals("rr-160", savedRunRecord.getRunRecordId());
		assertEquals("customer-load", savedRunRecord.getScenario());
		assertEquals("COMPLETED", savedRunRecord.getRunStatus());

		ArgumentCaptor<RunSummary> runSummaryCaptor = ArgumentCaptor.forClass(RunSummary.class);
		verify(runSummaryRepository).save(runSummaryCaptor.capture());
		RunSummary savedSummary = runSummaryCaptor.getValue();
		assertEquals(22L, savedSummary.getRunSummaryPk());
		assertEquals(11L, savedSummary.getRunRecordPk());
		assertEquals(160L, savedSummary.getJobExecutionId());
		assertEquals("COMPLETED", savedSummary.getStatus());

		ArgumentCaptor<TriggerEvent> triggerEventCaptor = ArgumentCaptor.forClass(TriggerEvent.class);
		verify(triggerEventRepository).save(triggerEventCaptor.capture());
		TriggerEvent savedTriggerEvent = triggerEventCaptor.getValue();
		assertEquals(11L, savedTriggerEvent.getLaunchedRunPk());
		assertEquals("160", savedTriggerEvent.getLaunchedRunId());
	}

	@Test
	void latestRunsMapsTriggerOriginFromTriggerEvent() {
		RunSummaryRepository runSummaryRepository = mock(RunSummaryRepository.class);
		RunRecordRepository runRecordRepository = mock(RunRecordRepository.class);
		StepRecordRepository stepRecordRepository = mock(StepRecordRepository.class);
		ArtifactRecordRepository artifactRecordRepository = mock(ArtifactRecordRepository.class);
		AttemptLinkRepository attemptLinkRepository = mock(AttemptLinkRepository.class);
		CheckpointAnchorRepository checkpointAnchorRepository = mock(CheckpointAnchorRepository.class);
		LogCheckpointRepository logCheckpointRepository = mock(LogCheckpointRepository.class);
		TriggerEventRepository triggerEventRepository = mock(TriggerEventRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);

		JpaRunSummaryRegistry registry = new JpaRunSummaryRegistry(
				runSummaryRepository,
				runRecordRepository,
				stepRecordRepository,
				artifactRecordRepository,
				attemptLinkRepository,
				checkpointAnchorRepository,
				logCheckpointRepository,
				triggerEventRepository,
				pkAllocator,
				100,
				"controlplane-test"
		);

		RunSummary summary = new RunSummary();
		summary.setRunSummaryPk(1L);
		summary.setRunRecordPk(9L);
		summary.setJobExecutionId(160L);
		summary.setScenario("customer-load");
		summary.setStatus("COMPLETED");
		summary.setStartTime(LocalDateTime.of(2026, 8, 19, 6, 0));
		summary.setEndTime(LocalDateTime.of(2026, 8, 19, 6, 1));
		summary.setDurationSeconds(60L);
		summary.setSourceCount(10L);
		summary.setWrittenCount(10L);
		summary.setRejectedCount(0L);
		summary.setRunMode("explicit-job");
		summary.setRecoveryPolicy("rerun-from-start");
		summary.setLogPath("C:/logs/run.log");

		RunRecord runRecord = new RunRecord();
		runRecord.setRunRecordPk(9L);
		runRecord.setRunRecordId("rr-160");
		runRecord.setJobExecutionId(160L);
		runRecord.setTriggerEventPk(21L);
		runRecord.setTriggerEventId("te-21");

		TriggerEvent triggerEvent = new TriggerEvent();
		triggerEvent.setTriggerEventPk(21L);
		triggerEvent.setTriggerOrigin("SCHEDULE");

		when(runSummaryRepository.findAll(any(Sort.class))).thenReturn(List.of(summary));
		when(runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(160L)).thenReturn(Optional.of(runRecord));
		when(triggerEventRepository.findById(21L)).thenReturn(Optional.of(triggerEvent));

		List<RunSummaryView> latest = registry.latestRuns(10);

		assertEquals(1, latest.size());
		assertEquals("te-21", latest.get(0).triggerEventId());
		assertEquals("SCHEDULE", latest.get(0).triggerOrigin());
	}

	@Test
	void listStepAndArtifactRecordsResolveRunRecordIdentity() {
		RunSummaryRepository runSummaryRepository = mock(RunSummaryRepository.class);
		RunRecordRepository runRecordRepository = mock(RunRecordRepository.class);
		StepRecordRepository stepRecordRepository = mock(StepRecordRepository.class);
		ArtifactRecordRepository artifactRecordRepository = mock(ArtifactRecordRepository.class);
		AttemptLinkRepository attemptLinkRepository = mock(AttemptLinkRepository.class);
		CheckpointAnchorRepository checkpointAnchorRepository = mock(CheckpointAnchorRepository.class);
		LogCheckpointRepository logCheckpointRepository = mock(LogCheckpointRepository.class);
		TriggerEventRepository triggerEventRepository = mock(TriggerEventRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);

		JpaRunSummaryRegistry registry = new JpaRunSummaryRegistry(
				runSummaryRepository,
				runRecordRepository,
				stepRecordRepository,
				artifactRecordRepository,
				attemptLinkRepository,
				checkpointAnchorRepository,
				logCheckpointRepository,
				triggerEventRepository,
				pkAllocator,
				100,
				"controlplane-test"
		);

		RunRecord runRecord = new RunRecord();
		runRecord.setRunRecordPk(41L);
		runRecord.setRunRecordId("rr-160");
		runRecord.setJobExecutionId(160L);
		when(runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(160L)).thenReturn(Optional.of(runRecord));

		StepRecord stepRecord = new StepRecord();
		stepRecord.setStepRecordPk(7L);
		stepRecord.setStepRecordId("sr-1");
		stepRecord.setRunRecordPk(41L);
		stepRecord.setStepName("extract");
		stepRecord.setStepStatus("COMPLETED");
		when(stepRecordRepository.findByRunRecordPkOrderByStartedAtAscStepRecordIdAsc(41L)).thenReturn(List.of(stepRecord));

		ArtifactRecord artifactRecord = new ArtifactRecord();
		artifactRecord.setArtifactRecordPk(8L);
		artifactRecord.setArtifactRecordId("ar-1");
		artifactRecord.setRunRecordPk(41L);
		artifactRecord.setStepRecordId("sr-1");
		artifactRecord.setArtifactRole("RUN_LOG");
		artifactRecord.setArtifactPath("C:/logs/run.log");
		when(artifactRecordRepository.findByRunRecordPkOrderByCreatedAtDescArtifactRecordIdDesc(41L)).thenReturn(List.of(artifactRecord));
		when(artifactRecordRepository.findByStepRecordIdOrderByCreatedAtDescArtifactRecordIdDesc("sr-1")).thenReturn(List.of(artifactRecord));
		when(runRecordRepository.findById(41L)).thenReturn(Optional.of(runRecord));

		List<RunStepRecordView> stepViews = registry.listStepRecordsByJobExecutionId(160L, 10);
		List<RunArtifactRecordView> artifactByRun = registry.listArtifactRecordsByJobExecutionId(160L, 10);
		List<RunArtifactRecordView> artifactByStep = registry.listArtifactRecordsByStepRecordId("sr-1", 10);

		assertEquals(1, stepViews.size());
		assertEquals("rr-160", stepViews.get(0).runRecordId());
		assertEquals(1, artifactByRun.size());
		assertEquals("rr-160", artifactByRun.get(0).runRecordId());
		assertEquals(1, artifactByStep.size());
		assertEquals("rr-160", artifactByStep.get(0).runRecordId());
	}

	@Test
	void returnsEmptyRecoveryWhenRunRecordMissing() {
		RunSummaryRepository runSummaryRepository = mock(RunSummaryRepository.class);
		RunRecordRepository runRecordRepository = mock(RunRecordRepository.class);
		StepRecordRepository stepRecordRepository = mock(StepRecordRepository.class);
		ArtifactRecordRepository artifactRecordRepository = mock(ArtifactRecordRepository.class);
		AttemptLinkRepository attemptLinkRepository = mock(AttemptLinkRepository.class);
		CheckpointAnchorRepository checkpointAnchorRepository = mock(CheckpointAnchorRepository.class);
		LogCheckpointRepository logCheckpointRepository = mock(LogCheckpointRepository.class);
		TriggerEventRepository triggerEventRepository = mock(TriggerEventRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);

		JpaRunSummaryRegistry registry = new JpaRunSummaryRegistry(
				runSummaryRepository,
				runRecordRepository,
				stepRecordRepository,
				artifactRecordRepository,
				attemptLinkRepository,
				checkpointAnchorRepository,
				logCheckpointRepository,
				triggerEventRepository,
				pkAllocator,
				100,
				"controlplane-test"
		);

		when(runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(anyLong())).thenReturn(Optional.empty());
		when(runSummaryRepository.findByJobExecutionId(999L)).thenReturn(Optional.empty());

		assertTrue(registry.findRecoveryByJobExecutionId(999L).isEmpty());
		assertFalse(registry.findByJobExecutionId(999L).isPresent());
		verify(runSummaryRepository, never()).save(any());
	}

	@Test
	void upsertStepSnapshotsWritesStepRecordsWithDeterministicIds() {
		RunSummaryRepository runSummaryRepository = mock(RunSummaryRepository.class);
		RunRecordRepository runRecordRepository = mock(RunRecordRepository.class);
		StepRecordRepository stepRecordRepository = mock(StepRecordRepository.class);
		ArtifactRecordRepository artifactRecordRepository = mock(ArtifactRecordRepository.class);
		AttemptLinkRepository attemptLinkRepository = mock(AttemptLinkRepository.class);
		CheckpointAnchorRepository checkpointAnchorRepository = mock(CheckpointAnchorRepository.class);
		LogCheckpointRepository logCheckpointRepository = mock(LogCheckpointRepository.class);
		TriggerEventRepository triggerEventRepository = mock(TriggerEventRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);

		JpaRunSummaryRegistry registry = new JpaRunSummaryRegistry(
				runSummaryRepository,
				runRecordRepository,
				stepRecordRepository,
				artifactRecordRepository,
				attemptLinkRepository,
				checkpointAnchorRepository,
				logCheckpointRepository,
				triggerEventRepository,
				pkAllocator,
				100,
				"controlplane-test"
		);

		RunRecord runRecord = new RunRecord();
		runRecord.setRunRecordPk(41L);
		runRecord.setRunRecordId("rr-160");
		when(runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(160L)).thenReturn(Optional.of(runRecord));
		when(stepRecordRepository.findByStepRecordId("sr-41-1001")).thenReturn(Optional.empty());
		when(pkAllocator.nextPk("controlplane_step_record_pk")).thenReturn(77L);

		StepExecution stepExecution = mock(StepExecution.class);
		when(stepExecution.getId()).thenReturn(1001L);
		when(stepExecution.getStepName()).thenReturn("extract");
		when(stepExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
		when(stepExecution.getStartTime()).thenReturn(LocalDateTime.of(2026, 8, 19, 8, 0));
		when(stepExecution.getEndTime()).thenReturn(LocalDateTime.of(2026, 8, 19, 8, 2));
		when(stepExecution.getReadCount()).thenReturn(10L);
		when(stepExecution.getWriteCount()).thenReturn(9L);
		when(stepExecution.getFilterCount()).thenReturn(1L);
		when(stepExecution.getRollbackCount()).thenReturn(0L);

		registry.upsertStepSnapshots(160L, List.of(stepExecution));

		ArgumentCaptor<StepRecord> stepRecordCaptor = ArgumentCaptor.forClass(StepRecord.class);
		verify(stepRecordRepository).save(stepRecordCaptor.capture());
		StepRecord saved = stepRecordCaptor.getValue();
		assertEquals(77L, saved.getStepRecordPk());
		assertEquals("sr-41-1001", saved.getStepRecordId());
		assertEquals("extract", saved.getStepName());
		assertEquals("COMPLETED", saved.getStepStatus());
		assertEquals(120L, saved.getDurationSeconds());
	}

	@Test
	void upsertAndReadLogCheckpointUsesNormalizedPathAsKey() {
		RunSummaryRepository runSummaryRepository = mock(RunSummaryRepository.class);
		RunRecordRepository runRecordRepository = mock(RunRecordRepository.class);
		StepRecordRepository stepRecordRepository = mock(StepRecordRepository.class);
		ArtifactRecordRepository artifactRecordRepository = mock(ArtifactRecordRepository.class);
		AttemptLinkRepository attemptLinkRepository = mock(AttemptLinkRepository.class);
		CheckpointAnchorRepository checkpointAnchorRepository = mock(CheckpointAnchorRepository.class);
		LogCheckpointRepository logCheckpointRepository = mock(LogCheckpointRepository.class);
		TriggerEventRepository triggerEventRepository = mock(TriggerEventRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);

		JpaRunSummaryRegistry registry = new JpaRunSummaryRegistry(
				runSummaryRepository,
				runRecordRepository,
				stepRecordRepository,
				artifactRecordRepository,
				attemptLinkRepository,
				checkpointAnchorRepository,
				logCheckpointRepository,
				triggerEventRepository,
				pkAllocator,
				100,
				"controlplane-test"
		);

		when(logCheckpointRepository.findById("C:/logs/run.log")).thenReturn(Optional.empty());
		registry.upsertLogCheckpoint("  C:/logs/run.log  ", 512L, 1024L, 12345L);

		ArgumentCaptor<LogCheckpoint> checkpointCaptor = ArgumentCaptor.forClass(LogCheckpoint.class);
		verify(logCheckpointRepository).save(checkpointCaptor.capture());
		LogCheckpoint saved = checkpointCaptor.getValue();
		assertEquals("C:/logs/run.log", saved.getLogPath());
		assertEquals(512L, saved.getLastOffsetBytes());
		assertEquals(1024L, saved.getFileSizeAtCheckpoint());
		assertEquals(12345L, saved.getFileMtimeAtCheckpoint());

		when(logCheckpointRepository.findById("C:/logs/run.log")).thenReturn(Optional.of(saved));
		Optional<RunSummaryRegistry.LogReadCheckpoint> loaded = registry.findLogCheckpoint("C:/logs/run.log");
		assertTrue(loaded.isPresent());
		assertEquals(512L, loaded.get().offsetBytes());
	}

	@Test
	void recoveryReadsAttemptLinkAndCheckpointAnchorsWhenPresent() {
		RunSummaryRepository runSummaryRepository = mock(RunSummaryRepository.class);
		RunRecordRepository runRecordRepository = mock(RunRecordRepository.class);
		StepRecordRepository stepRecordRepository = mock(StepRecordRepository.class);
		ArtifactRecordRepository artifactRecordRepository = mock(ArtifactRecordRepository.class);
		AttemptLinkRepository attemptLinkRepository = mock(AttemptLinkRepository.class);
		CheckpointAnchorRepository checkpointAnchorRepository = mock(CheckpointAnchorRepository.class);
		LogCheckpointRepository logCheckpointRepository = mock(LogCheckpointRepository.class);
		TriggerEventRepository triggerEventRepository = mock(TriggerEventRepository.class);
		JpaControlPlanePkAllocator pkAllocator = mock(JpaControlPlanePkAllocator.class);

		JpaRunSummaryRegistry registry = new JpaRunSummaryRegistry(
				runSummaryRepository,
				runRecordRepository,
				stepRecordRepository,
				artifactRecordRepository,
				attemptLinkRepository,
				checkpointAnchorRepository,
				logCheckpointRepository,
				triggerEventRepository,
				pkAllocator,
				100,
				"controlplane-test"
		);

		RunRecord currentRun = new RunRecord();
		currentRun.setRunRecordPk(41L);
		currentRun.setRunRecordId("rr-2402");
		currentRun.setJobExecutionId(2402L);
		RunRecord priorRun = new RunRecord();
		priorRun.setRunRecordPk(40L);
		priorRun.setRunRecordId("rr-2401");
		priorRun.setJobExecutionId(2401L);
		AttemptLink attemptLink = new AttemptLink();
		attemptLink.setAttemptLinkId("al-41");
		attemptLink.setRunRecordPk(41L);
		attemptLink.setPriorRunRecordPk(40L);
		attemptLink.setLinkKind("RERUN");
		CheckpointAnchor checkpointAnchor = new CheckpointAnchor();
		checkpointAnchor.setCheckpointAnchorId("ca-log-41");
		checkpointAnchor.setRunRecordPk(41L);
		checkpointAnchor.setAnchorKind("RUN_LOG");
		checkpointAnchor.setAnchorRef("C:/logs/run.log");

		when(runRecordRepository.findFirstByJobExecutionIdOrderByStartedAtDescRunRecordPkDesc(2402L)).thenReturn(Optional.of(currentRun));
		when(attemptLinkRepository.findByRunRecordPkOrderByCreatedAtDescAttemptLinkPkDesc(41L)).thenReturn(List.of(attemptLink));
		when(checkpointAnchorRepository.findByRunRecordPkOrderByCreatedAtDescCheckpointAnchorPkDesc(41L)).thenReturn(List.of(checkpointAnchor));
		when(runRecordRepository.findById(40L)).thenReturn(Optional.of(priorRun));

		RunRecoveryView recovery = registry.findRecoveryByJobExecutionId(2402L).orElseThrow();

		assertEquals("rr-2402", recovery.runRecordId());
		assertEquals("al-41", recovery.attemptLinkId());
		assertEquals("RERUN", recovery.linkKind());
		assertEquals("rr-2401", recovery.priorRunRecordId());
		assertEquals(2401L, recovery.priorJobExecutionId());
		assertEquals(1, recovery.checkpointAnchors().size());
		assertEquals("ca-log-41", recovery.checkpointAnchors().get(0).checkpointAnchorId());
	}
}







