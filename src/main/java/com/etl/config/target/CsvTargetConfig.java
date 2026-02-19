package com.etl.config.target;

import com.etl.config.FieldDefinition;
import com.etl.config.ColumnConfig;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class CsvTargetConfig extends TargetConfig {

    private final String filePath;
    private final String delimiter;

    @JsonCreator
    public CsvTargetConfig(
            @JsonProperty("targetName") String targetName,
            @JsonProperty("packageName") String packageName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("delimiter") String delimiter
    ) {
        super(targetName, packageName, fields);
        this.filePath = filePath;
        this.delimiter = delimiter;
    }

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.CSV;
    }

    @Override
    public List<? extends FieldDefinition> getFields() {
        return super.getFields();
    }
}
