package com.etl.runtime.job;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Self-explanatory descriptor for one complete selected scenario run.
 *
 * <p>This record is the top-level observability view of the selected runtime contract. It keeps
 * the scenario name, config paths, ordered steps, subflow grouping, recovery policy, and
 * validation summary together so logs and runtime diagnostics can describe one run consistently.</p>
 */
public record JobRuntimeDescriptor(
		String scenarioName,
		String displayName,
		String mainFlowName,
		String subFlowName,
		boolean implicitSubFlow,
		JobRecoveryPolicy recoveryPolicy,
		List<JobSubFlowDescriptor> subFlows,
		JobMainFlowContextDescriptor mainFlowContext,
		String flowSummary,
		String jobConfigPath,
		JobRunMode runMode,
		JobConfigPaths configPaths,
		List<JobStepDescriptor> steps,
		Map<String, JobStepDescriptor> stepsByName,
		List<JobStepLinkDescriptor> stepLinks,
		JobValidationSummary validationSummary
) {

	public JobRuntimeDescriptor {
		scenarioName = requireNonBlank(scenarioName, "scenarioName");
		displayName = displayName == null || displayName.isBlank() ? scenarioName : displayName.trim();
		mainFlowName = mainFlowName == null || mainFlowName.isBlank() ? defaultMainFlowName(scenarioName) : mainFlowName.trim();
		subFlowName = subFlowName == null || subFlowName.isBlank() ? "default-subflow" : subFlowName.trim();
		recoveryPolicy = recoveryPolicy == null ? JobRecoveryPolicy.RERUN_FROM_START : recoveryPolicy;
		jobConfigPath = jobConfigPath == null ? "" : jobConfigPath.trim();
		Objects.requireNonNull(runMode, "runMode must not be null.");
		Objects.requireNonNull(configPaths, "configPaths must not be null.");
		Objects.requireNonNull(validationSummary, "validationSummary must not be null.");
		steps = steps == null ? List.of() : List.copyOf(steps);
		subFlows = subFlows == null ? synthesizeDefaultSubFlows(steps) : List.copyOf(subFlows);
		stepLinks = stepLinks == null ? List.of() : List.copyOf(stepLinks);
		mainFlowContext = mainFlowContext == null ? JobMainFlowContextDescriptor.defaultFor(subFlows, steps, stepLinks, recoveryPolicy) : mainFlowContext;
		stepsByName = Collections.unmodifiableMap(buildStepsByName(stepsByName, steps));
		flowSummary = flowSummary == null || flowSummary.isBlank() ? buildFlowSummary(steps) : flowSummary.trim();
	}

	public int stepCount() {
		return steps.size();
	}

	public boolean hasStep(String stepName) {
		return stepName != null && stepsByName.containsKey(stepName);
	}

	public JobStepDescriptor firstStep() {
		return steps.isEmpty() ? null : steps.get(0);
	}

	public JobStepDescriptor finalStep() {
		return steps.isEmpty() ? null : steps.get(steps.size() - 1);
	}

	public List<String> orderedStepNames() {
		List<String> names = new ArrayList<>();
		for (JobStepDescriptor step : steps) {
			names.add(step.stepName());
		}
		return List.copyOf(names);
	}

	public boolean rerunsWholeScenarioOnFailure() {
		return recoveryPolicy == JobRecoveryPolicy.RERUN_FROM_START;
	}

	public boolean supportsCrossSubFlowHandshake() {
		return mainFlowContext.supportsCrossSubFlowHandshake();
	}

	public int subFlowCount() {
		return subFlows.size();
	}

	public JobSubFlowDescriptor firstSubFlow() {
		return subFlows.isEmpty() ? null : subFlows.get(0);
	}

	public JobSubFlowDescriptor finalSubFlow() {
		return subFlows.isEmpty() ? null : subFlows.get(subFlows.size() - 1);
	}

	private static String buildFlowSummary(List<JobStepDescriptor> steps) {
		if (steps == null || steps.isEmpty()) {
			return "No scenario steps resolved.";
		}
		return steps.stream()
				.map(step -> step.stepName() + ":" + step.sourceName() + "->" + step.targetName())
				.reduce((left, right) -> left + " | " + right)
				.orElse("No scenario steps resolved.");
	}

	private static Map<String, JobStepDescriptor> buildStepsByName(Map<String, JobStepDescriptor> provided,
	                                                                  List<JobStepDescriptor> steps) {
		if (provided != null && !provided.isEmpty()) {
			return validateUniqueStepNames(provided);
		}
		Map<String, JobStepDescriptor> indexed = new LinkedHashMap<>();
		for (JobStepDescriptor step : steps) {
			JobStepDescriptor previous = indexed.putIfAbsent(step.stepName(), step);
			if (previous != null) {
				throw new IllegalArgumentException("Duplicate scenario step name: " + step.stepName());
			}
		}
		return indexed;
	}

	private static Map<String, JobStepDescriptor> validateUniqueStepNames(Map<String, JobStepDescriptor> provided) {
		Map<String, JobStepDescriptor> copy = new LinkedHashMap<>();
		for (Map.Entry<String, JobStepDescriptor> entry : provided.entrySet()) {
			String stepName = requireNonBlank(entry.getKey(), "stepsByName key");
			JobStepDescriptor step = Objects.requireNonNull(entry.getValue(), "stepsByName value must not be null.");
			JobStepDescriptor previous = copy.putIfAbsent(stepName, step);
			if (previous != null) {
				throw new IllegalArgumentException("Duplicate scenario step name: " + stepName);
			}
		}
		return copy;
	}

	private static List<JobSubFlowDescriptor> synthesizeDefaultSubFlows(List<JobStepDescriptor> steps) {
		if (steps == null || steps.isEmpty()) {
			return List.of();
		}
		List<JobSubFlowDescriptor> synthesized = new ArrayList<>();
		JobSubFlowDescriptor previous = null;
		for (int i = 0; i < steps.size(); i++) {
			JobStepDescriptor step = steps.get(i);
			String subFlowName = step.stepName() + "-subflow";
			boolean requiresHandoffReady = step.input().inputAlias() != null && !step.input().inputAlias().isBlank();
			synthesized.add(new JobSubFlowDescriptor(
					subFlowName,
					i,
					List.of(step.stepName()),
					i == 0 ? JobSubFlowExecutionStatus.READY : JobSubFlowExecutionStatus.NOT_STARTED,
					JobSubFlowControlDescriptor.defaultSequentialControl(requiresHandoffReady),
					previous == null ? List.of() : List.of(previous.subFlowName()),
					step.input().inputAlias() == null || step.input().inputAlias().isBlank() ? List.of() : List.of(step.input().inputAlias()),
					step.output().outputAlias() == null || step.output().outputAlias().isBlank() ? List.of() : List.of(step.output().outputAlias()),
					null
			));
			previous = synthesized.get(i);
		}
		return List.copyOf(synthesized);
	}

	private static String defaultMainFlowName(String scenarioName) {
		return scenarioName.endsWith("-flow") || scenarioName.endsWith("Flow")
				? scenarioName
				: scenarioName + "-main-flow";
	}

	private static String requireNonBlank(String value, String field) {
		Objects.requireNonNull(value, field + " must not be null.");
		if (value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank.");
		}
		return value.trim();
	}
}

