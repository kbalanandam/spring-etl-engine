package com.etl.controlplane.api;

import com.etl.controlplane.triggers.TriggerSourceOptionView;

import java.util.List;

/**
 * Minimal list envelope for trigger-source options used by runs filters.
 */
public record TriggerSourceListResponse(
		List<TriggerSourceOptionView> items
) {
}

