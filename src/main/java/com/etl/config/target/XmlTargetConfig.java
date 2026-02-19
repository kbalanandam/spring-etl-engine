package com.etl.config.target;

import com.etl.config.FieldDefinition;
import com.etl.config.ColumnConfig;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class XmlTargetConfig extends TargetConfig {

    private final String filePath;
    private final String rootElement;
    private final String recordElement;

    @JsonCreator
    public XmlTargetConfig(
            @JsonProperty("targetName") String targetName,
            @JsonProperty("packageName") String packageName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("rootElement") String rootElement,
            @JsonProperty("recordElement") String recordElement
    ) {
        super(targetName, packageName, fields);
        this.filePath = filePath;
        this.rootElement = rootElement;
        this.recordElement = recordElement;
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
