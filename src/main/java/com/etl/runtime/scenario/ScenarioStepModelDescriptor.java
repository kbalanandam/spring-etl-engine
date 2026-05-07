package com.etl.runtime.scenario;

import com.etl.common.util.ResolvedModelMetadata;

import java.util.Objects;

/**
 * Self-explanatory model contract for one scenario step.
 */
public record ScenarioStepModelDescriptor(
		String sourceClassName,
		String targetProcessingClassName,
		String targetWriteClassName,
		boolean wrapperRequired,
		String wrapperFieldName,
		ScenarioModelResolutionMode resolutionMode,
		String summary
) {

	public ScenarioStepModelDescriptor {
		sourceClassName = requireNonBlank(sourceClassName, "sourceClassName");
		targetProcessingClassName = requireNonBlank(targetProcessingClassName, "targetProcessingClassName");
		targetWriteClassName = requireNonBlank(targetWriteClassName, "targetWriteClassName");
		if (resolutionMode == null) {
			throw new IllegalArgumentException("resolutionMode must not be null.");
		}
		summary = summary == null || summary.isBlank()
				? "sourceClass=" + sourceClassName + ", targetProcessingClass=" + targetProcessingClassName
				+ ", targetWriteClass=" + targetWriteClassName + ", wrapperRequired=" + wrapperRequired
				: summary.trim();
	}

	public static ScenarioStepModelDescriptor fromMetadata(ResolvedModelMetadata metadata,
	                                                      ScenarioModelResolutionMode resolutionMode) {
		if (metadata == null) {
			throw new IllegalArgumentException("metadata must not be null.");
		}
		return new ScenarioStepModelDescriptor(
				metadata.getSourceClassName(),
				metadata.getTargetProcessingClassName(),
				metadata.getTargetWriteClassName(),
				metadata.isWrapperRequired(),
				metadata.getWrapperFieldName(),
				resolutionMode,
				null
		);
	}

	private static String requireNonBlank(String value, String field) {
		Objects.requireNonNull(value, field + " must not be null.");
		if (value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank.");
		}
		return value.trim();
	}
}

