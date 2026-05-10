package com.etl.config.target;

import com.etl.config.FieldDefinition;
import com.etl.config.ColumnConfig;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CsvTargetConfig extends TargetConfig {

    private final String filePath;
    private final String delimiter;
    private final boolean includeHeader;

    public CsvTargetConfig(
            @JsonProperty("targetName") String targetName,
            @JsonProperty("packageName") String packageName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("delimiter") String delimiter
    ) {
        this(targetName, packageName, fields, filePath, delimiter, false);
    }

    public CsvTargetConfig(
            String targetName,
            String packageName,
            List<ColumnConfig> fields,
            String filePath,
            String delimiter,
            boolean includeHeader
    ) {
        super(targetName, packageName, fields);
        this.filePath = filePath;
        this.delimiter = delimiter;
        this.includeHeader = includeHeader;
    }

    @JsonCreator
    public CsvTargetConfig(
            @JsonProperty("targetName") String targetName,
            @JsonProperty("packageName") String packageName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("delimiter") String delimiter,
            @JsonProperty("includeHeader") Boolean includeHeader
    ) {
        super(targetName, packageName, fields);
        this.filePath = filePath;
        this.delimiter = delimiter;
        this.includeHeader = includeHeader != null && includeHeader;
    }

          public String getFilePath() {
            return filePath;
          }

          public String getDelimiter() {
            return delimiter;
          }

          public boolean isIncludeHeader() {
            return includeHeader;
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
