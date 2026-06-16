package com.etl.config.relational;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelationalConnectionConfigTest {

    private static final String USERNAME_PROP = "etl.test.sql.username";
    private static final String PASSWORD_PROP = "etl.test.sql.password";

    @Test
    void validateRejectsMissingVendor() {
        RelationalConnectionConfig connection = sqlServerConnection();
        connection.setVendor(null);
        connection.setJdbcUrl(null);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, connection::validate);
        assertEquals("Relational connection vendor must be provided (or inferable from jdbcUrl/connectionString).", ex.getMessage());
    }

    @Test
    void validateAllowsVendorInferenceFromConnectionString() {
        RelationalConnectionConfig connection = sqlServerConnection();
        connection.setVendor(null);
        connection.setHost(null);
        connection.setDatabase(null);
        connection.setUsername(null);
        connection.setPassword(null);
        connection.setConnectionString("jdbc:sqlserver://localhost:1433;databaseName=testdb;encrypt=true;trustServerCertificate=true;user=sa;password=secret");

        connection.validate();

        assertEquals(DatabaseVendor.SQLSERVER, connection.getResolvedVendor());
        assertEquals("sa", connection.resolveUsername());
        assertEquals("secret", connection.resolvePassword());
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
    void validateRejectsPlaceholderJdbcTemplateValues() {
        RelationalConnectionConfig connection = sqlServerConnection();
        connection.setJdbcUrl("jdbc:sqlserver://<SQLSERVER_HOST>:1433;databaseName=<SQLSERVER_DATABASE>;encrypt=true;trustServerCertificate=true");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, connection::validate);
        assertEquals(
                "Relational connection jdbcUrl still contains a placeholder value 'jdbc:sqlserver://<SQLSERVER_HOST>:1433;databaseName=<SQLSERVER_DATABASE>;encrypt=true;trustServerCertificate=true'. Replace template tokens like <...> with real environment-specific connection settings before runtime.",
                ex.getMessage()
        );
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

    @Test
    void validateAllowsCredentialsFromConfiguredEnvVarReferences() {
        RelationalConnectionConfig connection = sqlServerConnection();
        connection.setUsername(null);
        connection.setPassword(null);
        connection.setUsernameEnvVar(USERNAME_PROP);
        connection.setPasswordEnvVar(PASSWORD_PROP);

        System.setProperty(USERNAME_PROP, "env-user");
        System.setProperty(PASSWORD_PROP, "env-pass");
        try {
            connection.validate();
            assertEquals("env-user", connection.resolveUsername());
            assertEquals("env-pass", connection.resolvePassword());
        } finally {
            System.clearProperty(USERNAME_PROP);
            System.clearProperty(PASSWORD_PROP);
        }
    }

    @Test
    void validateRejectsMissingCredentialEnvVarReference() {
        RelationalConnectionConfig connection = sqlServerConnection();
        connection.setUsername(null);
        connection.setUsernameEnvVar("etl.test.missing.username");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, connection::validate);
        assertTrue(ex.getMessage().contains("usernameEnvVar 'etl.test.missing.username'"));
    }

    @Test
    void buildDataSourceAllowsConnectionStringOnlyCredentials() {
        RelationalConnectionConfig connection = sqlServerConnection();
        connection.setVendor(null);
        connection.setHost(null);
        connection.setDatabase(null);
        connection.setUsername(null);
        connection.setPassword(null);
        connection.setConnectionString("jdbc:sqlserver://localhost:1433;databaseName=testdb;encrypt=true;trustServerCertificate=true;user=sa;password=secret");

        DriverManagerDataSource dataSource = (DriverManagerDataSource) RelationalDataSourceFactory.buildDataSource(connection);

        assertEquals("jdbc:sqlserver://localhost:1433;databaseName=testdb;encrypt=true;trustServerCertificate=true;user=sa;password=secret", dataSource.getUrl());
        assertEquals("sa", dataSource.getUsername());
        assertEquals("secret", dataSource.getPassword());
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


