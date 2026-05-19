package com.etl.config.target;

import com.etl.config.ColumnConfig;
import com.etl.config.FieldDefinition;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class JsonTargetConfig extends TargetConfig {

    private final String filePath;
    private final boolean packageAsZip;

    public JsonTargetConfig(
            String targetName,
            String packageName,
            List<ColumnConfig> fields,
            String filePath
    ) {
        this(targetName, packageName, fields, filePath, false);
    }

    public JsonTargetConfig(
            String targetName,
            String packageName,
            List<ColumnConfig> fields,
            String filePath,
            boolean packageAsZip
    ) {
        super(targetName, packageName, fields);
        this.filePath = filePath;
        this.packageAsZip = packageAsZip;
    }

    @JsonCreator
    public JsonTargetConfig(
            @JsonProperty("targetName") String targetName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("packageAsZip") Boolean packageAsZip
    ) {
        this(targetName, null, fields, filePath, packageAsZip != null && packageAsZip);
    }

    public String getFilePath() {
        return filePath;
    }

    public boolean isPackageAsZip() {
        return packageAsZip;
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

