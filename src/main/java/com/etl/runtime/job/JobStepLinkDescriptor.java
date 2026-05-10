package com.etl.runtime.job;

import java.util.Objects;

/**
 * Describes a relationship between two scenario steps.
 */
public record JobStepLinkDescriptor(
		String fromStepName,
		String toStepName,
		JobStepLinkType linkType,
		String outputAlias,
		String inputAlias,
		JobStepLinkControlDescriptor control,
		String summary
) {

	public JobStepLinkDescriptor {
		fromStepName = requireNonBlank(fromStepName, "fromStepName");
		toStepName = requireNonBlank(toStepName, "toStepName");
		if (linkType == null) {
			throw new IllegalArgumentException("linkType must not be null.");
		}
		control = control == null ? JobStepLinkControlDescriptor.defaultSequentialControl(inputAlias != null && !inputAlias.isBlank()) : control;
		summary = summary == null || summary.isBlank()
				? "Step '" + fromStepName + "' connects to step '" + toStepName + "' via " + linkType + ". control={" + control.summary() + "}"
				: summary.trim();
	}

	private static String requireNonBlank(String value, String field) {
		Objects.requireNonNull(value, field + " must not be null.");
		if (value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank.");
		}
		return value.trim();
	}
}

