package com.etl.config.job;

import java.util.List;

/**
 * Explicit job-level config selection.
 *
 * <p>A job config chooses exactly one source config, one target config,
 * and one processor config for a run. It does not perform scenario discovery.</p>
 *
 * <p><strong>Transition status:</strong> REUSE.</p>
 *
 * <p>This remains an approved contract for explicit job selection in the next
 * architecture direction. Prefer reusing and evolving this contract instead of
 * introducing a second competing run-selection model without a documented design
 * decision.</p>
 */
public class JobConfig {

	private String name;
	private Boolean isActive;
	private String sourceConfigPath;
	private String targetConfigPath;
	private String processorConfigPath;
	private List<JobStepConfig> steps;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Boolean getIsActive() {
		return isActive;
	}

	public void setIsActive(Boolean isActive) {
		this.isActive = isActive;
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

	public List<JobStepConfig> getSteps() {
		return steps;
	}

	public void setSteps(List<JobStepConfig> steps) {
		this.steps = steps;
	}

	public static class JobStepConfig {

		private String name;
		private String source;
		private String target;
		private SkipPolicyConfig skipPolicy;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getSource() {
			return source;
		}

		public void setSource(String source) {
			this.source = source;
		}

		public String getTarget() {
			return target;
		}

		public void setTarget(String target) {
			this.target = target;
		}

		public SkipPolicyConfig getSkipPolicy() {
			return skipPolicy;
		}

		public void setSkipPolicy(SkipPolicyConfig skipPolicy) {
			this.skipPolicy = skipPolicy;
		}
	}

	public static class SkipPolicyConfig {

		private boolean enabled;
		private Integer skipLimit;
		private List<String> skippableCategories;
		private List<String> skippableExceptions;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public Integer getSkipLimit() {
			return skipLimit;
		}

		public void setSkipLimit(Integer skipLimit) {
			this.skipLimit = skipLimit;
		}

		public List<String> getSkippableCategories() {
			return skippableCategories;
		}

		public void setSkippableCategories(List<String> skippableCategories) {
			this.skippableCategories = skippableCategories;
		}

		public List<String> getSkippableExceptions() {
			return skippableExceptions;
		}

		public void setSkippableExceptions(List<String> skippableExceptions) {
			this.skippableExceptions = skippableExceptions;
		}
	}
}
