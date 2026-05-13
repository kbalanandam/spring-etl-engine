package com.etl.runtime.job;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.item.ExecutionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Helper methods that project scenario hierarchy metadata into log-friendly evidence.
 *
 * <p>This class is the glue between runtime descriptors and emitted log events. It copies
 * subflow/link metadata into step execution context, resolves inbound links for step/subflow
 * evidence, and derives observed subflow status from executed steps plus control metadata.</p>
 */
public final class JobHierarchyLoggingSupport {

	public static final String STEP_SUB_FLOW_NAME_KEY = "scenarioStepSubFlowName";
	public static final String STEP_SUB_FLOW_ORDER_KEY = "scenarioStepSubFlowOrder";
	public static final String STEP_DEPENDS_ON_SUB_FLOWS_KEY = "scenarioStepDependsOnSubFlows";
	public static final String STEP_CONSUMES_HANDOFFS_KEY = "scenarioStepConsumesHandoffs";
	public static final String STEP_PRODUCES_HANDOFFS_KEY = "scenarioStepProducesHandoffs";
	public static final String STEP_UPSTREAM_STEPS_KEY = "scenarioStepUpstreamSteps";
	public static final String STEP_LINK_TYPES_KEY = "scenarioStepLinkTypes";
	public static final String STEP_LINK_CONTROL_SUMMARY_KEY = "scenarioStepLinkControlSummary";
	public static final String STEP_SUMMARY_KEY = "scenarioStepSummary";

	private JobHierarchyLoggingSupport() {
	}

	public static void populateStepExecutionContext(ExecutionContext executionContext,
	                                              JobRuntimeDescriptor descriptor,
	                                              JobStepDescriptor stepDescriptor) {
		if (executionContext == null || descriptor == null || stepDescriptor == null) {
			return;
		}
		JobSubFlowDescriptor subFlowDescriptor = subFlowForStep(descriptor, stepDescriptor.stepName());
		List<JobStepLinkDescriptor> inboundLinks = inboundLinks(descriptor, stepDescriptor.stepName());
		executionContext.putString(STEP_SUB_FLOW_NAME_KEY, subFlowDescriptor == null ? "" : subFlowDescriptor.subFlowName());
		executionContext.putInt(STEP_SUB_FLOW_ORDER_KEY, subFlowDescriptor == null ? stepDescriptor.stepOrder() : subFlowDescriptor.subFlowOrder());
		executionContext.putString(STEP_DEPENDS_ON_SUB_FLOWS_KEY, formatList(subFlowDescriptor == null ? List.of() : subFlowDescriptor.dependsOnSubFlowNames()));
		executionContext.putString(STEP_CONSUMES_HANDOFFS_KEY, formatList(subFlowDescriptor == null ? List.of() : subFlowDescriptor.consumesHandoffAliases()));
		executionContext.putString(STEP_PRODUCES_HANDOFFS_KEY, formatList(subFlowDescriptor == null ? List.of() : subFlowDescriptor.producesHandoffAliases()));
		executionContext.putString(STEP_UPSTREAM_STEPS_KEY, formatList(inboundLinks.stream().map(JobStepLinkDescriptor::fromStepName).toList()));
		executionContext.putString(STEP_LINK_TYPES_KEY, formatList(inboundLinks.stream().map(link -> link.linkType().name()).toList()));
		executionContext.putString(STEP_LINK_CONTROL_SUMMARY_KEY, formatList(inboundLinks.stream().map(link -> link.control().summary()).toList()));
		executionContext.putString(STEP_SUMMARY_KEY, stepDescriptor.flowSummary());
	}

	public static JobSubFlowDescriptor subFlowForStep(JobRuntimeDescriptor descriptor, String stepName) {
		if (descriptor == null || stepName == null || stepName.isBlank()) {
			return null;
		}
		for (JobSubFlowDescriptor subFlow : descriptor.subFlows()) {
			if (subFlow.stepNames().contains(stepName)) {
				return subFlow;
			}
		}
		return null;
	}

	public static List<JobStepLinkDescriptor> inboundLinks(JobRuntimeDescriptor descriptor, String stepName) {
		if (descriptor == null || stepName == null || stepName.isBlank()) {
			return List.of();
		}
		return descriptor.stepLinks().stream()
				.filter(link -> stepName.equals(link.toStepName()))
				.toList();
	}

	public static List<JobStepLinkDescriptor> inboundLinks(JobRuntimeDescriptor descriptor, JobSubFlowDescriptor subFlowDescriptor) {
		if (descriptor == null || subFlowDescriptor == null) {
			return List.of();
		}
		Set<String> stepNames = new LinkedHashSet<>(subFlowDescriptor.stepNames());
		return descriptor.stepLinks().stream()
				.filter(link -> stepNames.contains(link.toStepName()))
				.toList();
	}

