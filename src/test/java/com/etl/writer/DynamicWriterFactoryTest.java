package com.etl.writer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import com.etl.config.ColumnConfig;
import com.etl.config.target.CsvTargetConfig;
import com.etl.model.source.Customers;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SuppressWarnings({"rawtypes", "unchecked"})
@SpringBootTest
class DynamicWriterFactoryTest {

    @Autowired
    private DynamicWriterFactory factory;

    @Test
    void testCsvWriterCreation() throws Exception {
        CsvTargetConfig config = getCsvTargetConfig();
        ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
        assertNotNull(writer);
        System.out.println("CSV Writer created successfully: " + writer.getClass().getName());
        try {
            if (writer instanceof FlatFileItemWriter<?> flatWriter) {
                flatWriter.afterPropertiesSet();
                ExecutionContext executionContext = new ExecutionContext();
                flatWriter.open(executionContext);
                Customers customer = new Customers();
                customer.setId(1);
                customer.setName("John Doe");
                customer.setEmail("john@example.com");
                flatWriter.write(new Chunk(List.of(customer)));
                flatWriter.close();
            } else {
                System.out.println("Writer is not a FlatFileItemWriter!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail("Failed to write records to CSV: " + e.getMessage());
        }
    }

    private static CsvTargetConfig getCsvTargetConfig() {
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
                "C:/ETLDemo/data/output/customers_test.csv",
                ","
        );
    }
}
