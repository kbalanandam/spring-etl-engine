package com.etl.config.target;

import com.etl.config.FieldDefinition;
import com.etl.config.ColumnConfig;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class CsvTargetConfig extends TargetConfig {

    public static final String DEFAULT_DELIMITER = ",";

    private final String filePath;
    private final String delimiter;
    private final boolean includeHeader;
    private final boolean packageAsZip;

    public CsvTargetConfig(
            String targetName,
            String packageName,
            List<ColumnConfig> fields,
            String filePath,
            String delimiter
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
        this(targetName, packageName, fields, filePath, delimiter, includeHeader, false);
    }

    public CsvTargetConfig(
            String targetName,
            String packageName,
            List<ColumnConfig> fields,
            String filePath,
            String delimiter,
            boolean includeHeader,
            boolean packageAsZip
    ) {
        super(targetName, packageName, fields);
        this.filePath = filePath;
        this.delimiter = resolveDelimiter(delimiter);
        this.includeHeader = includeHeader;
        this.packageAsZip = packageAsZip;
    }

    @JsonCreator
    public CsvTargetConfig(
            @JsonProperty("targetName") String targetName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("delimiter") String delimiter,
            @JsonProperty("includeHeader") Boolean includeHeader,
            @JsonProperty("packageAsZip") Boolean packageAsZip
    ) {
        this(targetName, null, fields, filePath, delimiter, includeHeader != null && includeHeader,
                packageAsZip != null && packageAsZip);
    }

    private static String resolveDelimiter(String delimiter) {
        return delimiter == null || delimiter.isBlank() ? DEFAULT_DELIMITER : delimiter;
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

          public boolean isPackageAsZip() {
            return packageAsZip;
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
