package com.etl.config.target;

import com.etl.config.ColumnConfig;
import com.etl.config.FieldDefinition;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class JsonTargetConfig extends TargetConfig {

    private final String filePath;

    public JsonTargetConfig(
            String targetName,
            String packageName,
            List<ColumnConfig> fields,
            String filePath
    ) {
        super(targetName, packageName, fields);
        this.filePath = filePath;
    }

    @JsonCreator
    public JsonTargetConfig(
            @JsonProperty("targetName") String targetName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("filePath") String filePath
    ) {
        this(targetName, null, fields, filePath);
    }

    public String getFilePath() {
        return filePath;
    }

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.JSON;
    }

    @Override
    public List<? extends FieldDefinition> getFields() {
        return super.getFields();
    }
}

