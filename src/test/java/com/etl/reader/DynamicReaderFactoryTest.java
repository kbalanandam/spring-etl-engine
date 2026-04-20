package com.etl.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.etl.config.ColumnConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.model.source.Customers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.file.FlatFileItemReader;

import com.etl.reader.impl.CsvDynamicReader;

class DynamicReaderFactoryTest {

    private final DynamicReaderFactory factory = new DynamicReaderFactory(List.of(new CsvDynamicReader<>()));

    @Test
    @SuppressWarnings("unchecked")
    void createsCsvReaderAndReadsRecords(@TempDir Path tempDir) throws Exception {
    Path inputFile = tempDir.resolve("customers.csv");
    Files.writeString(inputFile, "id,name,email\n1,John Doe,john@example.com\n2,Jane Doe,jane@example.com\n");

    CsvSourceConfig config = getCsvSourceConfig(inputFile);

    ItemReader<Customers> reader = factory.createReader(config, Customers.class);
    assertNotNull(reader);

    assertInstanceOf(FlatFileItemReader.class, reader);
    FlatFileItemReader<Customers> flatReader = (FlatFileItemReader<Customers>) reader;
    flatReader.afterPropertiesSet();
    flatReader.open(new ExecutionContext());

    List<Customers> records = new ArrayList<>();
    Customers record;
    while ((record = flatReader.read()) != null) {
      records.add(record);
    }
    flatReader.close();

    assertEquals(2, records.size());
    assertEquals(1, records.get(0).getId());
    assertEquals("John Doe", records.get(0).getName());
    assertEquals("john@example.com", records.get(0).getEmail());
    assertEquals(2, records.get(1).getId());
    assertEquals("Jane Doe", records.get(1).getName());
    assertEquals("jane@example.com", records.get(1).getEmail());
    }

  private static CsvSourceConfig getCsvSourceConfig(Path inputFile) {
        CsvSourceConfig config = new CsvSourceConfig();

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
        config.setFields(columnConfig);
            config.setFilePath(inputFile.toString());
        config.setDelimiter(",");
        config.setSourceName("customers");
            config.setPackageName("com.etl.model.source");
        return config;
    }
}
