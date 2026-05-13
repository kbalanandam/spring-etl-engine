package com.etl.writer.impl;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.FieldDefinition;
import com.etl.config.relational.RelationalConnectionConfig;
import com.etl.config.relational.RelationalDataSourceFactory;
import com.etl.config.target.RelationalTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.exception.RelationalException;
import com.etl.enums.ModelFormat;
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

/**
 * Runtime relational writer builder for database-backed targets.
 *
 * <p>This writer turns the selected relational target config into a
 * {@link JdbcBatchItemWriter}. It owns datasource creation, dialect-aware insert SQL,
 * and parameter binding from generated target model fields into named JDBC parameters.</p>
 */
@Component("relationalWriter")
public class RelationalDynamicWriter implements DynamicWriter {

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.RELATIONAL;
    }

    @Override
    public ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) {
        try {
            RelationalTargetConfig relationalConfig = (RelationalTargetConfig) config;
            relationalConfig.validate();

            RelationalConnectionConfig connection = relationalConfig.getConnection();
            // Resolve the database dialect once so table qualification and identifier
            // quoting remain vendor-correct for generated INSERT statements.
            DatabaseDialect dialect = DatabaseDialectResolver.resolve(connection.getResolvedVendor());
            DataSource dataSource = RelationalDataSourceFactory.buildDataSource(connection);

            JdbcBatchItemWriter<Object> writer = new JdbcBatchItemWriterBuilder<>()
                    .dataSource(dataSource)
                    .sql(buildInsertSql(relationalConfig, dialect))
                    .itemSqlParameterSourceProvider(item -> {
                        MapSqlParameterSource params = new MapSqlParameterSource();
                        // Parameter names are driven by target field names, which act as both
                        // JDBC placeholder names and generated Java property names.
                        for (FieldDefinition field : relationalConfig.getFields()) {
                            params.addValue(field.getName(), ReflectionUtils.getFieldValue(item, field.getName()));
                        }
                        return params;
                    })
                    .assertUpdates(false)
                    .build();
            writer.afterPropertiesSet();
            return writer;
        } catch (RelationalException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new RelationalException("Invalid relational target configuration: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RelationalException("Failed to initialize relational writer for target '"
                    + (config == null ? "unnamed" : config.getTargetName()) + "'.", e);
        }
    }


    /**
     * Builds the dialect-aware INSERT statement for the relational target contract.
     */
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

