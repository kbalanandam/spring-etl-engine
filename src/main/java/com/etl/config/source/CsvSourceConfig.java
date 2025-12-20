package com.etl.config.source;

import com.etl.config.FieldDefinition;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

public class CsvSourceConfig extends SourceConfig {

    private String filePath;
    private String delimiter;
    @JsonDeserialize(contentAs = ColumnConfig.class)
    private List<ColumnConfig> fields;

    public String getFilePath() {
        return filePath;
    }

    public String getDelimiter() {
        return delimiter;
    }

    @Override
    public String getType() {
        return "csv";
    }

    @Override
    public List<? extends FieldDefinition> getFields() {
        return fields;
    }

}