package com.etl.config.processor;

import java.util.List;

public class ProcessorConfig {

	private String type;
	private List<EntityMapping> mappings;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
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
	}
}
