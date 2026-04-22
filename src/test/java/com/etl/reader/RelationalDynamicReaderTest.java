package com.etl.reader;

import com.etl.config.ColumnConfig;
import com.etl.config.relational.RelationalConnectionConfig;
import com.etl.config.source.RelationalSourceConfig;
import com.etl.model.source.Customers;
import com.etl.reader.impl.CsvDynamicReader;
import com.etl.reader.impl.RelationalDynamicReader;
import com.etl.reader.impl.XmlDynamicReader;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.database.JdbcCursorItemReader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RelationalDynamicReaderTest {

    private static final String JDBC_URL = "jdbc:h2:mem:relational_reader_test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

    private final DynamicReaderFactory factory = new DynamicReaderFactory(
            List.of(new CsvDynamicReader<>(), new XmlDynamicReader<>(), new RelationalDynamicReader<>())
    );

    @Test
    void createsRelationalReaderAndReadsRowsFromTable() throws Exception {
        setupCustomersTable();
        RelationalSourceConfig config = relationalSourceConfig(null);

        ItemReader<Customers> reader = factory.createReader(config, Customers.class);
        assertNotNull(reader);
        assertInstanceOf(JdbcCursorItemReader.class, reader);

        JdbcCursorItemReader<Customers> jdbcReader = (JdbcCursorItemReader<Customers>) reader;
        jdbcReader.open(new ExecutionContext());

        List<Customers> records = new ArrayList<>();
        Customers record;
        while ((record = jdbcReader.read()) != null) {
            records.add(record);
        }
        jdbcReader.close();

        assertEquals(3, records.size());
        assertEquals(1, records.get(0).getId());
        assertEquals("John Doe", records.get(0).getName());
        assertEquals("john@example.com", records.get(0).getEmail());
    }

    @Test
    void readsRowsFromExplicitQueryAndHonorsMaxRows() throws Exception {
        setupCustomersTable();
        RelationalSourceConfig config = relationalSourceConfig("SELECT id, name, email FROM customers ORDER BY id");
        config.setMaxRows(2);

        JdbcCursorItemReader<Customers> reader = (JdbcCursorItemReader<Customers>) factory.createReader(config, Customers.class);
        reader.open(new ExecutionContext());

        List<Customers> records = new ArrayList<>();
        Customers record;
        while ((record = reader.read()) != null) {
            records.add(record);
        }
        reader.close();

        assertEquals(2, records.size());
        assertEquals(1, records.get(0).getId());
        assertEquals(2, records.get(1).getId());
    }

    private static void setupCustomersTable() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS customers");
            statement.execute("CREATE TABLE customers (id INT, name VARCHAR(255), email VARCHAR(255))");
            statement.execute("INSERT INTO customers (id, name, email) VALUES (1, 'John Doe', 'john@example.com')");
            statement.execute("INSERT INTO customers (id, name, email) VALUES (2, 'Jane Doe', 'jane@example.com')");
            statement.execute("INSERT INTO customers (id, name, email) VALUES (3, 'Ravi Kumar', 'ravi@example.com')");
        }
    }

    private static RelationalSourceConfig relationalSourceConfig(String query) {
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
                query == null ? "customers" : null,
                null,
                query,
                null,
                100,
                null
        );
    }
}

