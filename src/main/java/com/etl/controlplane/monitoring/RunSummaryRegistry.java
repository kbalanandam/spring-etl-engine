package com.etl.controlplane.monitoring;

import java.util.List;
import java.util.Optional;

/**
 * Persistence abstraction for projected run summaries.
 */
public interface RunSummaryRegistry {

	record LogReadCheckpoint(String logPath, long offsetBytes, long fileSizeBytes, long fileLastModifiedMillis) {
	}

	void upsert(RunSummaryView runSummary);

	default void upsertStepSnapshots(long jobExecutionId, java.util.Collection<? extends org.springframework.batch.core.StepExecution> stepExecutions) {
		// Optional hook for registries that can persist step projections immediately at job completion.
	}

	default Optional<LogReadCheckpoint> findLogCheckpoint(String logPath) {
		return Optional.empty();
	}

	default void upsertLogCheckpoint(String logPath, long offsetBytes, long fileSizeBytes, long fileLastModifiedMillis) {
		// Optional hook for registries that can durably persist incremental log replay progress.
	}

	List<RunSummaryView> latestRuns(int limit);

	Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId);

	Optional<RunRecoveryView> findRecoveryByJobExecutionId(long jobExecutionId);

	List<RunStepRecordView> listStepRecordsByJobExecutionId(long jobExecutionId, int limit);

	List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit);

	List<RunArtifactRecordView> listArtifactRecordsByStepRecordId(String stepRecordId, int limit);
}

