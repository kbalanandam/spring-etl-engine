package com.etl.config.target;

import com.etl.config.ColumnConfig;
import com.etl.config.FieldDefinition;
import com.etl.config.relational.RelationalConnectionConfig;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class RelationalTargetConfig extends TargetConfig {

    private RelationalConnectionConfig connection;
    private String connectionRef;
    private final String table;
    private final String schema;
    private final WriteMode writeMode;
    private final Integer batchSize;

    public RelationalTargetConfig(String targetName,
                                  String packageName,
                                  List<ColumnConfig> fields,
                                  RelationalConnectionConfig connection,
                                  String connectionRef,
                                  String table,
                                  String schema,
                                  String writeMode,
                                  Integer batchSize) {
        super(targetName, packageName, fields);
        this.connection = connection;
        this.connectionRef = connectionRef;
        this.table = table;
        this.schema = schema;
        this.writeMode = writeMode == null || writeMode.isBlank() ? WriteMode.INSERT : WriteMode.fromString(writeMode);
        this.batchSize = batchSize == null || batchSize <= 0 ? 100 : batchSize;
    }

    @JsonCreator
    public RelationalTargetConfig(
            @JsonProperty("targetName") String targetName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("connection") RelationalConnectionConfig connection,
            @JsonProperty("connectionRef") String connectionRef,
            @JsonProperty("table") String table,
            @JsonProperty("schema") String schema,
            @JsonProperty("writeMode") String writeMode,
            @JsonProperty("batchSize") Integer batchSize
    ) {
        this(targetName, null, fields, connection, connectionRef, table, schema, writeMode, batchSize);
    }

    public RelationalTargetConfig(String targetName,
                                  String packageName,
                                  List<ColumnConfig> fields,
                                  RelationalConnectionConfig connection,
                                  String table,
                                  String schema,
                                  String writeMode,
                                  Integer batchSize) {
        this(targetName, packageName, fields, connection, null, table, schema, writeMode, batchSize);
    }

  public RelationalConnectionConfig getConnection() {
    return connection;
  }

  public void setConnection(RelationalConnectionConfig connection) {
    this.connection = connection;
  }

  public String getConnectionRef() {
    return connectionRef;
  }

  public void setConnectionRef(String connectionRef) {
    this.connectionRef = connectionRef;
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

    public void validate() {
        boolean hasConnection = connection != null;
        boolean hasConnectionRef = connectionRef != null && !connectionRef.isBlank();
        if (hasConnection == hasConnectionRef) {
            throw new IllegalArgumentException("Relational target must define exactly one of 'connection' or 'connectionRef'.");
        }
        if (hasConnection) {
            connection.validate();
        }
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("Relational target table must be provided.");
        }
        if (getFields() == null || getFields().isEmpty()) {
            throw new IllegalArgumentException("Relational target fields must be provided.");
        }
        if (writeMode != WriteMode.INSERT) {
            throw new IllegalArgumentException("Phase 1 relational target support only supports INSERT mode.");
        }
        if (batchSize == null || batchSize <= 0) {
            throw new IllegalArgumentException("Relational target batchSize must be greater than zero.");
        }
    }
}


