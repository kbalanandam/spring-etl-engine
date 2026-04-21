package com.etl.config.relational;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Shared JDBC data source resolution for relational source and target flows.
 */
public final class RelationalDataSourceFactory {

    private RelationalDataSourceFactory() {
    }

    public static DataSource buildDataSource(RelationalConnectionConfig connection) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(resolveDriverClassName(connection));
        dataSource.setUrl(resolveJdbcUrl(connection));
        dataSource.setUsername(connection.getUsername());
        dataSource.setPassword(connection.getPassword());
        return dataSource;
    }

    public static String resolveDriverClassName(RelationalConnectionConfig connection) {
        if (connection.getDriverClassName() != null && !connection.getDriverClassName().isBlank()) {
            return connection.getDriverClassName();
        }

        return switch (connection.getResolvedVendor()) {
            case SQLSERVER -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case H2 -> "org.h2.Driver";
        };
    }

    public static String resolveJdbcUrl(RelationalConnectionConfig connection) {
        if (connection.getJdbcUrl() != null && !connection.getJdbcUrl().isBlank()) {
            return connection.getJdbcUrl();
        }

        DatabaseVendor vendor = connection.getResolvedVendor();
        return switch (vendor) {
            case SQLSERVER -> "jdbc:sqlserver://" + connection.getHost() + ":" + defaultPort(connection.getPort())
                    + ";databaseName=" + connection.getDatabase()
                    + ";encrypt=true;trustServerCertificate=true";
            case H2 -> "jdbc:h2:mem:relational_runtime;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        };
    }

    private static int defaultPort(Integer configuredPort) {
        return configuredPort != null && configuredPort > 0 ? configuredPort : 1433;
    }
}

