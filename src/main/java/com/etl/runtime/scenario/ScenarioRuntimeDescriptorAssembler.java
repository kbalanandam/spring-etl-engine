package com.etl.runtime.scenario;

import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.FileSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Transitional assembler that turns the current explicit scenario contract into
 * one self-explanatory scenario runtime descriptor.
 */
public class ScenarioRuntimeDescriptorAssembler {

	public ScenarioRuntimeDescriptor assemble(String scenarioName,
	                                        String jobConfigPath,
	                                        ScenarioRunMode runMode,
	                                        ScenarioConfigPaths configPaths,
	                                        List<JobConfig.JobStepConfig> configuredSteps,
	                                        SourceWrapper sourceWrapper,
	                                        TargetWrapper targetWrapper,
	                                        ProcessorConfig processorConfig) {
		String resolvedScenarioName = requireNonBlank(scenarioName, "scenarioName");
		Objects.requireNonNull(runMode, "runMode must not be null.");
		Objects.requireNonNull(configPaths, "configPaths must not be null.");
		Objects.requireNonNull(sourceWrapper, "sourceWrapper must not be null.");
		Objects.requireNonNull(targetWrapper, "targetWrapper must not be null.");
		Objects.requireNonNull(processorConfig, "processorConfig must not be null.");
		if (configuredSteps == null || configuredSteps.isEmpty()) {
			throw new IllegalArgumentException("configuredSteps must not be null or empty.");
		}

		Map<String, SourceConfig> sourcesByName = indexSources(sourceWrapper);
		Map<String, TargetConfig> targetsByName = indexTargets(targetWrapper);
		String processorType = requireNonBlank(processorConfig.getType(), "processorConfig.type");
		List<ScenarioStepDescriptor> stepDescriptors = new ArrayList<>();
		List<ScenarioStepLinkDescriptor> stepLinks = new ArrayList<>();
		List<ScenarioSubFlowDescriptor> subFlowDescriptors = new ArrayList<>();
		ScenarioStepDescriptor previousStep = null;
		ScenarioSubFlowDescriptor previousSubFlow = null;

		for (int i = 0; i < configuredSteps.size(); i++) {
			JobConfig.JobStepConfig configuredStep = Objects.requireNonNull(configuredSteps.get(i), "configuredSteps entry must not be null.");
			String stepName = requireNonBlank(configuredStep.getName(), "configuredSteps[" + i + "].name");
			String sourceName = requireNonBlank(configuredStep.getSource(), "configuredSteps[" + i + "].source");
			String targetName = requireNonBlank(configuredStep.getTarget(), "configuredSteps[" + i + "].target");
			SourceConfig sourceConfig = required(sourcesByName.get(sourceName), "No source config found for step '" + stepName + "' and source '" + sourceName + "'.");
			TargetConfig targetConfig = required(targetsByName.get(targetName), "No target config found for step '" + stepName + "' and target '" + targetName + "'.");
			ProcessorConfig.EntityMapping mapping = resolveProcessorMapping(processorConfig, stepName, sourceName, targetName);
			ResolvedModelMetadata metadata = GeneratedModelClassResolver.resolveMetadata(sourceConfig, targetConfig);
			boolean finalOutput = i == configuredSteps.size() - 1;
			ScenarioStepDescriptor stepDescriptor = new ScenarioStepDescriptor(
					stepName,
					i,
					sourceName,
					targetName,
					processorType,
					stepName,
					null,
					ScenarioStepInputDescriptor.fromConfiguredSource(sourceName),
					ScenarioStepOutputDescriptor.configuredTarget(targetName, finalOutput),
					sourceConfig,
					targetConfig,
					mapping,
					ScenarioStepModelDescriptor.fromMetadata(metadata, ScenarioModelResolutionMode.LEGACY_BRIDGE),
					buildExecutionHints(sourceConfig, processorConfig, mapping),
					new ScenarioStepValidationSummary(true, true, true, true, List.of(), List.of(), null)
			);
			stepDescriptors.add(stepDescriptor);
			String subFlowName = stepName + "-subflow";
			boolean requiresHandoffReady = previousStep != null && stepDescriptor.input().inputAlias() != null && !stepDescriptor.input().inputAlias().isBlank();
			ScenarioSubFlowDescriptor subFlowDescriptor = new ScenarioSubFlowDescriptor(
					subFlowName,
					i,
					List.of(stepName),
					i == 0 ? ScenarioSubFlowExecutionStatus.READY : ScenarioSubFlowExecutionStatus.NOT_STARTED,
					ScenarioSubFlowControlDescriptor.defaultSequentialControl(requiresHandoffReady),
					previousSubFlow == null ? List.of() : List.of(previousSubFlow.subFlowName()),
					previousStep == null ? List.of() : List.of(stepDescriptor.input().inputAlias()),
					List.of(stepDescriptor.output().outputAlias()),
					null
			);
			subFlowDescriptors.add(subFlowDescriptor);

			if (previousStep != null) {
				stepLinks.add(new ScenarioStepLinkDescriptor(
						previousStep.stepName(),
						stepDescriptor.stepName(),
						ScenarioStepLinkType.ORDER_ONLY,
						previousStep.output().outputAlias(),
						stepDescriptor.input().inputAlias(),
						ScenarioStepLinkControlDescriptor.defaultSequentialControl(requiresHandoffReady),
						null
				));
			}
			previousStep = stepDescriptor;
			previousSubFlow = subFlowDescriptor;
		}

		return new ScenarioRuntimeDescriptor(
				resolvedScenarioName,
				resolvedScenarioName,
				defaultMainFlowName(resolvedScenarioName),
				"default-subflow",
				true,
				ScenarioRecoveryPolicy.RERUN_FROM_START,
				subFlowDescriptors,
				null,
				null,
				jobConfigPath,
				runMode,
				configPaths,
				stepDescriptors,
				null,
				stepLinks,
				new ScenarioValidationSummary(true, true, true, true, List.of(), List.of(), null)
		);
	}