	public static String formatList(List<String> values) {
		if (values == null || values.isEmpty()) {
			return "none";
		}
		LinkedHashSet<String> normalized = new LinkedHashSet<>();
		for (String value : values) {
			if (value == null) {
				continue;
			}
			String trimmed = value.trim();
			if (!trimmed.isBlank()) {
				normalized.add(trimmed);
			}
		}
		return normalized.isEmpty() ? "none" : String.join(",", normalized);
	}

	public static List<SubFlowStatusEvidence> evaluateSubFlowEvidence(JobRuntimeDescriptor descriptor,
	                                                                Collection<StepExecution> stepExecutions,
	                                                                BatchStatus jobStatus) {
		if (descriptor == null || descriptor.subFlows().isEmpty()) {
			return List.of();
		}
		Map<String, StepExecution> stepExecutionByName = new LinkedHashMap<>();
		if (stepExecutions != null) {
			for (StepExecution stepExecution : stepExecutions) {
				if (stepExecution != null) {
					stepExecutionByName.put(stepExecution.getStepName(), stepExecution);
				}
			}
		}
		Map<String, JobSubFlowExecutionStatus> subFlowStatuses = new LinkedHashMap<>();
		List<SubFlowStatusEvidence> evidence = new ArrayList<>();
		for (JobSubFlowDescriptor subFlowDescriptor : descriptor.subFlows()) {
			JobSubFlowExecutionStatus observedStatus = resolveObservedStatus(subFlowDescriptor, stepExecutionByName, descriptor, subFlowStatuses, jobStatus);
			String blockedReason = observedStatus == JobSubFlowExecutionStatus.BLOCKED
					? buildBlockedReason(descriptor, subFlowDescriptor, subFlowStatuses)
					: "";
			subFlowStatuses.put(subFlowDescriptor.subFlowName(), observedStatus);
			evidence.add(new SubFlowStatusEvidence(subFlowDescriptor, observedStatus, blockedReason));
		}
		return List.copyOf(evidence);
	}

	public static String stringValue(ExecutionContext executionContext, String key) {
		if (executionContext == null || key == null || !executionContext.containsKey(key)) {
			return "";
		}
		return executionContext.getString(key, "");
	}

	public static int intValue(ExecutionContext executionContext, String key, int defaultValue) {
		return executionContext == null || key == null || !executionContext.containsKey(key)
				? defaultValue
				: executionContext.getInt(key, defaultValue);
	}

	private static JobSubFlowExecutionStatus resolveObservedStatus(JobSubFlowDescriptor subFlowDescriptor,
	                                                                  Map<String, StepExecution> stepExecutionByName,
	                                                                  JobRuntimeDescriptor descriptor,
	                                                                  Map<String, JobSubFlowExecutionStatus> knownStatuses,
	                                                                  BatchStatus jobStatus) {
		List<StepExecution> stepExecutions = subFlowDescriptor.stepNames().stream()
				.map(stepExecutionByName::get)
				.filter(Objects::nonNull)
				.toList();
		boolean anyFailed = stepExecutions.stream().anyMatch(JobHierarchyLoggingSupport::isFailed);
		if (anyFailed) {
			return JobSubFlowExecutionStatus.FAILED;
		}
		boolean allStepExecutionsObserved = !stepExecutions.isEmpty()
				&& stepExecutions.size() == subFlowDescriptor.stepNames().size();
		boolean allCompleted = allStepExecutionsObserved
				&& (jobStatus == BatchStatus.COMPLETED
					|| stepExecutions.stream().allMatch(JobHierarchyLoggingSupport::isCompleted));
		if (allCompleted) {
			return JobSubFlowExecutionStatus.COMPLETED;
		}
		boolean anyStarted = stepExecutions.stream().anyMatch(JobHierarchyLoggingSupport::isStarted);
		if (anyStarted && jobStatus == BatchStatus.STARTED) {
			return JobSubFlowExecutionStatus.RUNNING;
		}
		String blockedReason = buildBlockedReason(descriptor, subFlowDescriptor, knownStatuses);
		if (!blockedReason.isBlank()) {
			return JobSubFlowExecutionStatus.BLOCKED;
		}
		if (isReadyToStart(descriptor, subFlowDescriptor, knownStatuses)) {
			return JobSubFlowExecutionStatus.READY;
		}
		return subFlowDescriptor.initialStatus();
	}

