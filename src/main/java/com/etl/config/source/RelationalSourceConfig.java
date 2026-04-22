package com.etl.config.source;

import com.etl.config.ColumnConfig;
import com.etl.config.FieldDefinition;
import com.etl.config.relational.RelationalConnectionConfig;
import com.etl.config.relational.RelationalDataSourceFactory;
import com.etl.enums.ModelFormat;
import com.etl.relational.dialect.DatabaseDialect;
import com.etl.relational.dialect.DatabaseDialectResolver;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Relational source configuration for table/query based reads.
 */
public class RelationalSourceConfig extends SourceConfig {

    private RelationalConnectionConfig connection;
    private String table;
    private String schema;
    private String query;
    private String countQuery;
    private Integer fetchSize;
    private Integer maxRows;

    public RelationalSourceConfig() {
        super();
    }

    @JsonCreator
    public RelationalSourceConfig(
            @JsonProperty("sourceName") String sourceName,
            @JsonProperty("packageName") String packageName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("connection") RelationalConnectionConfig connection,
            @JsonProperty("table") String table,
            @JsonProperty("schema") String schema,
            @JsonProperty("query") String query,
            @JsonProperty("countQuery") String countQuery,
            @JsonProperty("fetchSize") Integer fetchSize,
            @JsonProperty("maxRows") Integer maxRows
    ) {
        super(sourceName, packageName, fields);
        this.connection = connection;
        this.table = table;
        this.schema = schema;
        this.query = query;
        this.countQuery = countQuery;
        this.fetchSize = fetchSize;
        this.maxRows = maxRows;
    }

    public RelationalConnectionConfig getConnection() {
        return connection;
    }

    public void setConnection(RelationalConnectionConfig connection) {
        this.connection = connection;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getCountQuery() {
        return countQuery;
    }

    public void setCountQuery(String countQuery) {
        this.countQuery = countQuery;
    }

    public Integer getFetchSize() {
        return fetchSize;
    }

    public void setFetchSize(Integer fetchSize) {
        this.fetchSize = fetchSize;
    }

    public Integer getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(Integer maxRows) {
        this.maxRows = maxRows;
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

    public boolean hasTable() {
        return table != null && !table.isBlank();
    }

    public boolean hasQuery() {
        return query != null && !query.isBlank();
    }

    public void validate() {
        if (connection == null) {
            throw new IllegalArgumentException("Relational source connection must be provided.");
        }
        connection.validate();
        if (getFields() == null || getFields().isEmpty()) {
            throw new IllegalArgumentException("Relational source fields must be provided.");
        }
        if (hasTable() == hasQuery()) {
            throw new IllegalArgumentException("Relational source must define exactly one of 'table' or 'query'.");
        }
        if (fetchSize != null && fetchSize <= 0) {
            throw new IllegalArgumentException("Relational source fetchSize must be greater than zero when provided.");
        }
        if (maxRows != null && maxRows <= 0) {
            throw new IllegalArgumentException("Relational source maxRows must be greater than zero when provided.");
        }
    }

    @Override
    public int getRecordCount() {
        validate();

        if (countQuery != null && !countQuery.isBlank()) {
            return executeCount(countQuery);
        }

        if (hasQuery()) {
            return -1;
        }

        DatabaseDialect dialect = DatabaseDialectResolver.resolve(connection.getResolvedVendor());
        String sql = "SELECT COUNT(*) FROM " + dialect.qualifyTableName(getEffectiveSchema(), table);
        return executeCount(sql);
    }

    private int executeCount(String sql) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(RelationalDataSourceFactory.buildDataSource(connection));
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class);
        return count != null ? count : 0;
    }
}

