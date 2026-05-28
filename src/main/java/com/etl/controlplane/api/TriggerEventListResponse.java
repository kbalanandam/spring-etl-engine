package com.etl.controlplane.api;

import com.etl.controlplane.triggers.TriggerEventView;

import java.util.List;

public record TriggerEventListResponse(
		List<TriggerEventView> items,
		int page,
		int size,
		long totalItems
) {
}

