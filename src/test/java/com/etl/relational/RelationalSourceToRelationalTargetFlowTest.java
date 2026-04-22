package com.etl.relational;

import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.ColumnConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.relational.RelationalConnectionConfig;
import com.etl.config.source.RelationalSourceConfig;
import com.etl.config.target.RelationalTargetConfig;
import com.etl.model.source.Customers;
import com.etl.model.target.CustomersSql;
import com.etl.processor.DynamicProcessorFactory;
import com.etl.processor.impl.DefaultDynamicProcessor;
import com.etl.reader.DynamicReaderFactory;
import com.etl.reader.impl.RelationalDynamicReader;
import com.etl.writer.DynamicWriterFactory;
import com.etl.writer.impl.RelationalDynamicWriter;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcCursorItemReader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelationalSourceToRelationalTargetFlowTest {

    private static final String JDBC_URL = "jdbc:h2:mem:relational_flow_test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

    @Test
    void readsFromRelationalSourceProcessesAndWritesToRelationalTarget() throws Exception {
        setupTables("customers_source", "customers_target");

        assertEquals(expectedRows(), runFlow(
                relationalSourceConfig(null),
                relationalTargetConfig(null),
                "SELECT id, name, email FROM customers_target ORDER BY id"
        ));
    }

    @Test
    void usesConnectionSchemaWhenSourceAndTargetSchemaAreNotConfigured() throws Exception {
        setupTables("dbo.customers_source", "dbo.customers_target");

        assertEquals(expectedRows(), runFlow(
                relationalSourceConfig("dbo"),
                relationalTargetConfig("dbo"),
                "SELECT id, name, email FROM dbo.customers_target ORDER BY id"
        ));
    }

    private static List<String> runFlow(RelationalSourceConfig sourceConfig,
                                        RelationalTargetConfig targetConfig,
                                        String verificationSql) throws Exception {
        ProcessorConfig processorConfig = processorConfig();

        DynamicReaderFactory readerFactory = new DynamicReaderFactory(List.of(new RelationalDynamicReader<>()));
        DynamicWriterFactory writerFactory = new DynamicWriterFactory(List.of(new RelationalDynamicWriter()));
        DynamicProcessorFactory processorFactory = new DynamicProcessorFactory(Map.of("default", new DefaultDynamicProcessor()));

        ResolvedModelMetadata metadata = GeneratedModelClassResolver.resolveMetadata(sourceConfig, targetConfig);
        JdbcCursorItemReader<Customers> reader = (JdbcCursorItemReader<Customers>) readerFactory.createReader(sourceConfig, Customers.class);
        ItemProcessor<Object, Object> processor = processorFactory.getProcessor(processorConfig, sourceConfig, targetConfig, metadata);

        reader.open(new ExecutionContext());
        List<Object> processedItems = new ArrayList<>();
        try {
            Customers item;
            while ((item = reader.read()) != null) {
                processedItems.add(processor.process(item));
            }
        } finally {
            reader.close();
        }

        writerFactory.createWriter(targetConfig, CustomersSql.class).write(new Chunk<>(processedItems));

        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(verificationSql)) {
            List<String> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(rs.getInt("id") + ":" + rs.getString("name") + ":" + rs.getString("email"));
            }
            return rows;
        }
    }

    private static List<String> expectedRows() {
        return List.of(
                "1:John Doe:john@example.com",
                "2:Jane Doe:jane@example.com",
                "3:Ravi Kumar:ravi@example.com"
        );
    }

    private static void setupTables(String sourceTableName, String targetTableName) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS dbo");
            statement.execute("DROP TABLE IF EXISTS dbo.customers_source");
            statement.execute("DROP TABLE IF EXISTS dbo.customers_target");
            statement.execute("DROP TABLE IF EXISTS customers_source");
            statement.execute("DROP TABLE IF EXISTS customers_target");
            statement.execute("CREATE TABLE " + sourceTableName + " (id INT, name VARCHAR(255), email VARCHAR(255))");
            statement.execute("CREATE TABLE " + targetTableName + " (id INT, name VARCHAR(255), email VARCHAR(255))");
            statement.execute("INSERT INTO " + sourceTableName + " (id, name, email) VALUES (1, 'John Doe', 'john@example.com')");
            statement.execute("INSERT INTO " + sourceTableName + " (id, name, email) VALUES (2, 'Jane Doe', 'jane@example.com')");
            statement.execute("INSERT INTO " + sourceTableName + " (id, name, email) VALUES (3, 'Ravi Kumar', 'ravi@example.com')");
        }
    }

    private static RelationalSourceConfig relationalSourceConfig(String connectionSchema) {
        return new RelationalSourceConfig(
                "Customers",
                "com.etl.model.source",
                fields(),
                connection(connectionSchema),
                "customers_source",
                null,
                null,
                null,
                100,
                null
        );
    }

    private static RelationalTargetConfig relationalTargetConfig(String connectionSchema) {
        return new RelationalTargetConfig(
                "CustomersSql",
                "com.etl.model.target",
                fields(),
                connection(connectionSchema),
                "customers_target",
                null,
                "insert",
                100
        );
    }

    private static ProcessorConfig processorConfig() {
        ProcessorConfig.FieldMapping id = new ProcessorConfig.FieldMapping();
        id.setFrom("id");
        id.setTo("id");

        ProcessorConfig.FieldMapping name = new ProcessorConfig.FieldMapping();
        name.setFrom("name");
        name.setTo("name");

        ProcessorConfig.FieldMapping email = new ProcessorConfig.FieldMapping();
        email.setFrom("email");
        email.setTo("email");

        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Customers");
        mapping.setTarget("CustomersSql");
        mapping.setFields(List.of(id, name, email));

        ProcessorConfig processorConfig = new ProcessorConfig();
        processorConfig.setType("default");
        processorConfig.setMappings(List.of(mapping));
        return processorConfig;
    }

    private static List<ColumnConfig> fields() {
        ColumnConfig id = new ColumnConfig();
        id.setName("id");
        id.setType("int");

        ColumnConfig name = new ColumnConfig();
        name.setName("name");
        name.setType("String");

        ColumnConfig email = new ColumnConfig();
        email.setName("email");
        email.setType("String");

        return List.of(id, name, email);
    }

    private static RelationalConnectionConfig connection(String schema) {
        RelationalConnectionConfig connection = new RelationalConnectionConfig();
        connection.setVendor("h2");
        connection.setJdbcUrl(JDBC_URL);
        connection.setUsername("sa");
        connection.setPassword("");
        connection.setDriverClassName("org.h2.Driver");
        connection.setSchema(schema);
        return connection;
    }
}


