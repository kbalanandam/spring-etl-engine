package com.etl.controlplane.api;

import com.etl.controlplane.monitoring.RunRecoveryView;

/**
 * Response envelope for one advisory run recovery view.
 */
public record RunRecoveryResponse(
		RunRecoveryView recovery
) {
}

