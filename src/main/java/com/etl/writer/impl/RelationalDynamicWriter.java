package com.etl.writer.impl;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.FieldDefinition;
import com.etl.config.relational.RelationalConnectionConfig;
import com.etl.config.relational.RelationalDataSourceFactory;
import com.etl.config.target.RelationalTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.WriteMode;
import com.etl.relational.dialect.DatabaseDialect;
import com.etl.relational.dialect.DatabaseDialectResolver;
import com.etl.writer.DynamicWriter;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.stream.Collectors;

@Component("relationalWriter")
public class RelationalDynamicWriter implements DynamicWriter {

    @Override
    public String getType() {
        return "relational";
    }

    @Override
    public ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) throws Exception {
        RelationalTargetConfig relationalConfig = (RelationalTargetConfig) config;
        validate(relationalConfig);

        RelationalConnectionConfig connection = relationalConfig.getConnection();
        DatabaseDialect dialect = DatabaseDialectResolver.resolve(connection.getResolvedVendor());
        DataSource dataSource = RelationalDataSourceFactory.buildDataSource(connection);

        JdbcBatchItemWriter<Object> writer = new JdbcBatchItemWriterBuilder<Object>()
                .dataSource(dataSource)
                .sql(buildInsertSql(relationalConfig, dialect))
                .itemSqlParameterSourceProvider(item -> {
                    MapSqlParameterSource params = new MapSqlParameterSource();
                    for (FieldDefinition field : relationalConfig.getFields()) {
                        params.addValue(field.getName(), ReflectionUtils.getFieldValue(item, field.getName()));
                    }
                    return params;
                })
                .assertUpdates(false)
                .build();
        writer.afterPropertiesSet();
        return writer;
    }

    private void validate(RelationalTargetConfig config) {
        if (config.getConnection() == null) {
            throw new IllegalArgumentException("Relational target connection must be provided.");
        }
        if (config.getTable() == null || config.getTable().isBlank()) {
            throw new IllegalArgumentException("Relational target table must be provided.");
        }
        if (config.getFields() == null || config.getFields().isEmpty()) {
            throw new IllegalArgumentException("Relational target fields must be provided.");
        }
        if (config.getWriteMode() != WriteMode.INSERT) {
            throw new IllegalArgumentException("Phase 1 relational target support only supports INSERT mode.");
        }
    }


    private String buildInsertSql(RelationalTargetConfig config, DatabaseDialect dialect) {
        String columns = config.getFields().stream()
                .map(FieldDefinition::getName)
                .map(dialect::quoteIdentifier)
                .collect(Collectors.joining(", "));

        String values = config.getFields().stream()
                .map(FieldDefinition::getName)
                .map(name -> ":" + name)
                .collect(Collectors.joining(", "));

        return "INSERT INTO " + dialect.qualifyTableName(config) + " (" + columns + ") VALUES (" + values + ")";
    }
}

