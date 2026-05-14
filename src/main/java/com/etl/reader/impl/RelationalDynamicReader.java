package com.etl.reader.impl;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.FieldDefinition;
import com.etl.config.relational.RelationalDataSourceFactory;
import com.etl.config.source.RelationalSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.exception.RelationalException;
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

/**
 * Runtime relational reader builder for database-backed sources.
 *
 * <p>This reader turns the selected relational source config into a
 * {@link JdbcCursorItemReader}. It owns datasource creation, dialect-aware SQL
 * selection, optional fetch/max row tuning, and column-to-field mapping into the
 * generated source model class.</p>
 *
 * <p>The reader supports two relational source styles:</p>
 * <ul>
 *   <li>an explicit SQL query provided by config</li>
 *   <li>a generated {@code SELECT} statement based on configured fields, schema, and table</li>
 * </ul>
 */
@Component("relational")
public class RelationalDynamicReader<T> implements DynamicReader<T> {

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.RELATIONAL;
    }

    @Override
    public ItemReader<T> getReader(SourceConfig config, Class<T> clazz) {
        try {
            if (config == null || clazz == null) {
                throw new RelationalException("SourceConfig and target class must not be null.");
            }

            RelationalSourceConfig relationalConfig = (RelationalSourceConfig) config;
            relationalConfig.validate();

            // Resolve the database dialect once so identifier quoting and qualified
            // table naming stay vendor-correct for generated fallback SQL.
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
            // Row mapping stays config-driven: each configured field name is treated as
            // the JDBC column label and the generated model property name.
            reader.setRowMapper(buildRowMapper(relationalConfig, clazz));
            reader.afterPropertiesSet();
            return new RuntimeCategorizingItemStreamReader<>(reader, relationalConfig.getSourceName());
        } catch (RelationalException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            throw new RelationalException("Invalid relational source configuration: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RelationalException("Failed to initialize relational reader for source '"
                    + (config == null ? "unnamed" : config.getSourceName()) + "'.", e);
        }
    }

    /**
     * Resolves the SQL statement used by the cursor reader.
     *
     * <p>If the source config already declares a query, that query is used verbatim.
     * Otherwise the reader synthesizes a simple dialect-aware {@code SELECT} based on
     * configured field names and the effective schema/table reference.</p>
     */
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

    /**
     * Builds a reflection-based row mapper for the generated source model class.
     *
     * <p>The mapping contract assumes relational field names already describe both the
     * selected column labels and the generated Java property names.</p>
     */
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

