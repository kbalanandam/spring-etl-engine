package com.etl.runtime.job;

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
 *
 * <p>This assembler does not change execution order. Instead, it projects the selected
 * explicit job bundle into descriptor records that make the shipped flat Spring Batch plan
 * easier to log, inspect, and reason about as MainFlow/SubFlow/Step observability data.</p>
 */
public class JobRuntimeDescriptorAssembler {

	/**
	 * Assembles one runtime descriptor for the selected scenario bundle.
	 *
	 * <p>The descriptor is built from the same source/target/processor/job contract used by
	 * execution so observability remains aligned with the real runtime plan. Intermediate
	 * handoffs, final outputs, and model-resolution metadata are all derived here from the
	 * explicit ordered step list.</p>
	 */
	public JobRuntimeDescriptor assemble(String scenarioName,
	                                        String jobConfigPath,
	                                        JobRunMode runMode,
	                                        JobRecoveryPolicy recoveryPolicy,
	                                        JobConfigPaths configPaths,
	                                        List<JobConfig.JobStepConfig> configuredSteps,
	                                        SourceWrapper sourceWrapper,
	                                        TargetWrapper targetWrapper,
	                                        ProcessorConfig processorConfig) {
		String resolvedScenarioName = requireNonBlank(scenarioName, "scenarioName");
		Objects.requireNonNull(runMode, "runMode must not be null.");
		Objects.requireNonNull(recoveryPolicy, "recoveryPolicy must not be null.");
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
		List<JobStepDescriptor> stepDescriptors = new ArrayList<>();
		List<JobStepLinkDescriptor> stepLinks = new ArrayList<>();
		List<JobSubFlowDescriptor> subFlowDescriptors = new ArrayList<>();
		JobStepDescriptor previousStep = null;
		JobSubFlowDescriptor previousSubFlow = null;

		for (int i = 0; i < configuredSteps.size(); i++) {
			JobConfig.JobStepConfig configuredStep = Objects.requireNonNull(configuredSteps.get(i), "configuredSteps entry must not be null.");
			String stepName = requireNonBlank(configuredStep.getName(), "configuredSteps[" + i + "].name");
			if (configuredStep.isCustomStep()) {
				continue;
			}
			String sourceName = requireNonBlank(configuredStep.getSource(), "configuredSteps[" + i + "].source");
			String targetName = requireNonBlank(configuredStep.getTarget(), "configuredSteps[" + i + "].target");
			SourceConfig sourceConfig = required(sourcesByName.get(sourceName), "No source config found for step '" + stepName + "' and source '" + sourceName + "'.");
			TargetConfig targetConfig = required(targetsByName.get(targetName), "No target config found for step '" + stepName + "' and target '" + targetName + "'.");
			ProcessorConfig.EntityMapping mapping = resolveProcessorMapping(processorConfig, stepName, sourceName, targetName);
			ResolvedModelMetadata metadata = GeneratedModelClassResolver.resolveMetadata(sourceConfig, targetConfig);
			boolean finalOutput = !hasDownstreamConsumer(configuredSteps, i, targetName);
			boolean directHandoff = previousStep != null && previousStep.targetName().equals(sourceName);
			JobStepInputDescriptor inputDescriptor = directHandoff
					? new JobStepInputDescriptor(
							JobStepInputType.UPSTREAM_STEP_OUTPUT,
							sourceName,
							previousStep.stepName(),
							sourceName,
							null,
							null)
					: JobStepInputDescriptor.fromConfiguredSource(sourceName);
			JobStepOutputDescriptor outputDescriptor = finalOutput
					? JobStepOutputDescriptor.configuredTarget(targetName, true)
					: new JobStepOutputDescriptor(
							JobStepOutputType.INTERMEDIATE_DATASET,
							targetName,
							targetName,
							false,
							null,
							null);
			JobStepDescriptor stepDescriptor = new JobStepDescriptor(
					stepName,
					i,
					sourceName,
					targetName,
					processorType,
					stepName,
					null,
					inputDescriptor,
					outputDescriptor,
					sourceConfig,
					targetConfig,
					mapping,
					JobStepModelDescriptor.fromMetadata(metadata, JobModelResolutionMode.LEGACY_BRIDGE),
					buildExecutionHints(sourceConfig, processorConfig, mapping),
					new JobStepValidationSummary(true, true, true, true, List.of(), List.of(), null)
			);
			stepDescriptors.add(stepDescriptor);
			String subFlowName = stepName + "-subflow";
			boolean requiresHandoffReady = previousStep != null && stepDescriptor.input().inputAlias() != null && !stepDescriptor.input().inputAlias().isBlank();
			JobSubFlowDescriptor subFlowDescriptor = new JobSubFlowDescriptor(
					subFlowName,
					i,
					List.of(stepName),
					i == 0 ? JobSubFlowExecutionStatus.READY : JobSubFlowExecutionStatus.NOT_STARTED,
					JobSubFlowControlDescriptor.defaultSequentialControl(requiresHandoffReady),
					previousSubFlow == null ? List.of() : List.of(previousSubFlow.subFlowName()),
					previousStep == null ? List.of() : handoffAliases(stepDescriptor.input().inputAlias()),
					List.of(stepDescriptor.output().outputAlias()),
					null
			);
			subFlowDescriptors.add(subFlowDescriptor);

			if (previousStep != null) {
				stepLinks.add(new JobStepLinkDescriptor(
						previousStep.stepName(),
						stepDescriptor.stepName(),
						directHandoff ? JobStepLinkType.DATA_HANDOFF : JobStepLinkType.ORDER_ONLY,
						previousStep.output().outputAlias(),
						stepDescriptor.input().inputAlias(),
						JobStepLinkControlDescriptor.defaultSequentialControl(requiresHandoffReady),
						null
				));
			}
			previousStep = stepDescriptor;
			previousSubFlow = subFlowDescriptor;
		}

		return new JobRuntimeDescriptor(
				resolvedScenarioName,
				resolvedScenarioName,
				defaultMainFlowName(resolvedScenarioName),
				"default-subflow",
				true,
				recoveryPolicy,
				subFlowDescriptors,
				null,
				null,
				jobConfigPath,
				runMode,
				configPaths,
				stepDescriptors,
				null,
				stepLinks,
				new JobValidationSummary(true, true, true, true, List.of(), List.of(), null)
		);
	}

