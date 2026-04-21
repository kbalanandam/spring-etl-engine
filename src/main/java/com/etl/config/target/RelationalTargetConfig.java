package com.etl.config.target;

import com.etl.config.ColumnConfig;
import com.etl.config.FieldDefinition;
import com.etl.config.relational.RelationalConnectionConfig;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class RelationalTargetConfig extends TargetConfig {

    private final RelationalConnectionConfig connection;
    private final String table;
    private final String schema;
    private final WriteMode writeMode;
    private final Integer batchSize;

    @JsonCreator
    public RelationalTargetConfig(
            @JsonProperty("targetName") String targetName,
            @JsonProperty("packageName") String packageName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("connection") RelationalConnectionConfig connection,
            @JsonProperty("table") String table,
            @JsonProperty("schema") String schema,
            @JsonProperty("writeMode") String writeMode,
            @JsonProperty("batchSize") Integer batchSize
    ) {
        super(targetName, packageName, fields);
        this.connection = connection;
        this.table = table;
        this.schema = schema;
        this.writeMode = writeMode == null || writeMode.isBlank() ? WriteMode.INSERT : WriteMode.fromString(writeMode);
        this.batchSize = batchSize == null || batchSize <= 0 ? 100 : batchSize;
    }

  public RelationalConnectionConfig getConnection() {
    return connection;
  }

  public String getTable() {
    return table;
  }

  public String getSchema() {
    return schema;
  }

  public WriteMode getWriteMode() {
    return writeMode;
  }

  public Integer getBatchSize() {
    return batchSize;
  }

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.RELATIONAL;
    }

    @Override
    public List<? extends FieldDefinition> getFields() {
        return super.getFields();
    }

    public String getEffectiveSchema() {
        if (schema != null && !schema.isBlank()) {
            return schema;
        }
        return connection != null ? connection.getSchema() : null;
    }
}


