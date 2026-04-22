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
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelationalLargeVolumeFlowTest {

    private static final String JDBC_URL = "jdbc:h2:mem:relational_large_flow_test;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
    private static final int TOTAL_ROWS = 20_000;
    private static final int WRITE_BATCH_SIZE = 500;

    @Test
    void streamsTwentyThousandRowsFromRelationalSourceToRelationalTarget() throws Exception {
        setupTables();
        seedSourceTable();

        RelationalSourceConfig sourceConfig = relationalSourceConfig();
        RelationalTargetConfig targetConfig = relationalTargetConfig();
        ProcessorConfig processorConfig = processorConfig();

        DynamicReaderFactory readerFactory = new DynamicReaderFactory(List.of(new RelationalDynamicReader<>()));
        DynamicWriterFactory writerFactory = new DynamicWriterFactory(List.of(new RelationalDynamicWriter()));
        DynamicProcessorFactory processorFactory = new DynamicProcessorFactory(Map.of("default", new DefaultDynamicProcessor()));

        ResolvedModelMetadata metadata = GeneratedModelClassResolver.resolveMetadata(sourceConfig, targetConfig);
        JdbcCursorItemReader<Customers> reader = (JdbcCursorItemReader<Customers>) readerFactory.createReader(sourceConfig, Customers.class);
        ItemProcessor<Object, Object> processor = processorFactory.getProcessor(processorConfig, sourceConfig, targetConfig, metadata);
        ItemWriter<Object> writer = writerFactory.createWriter(targetConfig, CustomersSql.class);

        reader.open(new ExecutionContext());
        List<Object> batch = new ArrayList<>(targetConfig.getBatchSize());
        try {
            Customers item;
            while ((item = reader.read()) != null) {
                batch.add(processor.process(item));
                if (batch.size() == targetConfig.getBatchSize()) {
                    writer.write(new Chunk<>(new ArrayList<>(batch)));
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                writer.write(new Chunk<>(new ArrayList<>(batch)));
            }
        } finally {
            reader.close();
        }

        assertEquals(TOTAL_ROWS, rowCount("SELECT COUNT(*) FROM customers_target"));
        assertEquals("1:Customer 1:customer1@example.com", singleRow("SELECT id, name, email FROM customers_target WHERE id = 1"));
        assertEquals(
                TOTAL_ROWS + ":Customer " + TOTAL_ROWS + ":customer" + TOTAL_ROWS + "@example.com",
                singleRow("SELECT id, name, email FROM customers_target WHERE id = " + TOTAL_ROWS)
        );
    }

    private static void setupTables() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS customers_source");
            statement.execute("DROP TABLE IF EXISTS customers_target");
            statement.execute("CREATE TABLE customers_source (id INT, name VARCHAR(255), email VARCHAR(255))");
            statement.execute("CREATE TABLE customers_target (id INT, name VARCHAR(255), email VARCHAR(255))");
        }
    }

    private static void seedSourceTable() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO customers_source (id, name, email) VALUES (?, ?, ?)")) {
            for (int i = 1; i <= TOTAL_ROWS; i++) {
                ps.setInt(1, i);
                ps.setString(2, "Customer " + i);
                ps.setString(3, "customer" + i + "@example.com");
                ps.addBatch();

                if (i % WRITE_BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private static int rowCount(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static String singleRow(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getInt("id") + ":" + rs.getString("name") + ":" + rs.getString("email");
        }
    }

    private static RelationalSourceConfig relationalSourceConfig() {
        return new RelationalSourceConfig(
                "Customers",
                "com.etl.model.source",
                fields(),
                connection(),
                "customers_source",
                null,
                null,
                "SELECT COUNT(*) FROM customers_source",
                500,
                null
        );
    }

    private static RelationalTargetConfig relationalTargetConfig() {
        return new RelationalTargetConfig(
                "CustomersSql",
                "com.etl.model.target",
                fields(),
                connection(),
                "customers_target",
                null,
                "insert",
                WRITE_BATCH_SIZE
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

    private static RelationalConnectionConfig connection() {
        RelationalConnectionConfig connection = new RelationalConnectionConfig();
        connection.setVendor("h2");
        connection.setJdbcUrl(JDBC_URL);
        connection.setUsername("sa");
        connection.setPassword("");
        connection.setDriverClassName("org.h2.Driver");
        return connection;
    }
}

