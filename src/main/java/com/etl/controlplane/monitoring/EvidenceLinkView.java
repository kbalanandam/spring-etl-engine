package com.etl.controlplane.monitoring;

/**
 * Lightweight evidence link for operator drill-down.
 */
public record EvidenceLinkView(
		String label,
		String href,
		String type
) {
}

