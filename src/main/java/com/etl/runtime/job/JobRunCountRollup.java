package com.etl.runtime.job;

import com.etl.runtime.FileIngestionRuntimeSupport;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Operator-oriented run-level count rollup derived from executed steps.
 *
 * <p>The current contract keeps step-level evidence as the ground truth and emits
 * run-level source / written / rejected totals as a separate operator-facing summary.
 * For multi-step jobs, intermediate handoff steps are counted separately from final
 * published output so the run summary does not become a misleading raw sum of all writes.</p>
 */
public record JobRunCountRollup(
		long sourceCount,
		long writtenCount,
		long rejectedCount,
		long handoffReadCount,
		long handoffWriteCount,
		int executedStepCount,
		String rollupMode,
		String summary
) {

	private static final String OPERATOR_ORIENTED_MODE = "operator-oriented";
	private static final String FALLBACK_MODE = "fallback-first-last";

	public JobRunCountRollup {
		rollupMode = normalize(rollupMode, OPERATOR_ORIENTED_MODE);
		summary = normalize(summary, buildSummary(sourceCount, writtenCount, rejectedCount, handoffReadCount, handoffWriteCount, executedStepCount, rollupMode));
	}

	/**
	 * Calculates the operator-facing run summary from executed steps.
	 *
	 * <p>When a runtime descriptor is available, counts are derived from explicit step roles so
	 * intermediate handoffs do not inflate the final written count. Without a descriptor, the
	 * fallback mode uses the first step as source evidence and the last step as final output evidence.</p>
	 */
	public static JobRunCountRollup calculate(JobExecution jobExecution, JobRuntimeDescriptor descriptor) {
		Collection<StepExecution> stepExecutions = jobExecution == null ? List.of() : jobExecution.getStepExecutions();
		if (descriptor != null && descriptor.steps() != null && !descriptor.steps().isEmpty()) {
			return fromDescriptor(stepExecutions, descriptor);
		}
		return fallback(stepExecutions);
	}

	/**
	 * Rollup that honors the runtime descriptor's step roles, separating source ingestion, handoff
	 * traffic, and final published output.
	 */
	private static JobRunCountRollup fromDescriptor(Collection<StepExecution> stepExecutions, JobRuntimeDescriptor descriptor) {
		Map<String, StepExecution> stepExecutionByName = new LinkedHashMap<>();
		for (StepExecution stepExecution : stepExecutions == null ? List.<StepExecution>of() : stepExecutions) {
			if (stepExecution != null && !stepExecution.getStepName().isBlank()) {
				stepExecutionByName.put(stepExecution.getStepName(), stepExecution);
			}
		}

		long sourceCount = 0;
		long writtenCount = 0;
		long rejectedCount = 0;
		long handoffReadCount = 0;
		long handoffWriteCount = 0;
		int executedStepCount = 0;

		for (JobStepDescriptor stepDescriptor : descriptor.steps()) {
			StepExecution stepExecution = stepExecutionByName.get(stepDescriptor.stepName());
			if (stepExecution == null) {
				continue;
			}
			executedStepCount++;
			rejectedCount += rejectedCount(stepExecution);
			if (stepDescriptor.input().type() == JobStepInputType.CONFIG_SOURCE) {
				sourceCount += stepExecution.getReadCount();
			} else {
				handoffReadCount += stepExecution.getReadCount();
			}
			if (stepDescriptor.emitsFinalScenarioOutput()) {
				writtenCount += stepExecution.getWriteCount();
			} else {
				handoffWriteCount += stepExecution.getWriteCount();
			}
		}

		return new JobRunCountRollup(
				sourceCount,
				writtenCount,
				rejectedCount,
				handoffReadCount,
				handoffWriteCount,
				executedStepCount,
				OPERATOR_ORIENTED_MODE,
				null
		);
	}

	/**
	 * Best-effort rollup used only when no runtime descriptor is available. The first executed step
	 * is treated as source evidence and the last executed step as final output evidence.
	 */
	private static JobRunCountRollup fallback(Collection<StepExecution> stepExecutions) {
		List<StepExecution> ordered = new ArrayList<>();
		for (StepExecution stepExecution : stepExecutions == null ? List.<StepExecution>of() : stepExecutions) {
			if (stepExecution != null) {
				ordered.add(stepExecution);
			}
		}
		ordered.sort(Comparator
				.comparing(JobRunCountRollup::startTimeOrMin)
				.thenComparing(StepExecution::getId)
				.thenComparing(stepExecution -> normalize(stepExecution.getStepName(), "unknown-step")));
		if (ordered.isEmpty()) {
			return new JobRunCountRollup(0, 0, 0, 0, 0, 0, FALLBACK_MODE, null);
		}

		long rejectedCount = ordered.stream().mapToLong(JobRunCountRollup::rejectedCount).sum();
		long sourceCount = ordered.get(0).getReadCount();
		long writtenCount = ordered.get(ordered.size() - 1).getWriteCount();
		long handoffWriteCount = ordered.size() <= 1 ? 0 : ordered.subList(0, ordered.size() - 1).stream().mapToLong(StepExecution::getWriteCount).sum();
		long handoffReadCount = ordered.size() <= 1 ? 0 : ordered.subList(1, ordered.size()).stream().mapToLong(StepExecution::getReadCount).sum();

		return new JobRunCountRollup(
				sourceCount,
				writtenCount,
				rejectedCount,
				handoffReadCount,
				handoffWriteCount,
				ordered.size(),
				FALLBACK_MODE,
				null
		);
	}

	private static long rejectedCount(StepExecution stepExecution) {
		ExecutionContext executionContext = stepExecution == null ? null : stepExecution.getExecutionContext();
		return executionContext == null ? 0 : executionContext.getInt(FileIngestionRuntimeSupport.REJECTED_COUNT_KEY, 0);
	}

	private static LocalDateTime startTimeOrMin(StepExecution stepExecution) {
		return stepExecution == null || stepExecution.getStartTime() == null ? LocalDateTime.MIN : stepExecution.getStartTime();
	}

	private static String buildSummary(long sourceCount,
	                                   long writtenCount,
	                                   long rejectedCount,
	                                   long handoffReadCount,
	                                   long handoffWriteCount,
	                                   int executedStepCount,
	                                   String rollupMode) {
		return "rollupMode=" + rollupMode
				+ ", sourceCount=" + sourceCount
				+ ", writtenCount=" + writtenCount
				+ ", rejectedCount=" + rejectedCount
				+ ", handoffReadCount=" + handoffReadCount
				+ ", handoffWriteCount=" + handoffWriteCount
				+ ", executedStepCount=" + executedStepCount;
	}

	private static String normalize(String value, String fallback) {
		return value == null || value.isBlank() ? Objects.requireNonNull(fallback) : value.trim();
	}
}