	private ScenarioStepExecutionHints buildExecutionHints(SourceConfig sourceConfig,
	                                                     ProcessorConfig processorConfig,
	                                                     ProcessorConfig.EntityMapping mapping) {
		boolean rejectHandlingEnabled = processorConfig.getRejectHandling() != null && processorConfig.getRejectHandling().isEnabled();
		boolean duplicateHandlingEnabled = hasDuplicateRule(mapping);
		boolean orderedDuplicateSelection = hasOrderedDuplicateSelection(mapping);
		boolean archiveOnSuccessEnabled = sourceConfig instanceof FileSourceConfig fileSourceConfig
				&& fileSourceConfig.isArchiveOnSuccessEnabled();
		return new ScenarioStepExecutionHints(
				ScenarioStepExecutionMode.UNRESOLVED,
				false,
				null,
				rejectHandlingEnabled,
				duplicateHandlingEnabled,
				orderedDuplicateSelection,
				archiveOnSuccessEnabled,
				null
		);
	}

	private boolean hasDuplicateRule(ProcessorConfig.EntityMapping mapping) {
		if (mapping.getFields() == null) {
			return false;
		}
		return mapping.getFields().stream()
				.filter(field -> field.getRules() != null)
				.flatMap(field -> field.getRules().stream())
				.anyMatch(rule -> "duplicate".equalsIgnoreCase(rule.getType()));
	}

	private boolean hasOrderedDuplicateSelection(ProcessorConfig.EntityMapping mapping) {
		if (mapping.getFields() == null) {
			return false;
		}
		return mapping.getFields().stream()
				.filter(field -> field.getRules() != null)
				.flatMap(field -> field.getRules().stream())
				.anyMatch(rule -> "duplicate".equalsIgnoreCase(rule.getType())
						&& rule.getOrderBy() != null
						&& !rule.getOrderBy().isEmpty());
	}

	private Map<String, SourceConfig> indexSources(SourceWrapper sourceWrapper) {
		Map<String, SourceConfig> indexed = new LinkedHashMap<>();
		List<SourceConfig> sources = sourceWrapper.getSources();
		if (sources == null || sources.isEmpty()) {
			throw new IllegalArgumentException("sourceWrapper must contain at least one source.");
		}
		for (SourceConfig sourceConfig : sources) {
			indexed.put(requireNonBlank(sourceConfig.getSourceName(), "sourceConfig.sourceName"), sourceConfig);
		}
		return indexed;
	}

	private Map<String, TargetConfig> indexTargets(TargetWrapper targetWrapper) {
		Map<String, TargetConfig> indexed = new LinkedHashMap<>();
		List<TargetConfig> targets = targetWrapper.getTargets();
		if (targets == null || targets.isEmpty()) {
			throw new IllegalArgumentException("targetWrapper must contain at least one target.");
		}
		for (TargetConfig targetConfig : targets) {
			indexed.put(requireNonBlank(targetConfig.getTargetName(), "targetConfig.targetName"), targetConfig);
		}
		return indexed;
	}

	private ProcessorConfig.EntityMapping resolveProcessorMapping(ProcessorConfig processorConfig,
	                                                            String stepName,
	                                                            String sourceName,
	                                                            String targetName) {
		if (processorConfig.getMappings() == null || processorConfig.getMappings().isEmpty()) {
			throw new IllegalArgumentException("Processor config contains no mappings for step '" + stepName + "'.");
		}
		return processorConfig.getMappings().stream()
				.filter(mapping -> sourceName.equals(mapping.getSource()) && targetName.equals(mapping.getTarget()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"No processor mapping found for step '" + stepName + "' using source '" + sourceName + "' and target '" + targetName + "'."
				));
	}

	private static String requireNonBlank(String value, String field) {
		Objects.requireNonNull(value, field + " must not be null.");
		if (value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank.");
		}
		return value.trim();
	}

	private static <T> T required(T value, String message) {
		if (value == null) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private static String defaultMainFlowName(String scenarioName) {
		return scenarioName.endsWith("-flow") || scenarioName.endsWith("Flow")
				? scenarioName
				: scenarioName + "-main-flow";
	}
}


