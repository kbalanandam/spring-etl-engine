package com.etl.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.etl.config.ColumnConfig;
import com.etl.config.target.CsvTargetConfig;
import com.etl.model.source.Customers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;

import com.etl.writer.impl.CsvDynamicWriter;

@SuppressWarnings({"rawtypes", "unchecked"})
class DynamicWriterFactoryTest {

    private final DynamicWriterFactory factory = new DynamicWriterFactory(List.of(new CsvDynamicWriter()));

    @Test
    void createsCsvWriterAndPersistsOutput(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_test.csv");
    CsvTargetConfig config = getCsvTargetConfig(outputFile);

    ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
    assertNotNull(writer);

    FlatFileItemWriter<?> flatWriter = assertInstanceOf(FlatFileItemWriter.class, writer);
    flatWriter.afterPropertiesSet();
    flatWriter.open(new ExecutionContext());

    Customers customer = new Customers();
    customer.setId(1);
    customer.setName("John Doe");
    customer.setEmail("john@example.com");

    flatWriter.write(new Chunk(List.of(customer)));
    flatWriter.close();

    assertEquals("1,John Doe,john@example.com", Files.readString(outputFile).trim());
    }

  private static CsvTargetConfig getCsvTargetConfig(Path outputFile) {
        ColumnConfig col1 = new ColumnConfig();
        col1.setName("id");
        col1.setType("integer");

        ColumnConfig col2 = new ColumnConfig();
        col2.setName("name");
        col2.setType("string");

        ColumnConfig col3 = new ColumnConfig();
        col3.setName("email");
        col3.setType("string");

        List<ColumnConfig> columnConfig = List.of(col1, col2, col3);
        return new CsvTargetConfig(
                "customers",
                "com.etl.model.target",
                columnConfig,
                outputFile.toString(),
                ","
        );
    }
}
