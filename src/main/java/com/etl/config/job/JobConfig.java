package com.etl.config.job;

/**
 * Explicit job-level config selection.
 *
 * <p>A job config chooses exactly one source config, one target config,
 * and one processor config for a run. It does not perform scenario discovery.</p>
 */
public class JobConfig {

	private String name;
	private String sourceConfigPath;
	private String targetConfigPath;
	private String processorConfigPath;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getSourceConfigPath() {
		return sourceConfigPath;
	}

	public void setSourceConfigPath(String sourceConfigPath) {
		this.sourceConfigPath = sourceConfigPath;
	}

	public String getTargetConfigPath() {
		return targetConfigPath;
	}

	public void setTargetConfigPath(String targetConfigPath) {
		this.targetConfigPath = targetConfigPath;
	}

	public String getProcessorConfigPath() {
		return processorConfigPath;
	}

	public void setProcessorConfigPath(String processorConfigPath) {
		this.processorConfigPath = processorConfigPath;
	}
}
