package com.etl.runtime.scenario;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Self-explanatory descriptor for one complete selected scenario run.
 */
public record ScenarioRuntimeDescriptor(
		String scenarioName,
		String displayName,
		String mainFlowName,
		String subFlowName,
		boolean implicitSubFlow,
		ScenarioRecoveryPolicy recoveryPolicy,
		List<ScenarioSubFlowDescriptor> subFlows,
		ScenarioMainFlowContextDescriptor mainFlowContext,
		String flowSummary,
		String jobConfigPath,
		ScenarioRunMode runMode,
		ScenarioConfigPaths configPaths,
		List<ScenarioStepDescriptor> steps,
		Map<String, ScenarioStepDescriptor> stepsByName,
		List<ScenarioStepLinkDescriptor> stepLinks,
		ScenarioValidationSummary validationSummary
) {

	public ScenarioRuntimeDescriptor {
		scenarioName = requireNonBlank(scenarioName, "scenarioName");
		displayName = displayName == null || displayName.isBlank() ? scenarioName : displayName.trim();
		mainFlowName = mainFlowName == null || mainFlowName.isBlank() ? defaultMainFlowName(scenarioName) : mainFlowName.trim();
		subFlowName = subFlowName == null || subFlowName.isBlank() ? "default-subflow" : subFlowName.trim();
		recoveryPolicy = recoveryPolicy == null ? ScenarioRecoveryPolicy.RERUN_FROM_START : recoveryPolicy;
		jobConfigPath = jobConfigPath == null ? "" : jobConfigPath.trim();
		Objects.requireNonNull(runMode, "runMode must not be null.");
		Objects.requireNonNull(configPaths, "configPaths must not be null.");
		Objects.requireNonNull(validationSummary, "validationSummary must not be null.");
		steps = steps == null ? List.of() : List.copyOf(steps);
		subFlows = subFlows == null ? synthesizeDefaultSubFlows(steps) : List.copyOf(subFlows);
		stepLinks = stepLinks == null ? List.of() : List.copyOf(stepLinks);
		mainFlowContext = mainFlowContext == null ? ScenarioMainFlowContextDescriptor.defaultFor(subFlows, steps, stepLinks, recoveryPolicy) : mainFlowContext;
		stepsByName = Collections.unmodifiableMap(buildStepsByName(stepsByName, steps));
		flowSummary = flowSummary == null || flowSummary.isBlank() ? buildFlowSummary(steps) : flowSummary.trim();
	}

	public int stepCount() {
		return steps.size();
	}

	public boolean hasStep(String stepName) {
		return stepName != null && stepsByName.containsKey(stepName);
	}

	public ScenarioStepDescriptor firstStep() {
		return steps.isEmpty() ? null : steps.get(0);
	}

	public ScenarioStepDescriptor finalStep() {
		return steps.isEmpty() ? null : steps.get(steps.size() - 1);
	}

	public List<String> orderedStepNames() {
		List<String> names = new ArrayList<>();
		for (ScenarioStepDescriptor step : steps) {
			names.add(step.stepName());
		}
		return List.copyOf(names);
	}

	public boolean rerunsWholeScenarioOnFailure() {
		return recoveryPolicy == ScenarioRecoveryPolicy.RERUN_FROM_START;
	}

	public boolean supportsCrossSubFlowHandshake() {
		return mainFlowContext.supportsCrossSubFlowHandshake();
	}

	public int subFlowCount() {
		return subFlows.size();
	}

	public ScenarioSubFlowDescriptor firstSubFlow() {
		return subFlows.isEmpty() ? null : subFlows.get(0);
	}

	public ScenarioSubFlowDescriptor finalSubFlow() {
		return subFlows.isEmpty() ? null : subFlows.get(subFlows.size() - 1);
	}

	private static String buildFlowSummary(List<ScenarioStepDescriptor> steps) {
		if (steps == null || steps.isEmpty()) {
			return "No scenario steps resolved.";
		}
		return steps.stream()
				.map(step -> step.stepName() + ":" + step.sourceName() + "->" + step.targetName())
				.reduce((left, right) -> left + " | " + right)
				.orElse("No scenario steps resolved.");
	}

	private static Map<String, ScenarioStepDescriptor> buildStepsByName(Map<String, ScenarioStepDescriptor> provided,
	                                                                  List<ScenarioStepDescriptor> steps) {
		if (provided != null && !provided.isEmpty()) {
			return validateUniqueStepNames(provided);
		}
		Map<String, ScenarioStepDescriptor> indexed = new LinkedHashMap<>();
		for (ScenarioStepDescriptor step : steps) {
			ScenarioStepDescriptor previous = indexed.putIfAbsent(step.stepName(), step);
			if (previous != null) {
				throw new IllegalArgumentException("Duplicate scenario step name: " + step.stepName());
			}
		}
		return indexed;
	}

	private static Map<String, ScenarioStepDescriptor> validateUniqueStepNames(Map<String, ScenarioStepDescriptor> provided) {
		Map<String, ScenarioStepDescriptor> copy = new LinkedHashMap<>();
		for (Map.Entry<String, ScenarioStepDescriptor> entry : provided.entrySet()) {
			String stepName = requireNonBlank(entry.getKey(), "stepsByName key");
			ScenarioStepDescriptor step = Objects.requireNonNull(entry.getValue(), "stepsByName value must not be null.");
			ScenarioStepDescriptor previous = copy.putIfAbsent(stepName, step);
			if (previous != null) {
				throw new IllegalArgumentException("Duplicate scenario step name: " + stepName);
			}
		}
		return copy;
	}

	private static List<ScenarioSubFlowDescriptor> synthesizeDefaultSubFlows(List<ScenarioStepDescriptor> steps) {
		if (steps == null || steps.isEmpty()) {
			return List.of();
		}
		List<ScenarioSubFlowDescriptor> synthesized = new ArrayList<>();
		ScenarioSubFlowDescriptor previous = null;
		for (int i = 0; i < steps.size(); i++) {
			ScenarioStepDescriptor step = steps.get(i);
			String subFlowName = step.stepName() + "-subflow";
			boolean requiresHandoffReady = step.input().inputAlias() != null && !step.input().inputAlias().isBlank();
			synthesized.add(new ScenarioSubFlowDescriptor(
					subFlowName,
					i,
					List.of(step.stepName()),
					i == 0 ? ScenarioSubFlowExecutionStatus.READY : ScenarioSubFlowExecutionStatus.NOT_STARTED,
					ScenarioSubFlowControlDescriptor.defaultSequentialControl(requiresHandoffReady),
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