	private JobStepExecutionHints buildExecutionHints(SourceConfig sourceConfig,
	                                                     ProcessorConfig processorConfig,
	                                                     ProcessorConfig.EntityMapping mapping) {
		boolean rejectHandlingEnabled = processorConfig.getRejectHandling() != null && processorConfig.getRejectHandling().isEnabled();
		boolean duplicateHandlingEnabled = hasDuplicateRule(mapping);
		boolean orderedDuplicateSelection = hasOrderedDuplicateSelection(mapping);
		boolean archiveOnSuccessEnabled = sourceConfig instanceof FileSourceConfig fileSourceConfig
				&& fileSourceConfig.isArchiveOnSuccessEnabled();
		return new JobStepExecutionHints(
				JobStepExecutionMode.UNRESOLVED,
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

	private boolean hasDownstreamConsumer(List<JobConfig.JobStepConfig> configuredSteps, int currentIndex, String targetName) {
		if (configuredSteps == null || targetName == null || targetName.isBlank()) {
			return false;
		}
		for (int i = currentIndex + 1; i < configuredSteps.size(); i++) {
			JobConfig.JobStepConfig candidate = configuredSteps.get(i);
			if (candidate != null && candidate.isCustomStep()) {
				continue;
			}
			if (candidate != null && targetName.equals(candidate.getSource())) {
				return true;
			}
		}
		return false;
	}

	private List<String> handoffAliases(String alias) {
		return alias == null || alias.isBlank() ? List.of() : List.of(alias.trim());
	}
}


