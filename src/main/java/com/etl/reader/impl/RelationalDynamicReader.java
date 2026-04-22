package com.etl.reader.impl;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.FieldDefinition;
import com.etl.config.relational.RelationalDataSourceFactory;
import com.etl.config.source.RelationalSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.enums.ModelFormat;
import com.etl.reader.DynamicReader;
import com.etl.relational.dialect.DatabaseDialect;
import com.etl.relational.dialect.DatabaseDialectResolver;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.util.stream.Collectors;

@Component("relational")
public class RelationalDynamicReader<T> implements DynamicReader<T> {

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.RELATIONAL;
    }

    @Override
    public ItemReader<T> getReader(SourceConfig config, Class<T> clazz) throws Exception {
        if (config == null || clazz == null) {
            throw new IllegalArgumentException("SourceConfig and target class must not be null.");
        }

        RelationalSourceConfig relationalConfig = (RelationalSourceConfig) config;
        relationalConfig.validate();

        DatabaseDialect dialect = DatabaseDialectResolver.resolve(relationalConfig.getConnection().getResolvedVendor());

        JdbcCursorItemReader<T> reader = new JdbcCursorItemReader<>();
        reader.setDataSource(RelationalDataSourceFactory.buildDataSource(relationalConfig.getConnection()));
        reader.setSql(resolveReadSql(relationalConfig, dialect));
        reader.setVerifyCursorPosition(false);
        if (relationalConfig.getFetchSize() != null && relationalConfig.getFetchSize() > 0) {
            reader.setFetchSize(relationalConfig.getFetchSize());
        }
        if (relationalConfig.getMaxRows() != null && relationalConfig.getMaxRows() > 0) {
            reader.setMaxRows(relationalConfig.getMaxRows());
        }
        reader.setRowMapper(buildRowMapper(relationalConfig, clazz));
        reader.afterPropertiesSet();
        return reader;
    }

    private String resolveReadSql(RelationalSourceConfig config, DatabaseDialect dialect) {
        if (config.hasQuery()) {
            return config.getQuery();
        }

        String selectColumns = config.getFields().stream()
                .map(FieldDefinition::getName)
                .map(dialect::quoteIdentifier)
                .collect(Collectors.joining(", "));

        return "SELECT " + selectColumns
                + " FROM " + dialect.qualifyTableName(config.getEffectiveSchema(), config.getTable());
    }

    private RowMapper<T> buildRowMapper(RelationalSourceConfig config, Class<T> clazz) {
        return (ResultSet rs, int rowNum) -> {
            T instance = ReflectionUtils.createInstance(clazz);
            for (FieldDefinition field : config.getFields()) {
                Object value = rs.getObject(field.getName());
                ReflectionUtils.setFieldValue(instance, field.getName(), value);
            }
            return instance;
        };
    }
}

