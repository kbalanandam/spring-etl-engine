package com.etl.config.target;

import java.util.List;

import com.etl.config.ModelConfig;

public class TargetConfig implements ModelConfig{
	private String type;
	private String packageName;
	private String filePath;
	private String targetName;
	private List<ColumnConfig> fields;

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

	public List<ColumnConfig> getFields() {
		return fields;
	}

	public void setFields(List<ColumnConfig> fields) {
		this.fields = fields;
	}
}
