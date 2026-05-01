package com.etl.config.target;

import com.etl.config.FieldDefinition;
import com.etl.config.ColumnConfig;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class XmlTargetConfig extends TargetConfig {

    private final String filePath;
    private final String rootElement;
    private final String recordElement;
    private final String modelDefinitionPath;

    public XmlTargetConfig(
            String targetName,
            String packageName,
            List<ColumnConfig> fields,
            String filePath,
            String rootElement,
            String recordElement
    ) {
        this(targetName, packageName, fields, filePath, rootElement, recordElement, null);
    }

    @JsonCreator
    public XmlTargetConfig(
            @JsonProperty("targetName") String targetName,
            @JsonProperty("packageName") String packageName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("rootElement") String rootElement,
            @JsonProperty("recordElement") String recordElement,
            @JsonProperty("modelDefinitionPath") String modelDefinitionPath
    ) {
        super(targetName, packageName, fields);
        this.filePath = filePath;
        this.rootElement = rootElement;
        this.recordElement = recordElement;
        this.modelDefinitionPath = modelDefinitionPath;
    }

          public String getFilePath() {
            return filePath;
          }

          public String getRootElement() {
            return rootElement;
          }

          public String getRecordElement() {
            return recordElement;
          }

          public String getModelDefinitionPath() {
            return modelDefinitionPath;
          }

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.XML;
    }

    @Override
    public List<? extends FieldDefinition> getFields() {
        return super.getFields();
    }

}
