package com.etl.config.source;

import java.util.List;

public class SourceWrapper {
	private List<SourceConfig> sources;

	public List<SourceConfig> getSources() {
		return sources;
	}

	public void setSources(List<SourceConfig> sources) {
		this.sources = sources;
	}
}
