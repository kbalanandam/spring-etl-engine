package com.etl.config.target;

import java.util.List;

import com.etl.config.FieldDefinition;
import com.etl.config.ModelConfig;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class TargetConfig implements ModelConfig{
	private String type;
	private String packageName;
	private String filePath;
	private String targetName;
	@JsonDeserialize(contentAs = ColumnConfig.class)
	private List<FieldDefinition> fields;

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

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public String getTargetName() {
		return targetName;
	}

	public void setTargetName(String targetName) {
		this.targetName = targetName;
	}

	public List<FieldDefinition> getFields() {
		return (List<FieldDefinition>)(List<?>) fields;
	}

	public void setFields(List<FieldDefinition> fields) {
		this.fields = fields;
	}
}
