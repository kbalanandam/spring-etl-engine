package com.etl.config.source;

import com.etl.config.ColumnConfig;
import com.etl.config.relational.RelationalConnectionConfig;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelationalSourceConfigTest {

    private static final String JDBC_URL = "jdbc:h2:mem:relational_source_count_test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

    @Test
    void getRecordCountUsesTableCountWhenTableSourceIsConfigured() throws Exception {
        setupCustomersTable();
        RelationalSourceConfig config = relationalTableConfig();

        assertEquals(3, config.getRecordCount());
    }

    @Test
    void getRecordCountReturnsUnknownForQuerySourceWithoutExplicitCountQuery() {
        RelationalSourceConfig config = relationalQueryConfig("SELECT id, name, email FROM customers", null);

        assertEquals(-1, config.getRecordCount());
    }

    @Test
    void getRecordCountUsesExplicitCountQueryForQuerySources() throws Exception {
        setupCustomersTable();
        RelationalSourceConfig config = relationalQueryConfig(
                "SELECT id, name, email FROM customers",
                "SELECT COUNT(*) FROM customers"
        );

        assertEquals(3, config.getRecordCount());
    }

    @Test
    void getRecordCountUsesConnectionSchemaWhenSourceSchemaIsNotConfigured() throws Exception {
        setupCustomersTable("dbo.customers");
        RelationalSourceConfig config = relationalTableConfig();
        config.getConnection().setSchema("dbo");

        assertEquals(3, config.getRecordCount());
    }

    private static void setupCustomersTable() throws Exception {
        setupCustomersTable("customers");
    }

    private static void setupCustomersTable(String qualifiedTableName) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS dbo");
            statement.execute("DROP TABLE IF EXISTS dbo.customers");
            statement.execute("DROP TABLE IF EXISTS customers");
            statement.execute("CREATE TABLE " + qualifiedTableName + " (id INT, name VARCHAR(255), email VARCHAR(255))");
            statement.execute("INSERT INTO " + qualifiedTableName + " (id, name, email) VALUES (1, 'John Doe', 'john@example.com')");
            statement.execute("INSERT INTO " + qualifiedTableName + " (id, name, email) VALUES (2, 'Jane Doe', 'jane@example.com')");
            statement.execute("INSERT INTO " + qualifiedTableName + " (id, name, email) VALUES (3, 'Ravi Kumar', 'ravi@example.com')");
        }
    }

    private static RelationalSourceConfig relationalTableConfig() {
        RelationalSourceConfig config = relationalQueryConfig(null, null);
        config.setTable("customers");
        return config;
    }

    private static RelationalSourceConfig relationalQueryConfig(String query, String countQuery) {
        ColumnConfig id = new ColumnConfig();
        id.setName("id");
        id.setType("int");

        ColumnConfig name = new ColumnConfig();
        name.setName("name");
        name.setType("String");

        ColumnConfig email = new ColumnConfig();
        email.setName("email");
        email.setType("String");

        RelationalConnectionConfig connection = new RelationalConnectionConfig();
        connection.setVendor("h2");
        connection.setJdbcUrl(JDBC_URL);
        connection.setUsername("sa");
        connection.setPassword("");
        connection.setDriverClassName("org.h2.Driver");

        return new RelationalSourceConfig(
                "Customers",
                "com.etl.model.source",
                List.of(id, name, email),
                connection,
                null,
                null,
                query,
                countQuery,
                100,
                null
        );
    }
}


