package com.etl.controlplane.monitoring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback registry for run summaries.
 */
@Component
@ConditionalOnProperty(name = "controlplane.runs.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryRunSummaryRegistry implements RunSummaryRegistry {

	private final ConcurrentHashMap<Long, RunSummaryView> runsByJobExecutionId = new ConcurrentHashMap<>();

	@Override
	public void upsert(RunSummaryView runSummary) {
		Long jobExecutionId = runSummary.jobExecutionId();
		if (jobExecutionId == null) {
			return;
		}
		runsByJobExecutionId.put(jobExecutionId, runSummary);
	}

	@Override
	public List<RunSummaryView> latestRuns(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		List<RunSummaryView> runs = new ArrayList<>(runsByJobExecutionId.values());
		runs.sort(Comparator
				.comparing(RunSummaryView::startTime, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(RunSummaryView::jobExecutionId, Comparator.nullsLast(Comparator.reverseOrder())));
		return runs.size() <= limit ? runs : runs.subList(0, limit);
	}

	@Override
	public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
		return Optional.ofNullable(runsByJobExecutionId.get(jobExecutionId));
	}

	@Override
	public Optional<RunRecoveryView> findRecoveryByJobExecutionId(long jobExecutionId) {
		return Optional.empty();
	}

	@Override
	public List<RunStepRecordView> listStepRecordsByJobExecutionId(long jobExecutionId, int limit) {
		return List.of();
	}

	@Override
	public List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit) {
		return List.of();
	}

	@Override
	public List<RunArtifactRecordView> listArtifactRecordsByStepRecordId(String stepRecordId, int limit) {
		return List.of();
	}
}



