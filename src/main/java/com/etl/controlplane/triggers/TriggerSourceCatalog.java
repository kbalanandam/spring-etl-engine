package com.etl.controlplane.triggers;

import java.util.List;

public interface TriggerSourceCatalog {
	List<TriggerSourceOptionView> listActiveSources();

	static List<TriggerSourceOptionView> defaultSources() {
		return List.of(
				new TriggerSourceOptionView("MANUAL", "Manual", "Ad hoc operator or API-triggered launch"),
				new TriggerSourceOptionView("SCHEDULE", "Schedule", "Native scheduler-origin launch"),
				new TriggerSourceOptionView("EVENT", "Event", "File watcher or external event-origin launch")
		);
	}
}

