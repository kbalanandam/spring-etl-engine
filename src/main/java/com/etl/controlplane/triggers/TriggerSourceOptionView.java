package com.etl.controlplane.triggers;

/**
 * Canonical trigger-source option for API/UI filter use.
 */
public record TriggerSourceOptionView(
		String sourceCode,
		String displayName,
		String description
) {
}

