package com.etl.runtime.job;

import com.etl.common.util.ResolvedModelMetadata;

import java.util.Objects;

/**
 * Self-explanatory model contract for one scenario step.
 *
 * <p>This record captures the model classes that the active step expects the runtime to use:
 * the source read class, the target processing class, the target write/root class, and any
 * XML wrapper-field requirement. It is the descriptor-level mirror of
 * {@link com.etl.common.util.ResolvedModelMetadata}.</p>
 */
public record JobStepModelDescriptor(
		String sourceClassName,
		String targetProcessingClassName,
		String targetWriteClassName,
		boolean wrapperRequired,
		String wrapperFieldName,
		JobModelResolutionMode resolutionMode,
		String summary
) {

	public JobStepModelDescriptor {
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

	public static JobStepModelDescriptor fromMetadata(ResolvedModelMetadata metadata,
	                                                      JobModelResolutionMode resolutionMode) {
		if (metadata == null) {
			throw new IllegalArgumentException("metadata must not be null.");
		}
		return new JobStepModelDescriptor(
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

