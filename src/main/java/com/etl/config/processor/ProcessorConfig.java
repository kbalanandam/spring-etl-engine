package com.etl.config.processor;

import java.util.List;
import java.util.Map;

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
		private List<FieldTransform> transforms;
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

		public List<FieldTransform> getTransforms() {
			return transforms;
		}

		public void setTransforms(List<FieldTransform> transforms) {
			this.transforms = transforms;
		}

		public List<FieldRule> getRules() {
			return rules;
		}

		public void setRules(List<FieldRule> rules) {
			this.rules = rules;
		}
	}

	public static class FieldTransform {

		private String type;
		private String expression;
		private Map<String, Object> mappings;
		private List<ConditionalCase> cases;
		private Object defaultValue;
		private Boolean caseSensitive;

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getExpression() {
			return expression;
		}

		public void setExpression(String expression) {
			this.expression = expression;
		}

		public Map<String, Object> getMappings() {
			return mappings;
		}

		public void setMappings(Map<String, Object> mappings) {
			this.mappings = mappings;
		}

		public List<ConditionalCase> getCases() {
			return cases;
		}

		public void setCases(List<ConditionalCase> cases) {
			this.cases = cases;
		}

		public Object getDefaultValue() {
			return defaultValue;
		}

		public void setDefaultValue(Object defaultValue) {
			this.defaultValue = defaultValue;
		}

		public Boolean getCaseSensitive() {
			return caseSensitive;
		}

		public void setCaseSensitive(Boolean caseSensitive) {
			this.caseSensitive = caseSensitive;
		}
	}

	public static class ConditionalCase {

		private String when;
		private Object then;

		public String getWhen() {
			return when;
		}

		public void setWhen(String when) {
			this.when = when;
		}

		public Object getThen() {
			return then;
		}

		public void setThen(Object then) {
			this.then = then;
		}
	}

	public static class RejectHandling {

		private boolean enabled;
		private String outputPath;
		private String quarantinePath;
		private boolean includeReasonColumns = true;
		private boolean packageAsZip;

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

		public String getQuarantinePath() {
			return quarantinePath;
		}

		public void setQuarantinePath(String quarantinePath) {
			this.quarantinePath = quarantinePath;
		}

		public boolean isIncludeReasonColumns() {
			return includeReasonColumns;
		}

		public void setIncludeReasonColumns(boolean includeReasonColumns) {
			this.includeReasonColumns = includeReasonColumns;
		}

		public boolean isPackageAsZip() {
			return packageAsZip;
		}

		public void setPackageAsZip(boolean packageAsZip) {
			this.packageAsZip = packageAsZip;
		}
	}

	public static class FieldRule {

		private String type;
		private String onFailure;
		private String pattern;
		private List<String> keyFields;
		private List<OrderByField> orderBy;
		private String storageMode;

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getOnFailure() {
			return onFailure;
		}

		public void setOnFailure(String onFailure) {
			this.onFailure = onFailure;
		}

		public String getPattern() {
			return pattern;
		}

		public void setPattern(String pattern) {
			this.pattern = pattern;
		}

		public List<String> getKeyFields() {
			return keyFields;
		}

		public void setKeyFields(List<String> keyFields) {
			this.keyFields = keyFields;
		}

		public List<OrderByField> getOrderBy() {
			return orderBy;
		}

		public void setOrderBy(List<OrderByField> orderBy) {
			this.orderBy = orderBy;
		}

		public String getStorageMode() {
			return storageMode;
		}

		public void setStorageMode(String storageMode) {
			this.storageMode = storageMode;
		}
	}

	public static class OrderByField {

		private String field;
		private String direction;

		public String getField() {
			return field;
		}

		public void setField(String field) {
			this.field = field;
		}

		public String getDirection() {
			return direction;
		}

		public void setDirection(String direction) {
			this.direction = direction;
		}
	}
}
