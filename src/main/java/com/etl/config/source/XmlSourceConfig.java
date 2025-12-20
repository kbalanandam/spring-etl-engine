package com.etl.config.source;

import com.etl.config.FieldDefinition;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

public class XmlSourceConfig extends SourceConfig {

    private String filePath;
    private String rootElement;
    private String recordElement;

    @JsonDeserialize(contentAs = ColumnConfig.class)
    private List<ColumnConfig> fields;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getRootElement() {
        return rootElement;
    }

    public void setRootElement(String rootElement) {
        this.rootElement = rootElement;
    }

    public String getRecordElement() {
        return recordElement;
    }

    public void setRecordElement(String recordElement) {
        this.recordElement = recordElement;
    }

    @Override
    public String getType() {
        return "xml";
    }

    @Override
    public List<? extends FieldDefinition> getFields() {
        return fields;
    }

    public void setFields(List<ColumnConfig> fields) {
        this.fields = fields;
    }
}