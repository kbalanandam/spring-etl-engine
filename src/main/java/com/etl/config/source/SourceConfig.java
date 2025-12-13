package com.etl.config.source;

import com.etl.config.FieldDefinition;
import com.etl.config.ModelConfig;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

public class SourceConfig implements ModelConfig {
	private String type;
	private String sourceName;
	private String packageName;
	private String filePath;
	private String delimiter;
	@JsonDeserialize(contentAs = ColumnConfig.class)
	private List<ColumnConfig> fields;

	// Getters and Setters

	public String getPackageName() {
		return packageName;
	}

	public void setPackageName(String packageName) {
		this.packageName = packageName;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSourceName() {
		return sourceName;
	}

	public void setSourceName(String sourceName) {
		this.sourceName = sourceName;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public String getDelimiter() {
		return delimiter;
	}

	public void setDelimiter(String delimiter) {
		this.delimiter = delimiter;
	}

	public List<FieldDefinition> getFields() {
		return List.copyOf(fields); // safe, immutable view
	}

	public void setColumns(List<ColumnConfig> fields) {
		this.fields = fields;
	}


}
