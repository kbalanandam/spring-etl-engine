package com.etl.reader;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import jakarta.annotation.Nonnull;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.etl.config.ColumnConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.model.source.Customers;

@SpringBootTest
class DynamicReaderFactoryTest {

    @Autowired
    private DynamicReaderFactory factory;

    @Test
    void testCsvReaderCreation() throws Exception {
        CsvSourceConfig config = getCsvSourceConfig();

        ItemReader<Customers> reader = factory.createReader(config, Customers.class);
        assertNotNull(reader);
        System.out.println("CSV Reader created successfully: " + reader.getClass().getName());
        try {
            if (reader instanceof FlatFileItemReader<Customers> flatReader) {

                flatReader.afterPropertiesSet();
                ExecutionContext executionContext = new ExecutionContext();
                flatReader.open(executionContext);

                Customers record;
                while ((record = flatReader.read()) != null) {
                    System.out.println(record);
                }

                flatReader.close();
            } else {
                System.out.println("Reader is not a FlatFileItemReader!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail("Failed to read records from CSV: " + e.getMessage());
        }
    }

    @Nonnull
    private static CsvSourceConfig getCsvSourceConfig() {
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
        config.setFilePath("C:/ETLDemo/data/input/customers.csv");
        config.setDelimiter(",");
        config.setSourceName("customers");
        return config;
    }
}
