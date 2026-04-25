package com.etl.config.processor;

import java.util.List;

public class ProcessorConfig {

	private String type;
	private RejectHandling rejectHandling;
	private List<EntityMapping> mappings;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public RejectHandling getRejectHandling() {
		return rejectHandling;
	}

	public void setRejectHandling(RejectHandling rejectHandling) {
		this.rejectHandling = rejectHandling;
	}

	public List<EntityMapping> getMappings() {
		return mappings;
	}

	public void setMappings(List<EntityMapping> mappings) {
		this.mappings = mappings;
	}

	// -------- EntityMapping --------
	public static class EntityMapping {

		private String source;
		private String target;
		private List<FieldMapping> fields;

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

		public List<FieldMapping> getFields() {
			return fields;
		}

		public void setFields(List<FieldMapping> fields) {
			this.fields = fields;
		}
	}

	// -------- FieldMapping --------
	public static class FieldMapping {

		private String from;
		private String to;
		private List<FieldRule> rules;

		public String getFrom() {
			return from;
		}

		public void setFrom(String from) {
			this.from = from;
		}

		public String getTo() {
			return to;
		}

		public void setTo(String to) {
			this.to = to;
		}

		public List<FieldRule> getRules() {
			return rules;
		}

		public void setRules(List<FieldRule> rules) {
			this.rules = rules;
		}
	}

	public static class RejectHandling {

		private boolean enabled;
		private String outputPath;
		private boolean includeReasonColumns = true;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getOutputPath() {
			return outputPath;
		}

		public void setOutputPath(String outputPath) {
			this.outputPath = outputPath;
		}

		public boolean isIncludeReasonColumns() {
			return includeReasonColumns;
		}

		public void setIncludeReasonColumns(boolean includeReasonColumns) {
			this.includeReasonColumns = includeReasonColumns;
		}
	}

	public static class FieldRule {

		private String type;
		private String pattern;

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getPattern() {
			return pattern;
		}

		public void setPattern(String pattern) {
			this.pattern = pattern;
		}
	}
}
