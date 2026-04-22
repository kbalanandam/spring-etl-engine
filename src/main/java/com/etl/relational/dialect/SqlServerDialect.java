package com.etl.relational.dialect;

import com.etl.config.target.RelationalTargetConfig;

public class SqlServerDialect implements DatabaseDialect {

    @Override
    public String qualifyTableName(RelationalTargetConfig config) {
        return DatabaseDialect.super.qualifyTableName(config.getEffectiveSchema(), config.getTable());
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return "[" + identifier + "]";
    }
}

