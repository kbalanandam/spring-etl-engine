package com.etl.runtime.job;

import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;

import java.util.Objects;

/**
 * Self-explanatory descriptor for one executable flow unit inside a selected scenario.
 */
public record JobStepDescriptor(
		String stepName,
		int stepOrder,
		String sourceName,
		String targetName,
		String processorType,
		String displayName,
		String flowSummary,
		JobStepInputDescriptor input,
		JobStepOutputDescriptor output,
		SourceConfig sourceConfig,
		TargetConfig targetConfig,
		ProcessorConfig.EntityMapping processorMapping,
		JobStepModelDescriptor modelDescriptor,
		JobStepExecutionHints executionHints,
		JobStepValidationSummary validationSummary
) {

	public JobStepDescriptor {
		stepName = requireNonBlank(stepName, "stepName");
		if (stepOrder < 0) {
			throw new IllegalArgumentException("stepOrder must not be negative.");
		}
		sourceName = requireNonBlank(sourceName, "sourceName");
		targetName = requireNonBlank(targetName, "targetName");
		processorType = requireNonBlank(processorType, "processorType");
		if (input == null) {
			throw new IllegalArgumentException("input must not be null.");
		}
		if (output == null) {
			throw new IllegalArgumentException("output must not be null.");
		}
		Objects.requireNonNull(sourceConfig, "sourceConfig must not be null.");
		Objects.requireNonNull(targetConfig, "targetConfig must not be null.");
		Objects.requireNonNull(processorMapping, "processorMapping must not be null.");
		Objects.requireNonNull(modelDescriptor, "modelDescriptor must not be null.");
		Objects.requireNonNull(executionHints, "executionHints must not be null.");
		Objects.requireNonNull(validationSummary, "validationSummary must not be null.");
		displayName = displayName == null || displayName.isBlank() ? stepName : displayName.trim();
		flowSummary = flowSummary == null || flowSummary.isBlank()
				? "Step " + stepOrder + " reads '" + sourceName + "', applies processor '" + processorType
				+ "', and writes '" + targetName + "'."
				: flowSummary.trim();
	}

	public boolean emitsFinalScenarioOutput() {
		return output.finalScenarioOutput();
	}

	private static String requireNonBlank(String value, String field) {
		Objects.requireNonNull(value, field + " must not be null.");
		if (value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank.");
		}
		return value.trim();
	}
}

