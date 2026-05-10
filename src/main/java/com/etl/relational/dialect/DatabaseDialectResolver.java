package com.etl.relational.dialect;

import com.etl.config.relational.DatabaseVendor;
import com.etl.exception.RelationalException;

public final class DatabaseDialectResolver {

    private DatabaseDialectResolver() {
    }

    public static DatabaseDialect resolve(DatabaseVendor vendor) {
        if (vendor == null) {
            throw new RelationalException("Database vendor must be provided to resolve a dialect.");
        }
        return switch (vendor) {
            case SQLSERVER -> new SqlServerDialect();
            case H2 -> new H2Dialect();
        };
    }
}

