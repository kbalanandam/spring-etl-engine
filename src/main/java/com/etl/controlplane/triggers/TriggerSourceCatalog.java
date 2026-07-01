package com.etl.controlplane.triggers;

import java.util.List;

public interface TriggerSourceCatalog {
	List<TriggerSourceOptionView> listActiveSources();
}


