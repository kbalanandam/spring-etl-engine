package com.etl.config.target;

import java.util.List;

//TargetConfig.java
public class TargetConfig {
	private Target target;

	public Target getTarget() {
		return target;
	}

	public void setTarget(Target target) {
		this.target = target;
	}

	public static class Target {
		private String packageName;
		private String className;
		private List<Field> fields;
		private String type; // e.g., "xml", "json", etc.

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}

		public String getPackageName() {
			return packageName;
		}

		public void setPackageName(String packageName) {
			this.packageName = packageName;
		}

		public String getClassName() {
			return className;
		}

		public void setClassName(String className) {
			this.className = className;
		}

		public List<Field> getFields() {
			return fields;
		}

		public void setFields(List<Field> fields) {
			this.fields = fields;
		}
	}

	public static class Field {
		private String name;
		private String type;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getType() {
			return type;
		}

		public void setType(String type) {
			this.type = type;
		}
	}
}
