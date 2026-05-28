package com.etl.controlplane.monitoring;

import java.util.List;
import java.util.Optional;

/**
 * Persistence abstraction for projected run summaries.
 */
public interface RunSummaryRegistry {

	void upsert(RunSummaryView runSummary);

	List<RunSummaryView> latestRuns(int limit);

	Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId);
}

