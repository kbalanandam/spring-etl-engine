package com.etl.relational.dialect;

import com.etl.config.relational.DatabaseVendor;

public final class DatabaseDialectResolver {

    private DatabaseDialectResolver() {
    }

    public static DatabaseDialect resolve(DatabaseVendor vendor) {
        return switch (vendor) {
            case SQLSERVER -> new SqlServerDialect();
            case H2 -> new H2Dialect();
        };
    }
}

