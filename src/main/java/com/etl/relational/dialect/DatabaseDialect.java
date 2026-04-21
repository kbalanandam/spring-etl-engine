package com.etl.relational.dialect;

import com.etl.config.target.RelationalTargetConfig;

public interface DatabaseDialect {
    String qualifyTableName(RelationalTargetConfig config);

    default String qualifyTableName(String schema, String table) {
        if (schema != null && !schema.isBlank()) {
            return quoteIdentifier(schema) + "." + quoteIdentifier(table);
        }
        return quoteIdentifier(table);
    }

    String quoteIdentifier(String identifier);
}

