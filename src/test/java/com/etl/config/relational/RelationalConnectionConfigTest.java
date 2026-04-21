package com.etl.config.relational;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RelationalConnectionConfigTest {

    @Test
    void validateRejectsMissingVendor() {
        RelationalConnectionConfig connection = sqlServerConnection();
        connection.setVendor(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, connection::validate);
        assertEquals("Relational connection vendor must be provided.", ex.getMessage());
    }

    @Test
    void validateRejectsInvalidPort() {
        RelationalConnectionConfig connection = sqlServerConnection();
        connection.setPort(0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, connection::validate);
        assertEquals("Relational connection port must be greater than zero when provided.", ex.getMessage());
    }

    @Test
    void validateRejectsSqlServerConnectionWithoutJdbcUrlOrHost() {
        RelationalConnectionConfig connection = sqlServerConnection();
        connection.setJdbcUrl(null);
        connection.setHost(" ");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, connection::validate);
        assertEquals("Relational connection host must be provided when jdbcUrl is not configured.", ex.getMessage());
    }

    @Test
    void resolveJdbcUrlBuildsSqlServerUrlFromHostAndDatabase() {
        RelationalConnectionConfig connection = sqlServerConnection();
        connection.setJdbcUrl(null);

        assertEquals(
                "jdbc:sqlserver://localhost:1433;databaseName=testdb;encrypt=true;trustServerCertificate=true",
                RelationalDataSourceFactory.resolveJdbcUrl(connection)
        );
    }

    private static RelationalConnectionConfig sqlServerConnection() {
        RelationalConnectionConfig connection = new RelationalConnectionConfig();
        connection.setVendor("sqlserver");
        connection.setHost("localhost");
        connection.setPort(1433);
        connection.setDatabase("testdb");
        connection.setUsername("sa");
        connection.setPassword("secret");
        return connection;
    }
}