	private static boolean isReadyToStart(JobRuntimeDescriptor descriptor,
	                                   JobSubFlowDescriptor subFlowDescriptor,
	                                   Map<String, JobSubFlowExecutionStatus> knownStatuses) {
		for (String dependencyName : subFlowDescriptor.dependsOnSubFlowNames()) {
			JobSubFlowExecutionStatus dependencyStatus = knownStatuses.get(dependencyName);
			if (dependencyStatus == null) {
				return false;
			}
			if (!subFlowDescriptor.control().startAfterStatuses().isEmpty()
					&& !subFlowDescriptor.control().startAfterStatuses().contains(dependencyStatus)) {
				return false;
			}
		}
		for (JobStepLinkDescriptor inboundLink : inboundLinks(descriptor, subFlowDescriptor)) {
			JobSubFlowDescriptor upstreamSubFlow = subFlowForStep(descriptor, inboundLink.fromStepName());
			JobSubFlowExecutionStatus upstreamStatus = upstreamSubFlow == null ? null : knownStatuses.get(upstreamSubFlow.subFlowName());
			if (upstreamStatus == null) {
				return false;
			}
			if (!inboundLink.control().requiredUpstreamStatuses().isEmpty()
					&& !inboundLink.control().requiredUpstreamStatuses().contains(upstreamStatus)) {
				return false;
			}
		}
		return true;
	}

	private static String buildBlockedReason(JobRuntimeDescriptor descriptor,
	                                       JobSubFlowDescriptor subFlowDescriptor,
	                                       Map<String, JobSubFlowExecutionStatus> knownStatuses) {
		LinkedHashSet<String> reasons = new LinkedHashSet<>();
		for (String dependencyName : subFlowDescriptor.dependsOnSubFlowNames()) {
			JobSubFlowExecutionStatus dependencyStatus = knownStatuses.get(dependencyName);
			if (dependencyStatus == null) {
				continue;
			}
			if (subFlowDescriptor.control().blocksOn(dependencyStatus)) {
				reasons.add("upstreamSubFlow=" + dependencyName + " status=" + dependencyStatus
						+ " blockOnStatuses=" + subFlowDescriptor.control().blockOnStatuses());
			} else if (!subFlowDescriptor.control().startAfterStatuses().isEmpty()
					&& !subFlowDescriptor.control().startAfterStatuses().contains(dependencyStatus)) {
				reasons.add("upstreamSubFlow=" + dependencyName + " status=" + dependencyStatus
						+ " requiredStartAfterStatuses=" + subFlowDescriptor.control().startAfterStatuses());
			}
		}
		for (JobStepLinkDescriptor inboundLink : inboundLinks(descriptor, subFlowDescriptor)) {
			JobSubFlowDescriptor upstreamSubFlow = subFlowForStep(descriptor, inboundLink.fromStepName());
			JobSubFlowExecutionStatus upstreamStatus = upstreamSubFlow == null ? null : knownStatuses.get(upstreamSubFlow.subFlowName());
			if (upstreamStatus == null) {
				continue;
			}
			if (inboundLink.control().blocksOnUpstreamStatus(upstreamStatus)) {
				reasons.add("upstreamLink=" + inboundLink.fromStepName() + "->" + inboundLink.toStepName()
						+ " upstreamStatus=" + upstreamStatus
						+ " blockingStatuses=" + inboundLink.control().blockingUpstreamStatuses()
						+ appendHandoffContext(inboundLink));
			} else if (!inboundLink.control().requiredUpstreamStatuses().isEmpty()
					&& !inboundLink.control().requiredUpstreamStatuses().contains(upstreamStatus)) {
				reasons.add("upstreamLink=" + inboundLink.fromStepName() + "->" + inboundLink.toStepName()
						+ " upstreamStatus=" + upstreamStatus
						+ " requiredStatuses=" + inboundLink.control().requiredUpstreamStatuses()
						+ appendHandoffContext(inboundLink));
			}
		}
		return reasons.isEmpty() ? "" : String.join(" | ", reasons);
	}

	private static String appendHandoffContext(JobStepLinkDescriptor inboundLink) {
		if (!inboundLink.control().requiresHandoffReady()) {
			return "";
		}
		List<String> aliases = new ArrayList<>();
		if (inboundLink.outputAlias() != null && !inboundLink.outputAlias().isBlank()) {
			aliases.add(inboundLink.outputAlias());
		}
		if (inboundLink.inputAlias() != null && !inboundLink.inputAlias().isBlank()) {
			aliases.add(inboundLink.inputAlias());
		}
		return aliases.isEmpty() ? "" : " handoffAliases=" + formatList(aliases);
	}

	private static boolean isCompleted(StepExecution stepExecution) {
		return stepExecution.getStatus() == BatchStatus.COMPLETED;
	}

	private static boolean isFailed(StepExecution stepExecution) {
		BatchStatus status = stepExecution.getStatus();
		return status == BatchStatus.FAILED || status == BatchStatus.STOPPED || status == BatchStatus.UNKNOWN;
	}

	private static boolean isStarted(StepExecution stepExecution) {
		BatchStatus status = stepExecution.getStatus();
		return status == BatchStatus.STARTED || status == BatchStatus.STARTING || status == BatchStatus.STOPPING;
	}

	/**
	 * Observed runtime evidence for one subflow after comparing descriptor intent with step results.
	 */
	public record SubFlowStatusEvidence(
			JobSubFlowDescriptor subFlowDescriptor,
			JobSubFlowExecutionStatus observedStatus,
			String blockedReason
	) {
	}
}

