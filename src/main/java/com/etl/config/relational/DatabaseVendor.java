package com.etl.config.relational;

public enum DatabaseVendor {
    SQLSERVER,
    H2;

    public static DatabaseVendor fromString(String value) {
        for (DatabaseVendor vendor : values()) {
            if (vendor.name().equalsIgnoreCase(value)) {
                return vendor;
            }
        }
        throw new IllegalArgumentException("Unsupported database vendor: " + value);
    }
}

