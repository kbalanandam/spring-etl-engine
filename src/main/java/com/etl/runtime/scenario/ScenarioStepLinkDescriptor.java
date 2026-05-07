package com.etl.runtime.scenario;

import java.util.Objects;

/**
 * Describes a relationship between two scenario steps.
 */
public record ScenarioStepLinkDescriptor(
		String fromStepName,
		String toStepName,
		ScenarioStepLinkType linkType,
		String outputAlias,
		String inputAlias,
		ScenarioStepLinkControlDescriptor control,
		String summary
) {

	public ScenarioStepLinkDescriptor {
		fromStepName = requireNonBlank(fromStepName, "fromStepName");
		toStepName = requireNonBlank(toStepName, "toStepName");
		if (linkType == null) {
			throw new IllegalArgumentException("linkType must not be null.");
		}
		control = control == null ? ScenarioStepLinkControlDescriptor.defaultSequentialControl(inputAlias != null && !inputAlias.isBlank()) : control;
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

