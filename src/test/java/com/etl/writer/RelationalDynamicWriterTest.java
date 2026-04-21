package com.etl.writer;

import com.etl.config.ColumnConfig;
import com.etl.config.relational.RelationalConnectionConfig;
import com.etl.config.target.RelationalTargetConfig;
import com.etl.model.target.CustomersSql;
import com.etl.writer.impl.RelationalDynamicWriter;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcBatchItemWriter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RelationalDynamicWriterTest {

    private static final String JDBC_URL = "jdbc:h2:mem:relational_writer_test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

    @Test
    void createsRelationalWriterAndPersistsRowsToDatabase() throws Exception {
        setupCustomersTable("customers");

        DynamicWriterFactory factory = new DynamicWriterFactory(List.of(new RelationalDynamicWriter()));
        RelationalTargetConfig config = relationalTargetConfig(null);

        ItemWriter<Object> writer = factory.createWriter(config, CustomersSql.class);
        assertNotNull(writer);
        assertInstanceOf(JdbcBatchItemWriter.class, writer);

        CustomersSql customer = new CustomersSql();
        customer.setId(1);
        customer.setName("John Doe");
        customer.setEmail("john@example.com");

        writer.write(new Chunk<>(List.of(customer)));

        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id, name, email FROM customers")) {
            org.junit.jupiter.api.Assertions.assertTrue(rs.next());
            assertEquals(1, rs.getInt("id"));
            assertEquals("John Doe", rs.getString("name"));
            assertEquals("john@example.com", rs.getString("email"));
        }
    }

    @Test
    void usesConnectionSchemaWhenTargetSchemaIsNotConfigured() throws Exception {
        setupCustomersTable("dbo.customers");

        DynamicWriterFactory factory = new DynamicWriterFactory(List.of(new RelationalDynamicWriter()));
        RelationalTargetConfig config = relationalTargetConfig("dbo");

        ItemWriter<Object> writer = factory.createWriter(config, CustomersSql.class);

        CustomersSql customer = new CustomersSql();
        customer.setId(2);
        customer.setName("Jane Doe");
        customer.setEmail("jane@example.com");

        writer.write(new Chunk<>(List.of(customer)));

        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id, name, email FROM dbo.customers")) {
            org.junit.jupiter.api.Assertions.assertTrue(rs.next());
            assertEquals(2, rs.getInt("id"));
            assertEquals("Jane Doe", rs.getString("name"));
            assertEquals("jane@example.com", rs.getString("email"));
        }
    }

    @Test
    void rejectsSqlServerTargetWithoutJdbcUrlOrHostDatabase() {
        DynamicWriterFactory factory = new DynamicWriterFactory(List.of(new RelationalDynamicWriter()));
        RelationalTargetConfig config = relationalTargetConfig(null);
        config.getConnection().setVendor("sqlserver");
        config.getConnection().setJdbcUrl(null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> factory.createWriter(config, CustomersSql.class)
        );

        assertEquals(
                "Relational connection host must be provided when jdbcUrl is not configured.",
                ex.getMessage()
        );
    }

    private static void setupCustomersTable(String qualifiedTableName) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS dbo");
            statement.execute("DROP TABLE IF EXISTS dbo.customers");
            statement.execute("DROP TABLE IF EXISTS customers");
            statement.execute("CREATE TABLE " + qualifiedTableName + " (id INT, name VARCHAR(255), email VARCHAR(255))");
        }
    }

    private static RelationalTargetConfig relationalTargetConfig(String connectionSchema) {
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
        connection.setSchema(connectionSchema);

        return new RelationalTargetConfig(
                "CustomersSql",
                "com.etl.model.target",
                List.of(id, name, email),
                connection,
                "customers",
                null,
                "insert",
                100
        );
    }
}


