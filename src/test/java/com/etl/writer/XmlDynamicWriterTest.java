package com.etl.writer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.etl.config.ColumnConfig;
import com.etl.config.target.XmlTargetConfig;
import com.etl.model.target.Customer;
import com.etl.model.target.Customers;
import com.etl.writer.impl.SingleObjectXmlWriter;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.xml.StaxEventItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class XmlDynamicWriterTest {

    @Autowired
    private DynamicWriterFactory factory;

    @Test
    void testXmlWriterCreation() throws Exception {
        XmlTargetConfig config = getXmlTargetConfig();
        ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
        assertNotNull(writer);
        assertInstanceOf(SingleObjectXmlWriter.class, writer);
        try {
            Customer customer = new Customer();
            customer.setId(1);
            customer.setName("Jane Doe");
            customer.setEmail("jane@example.com");

            Customers customers = new Customers();
            customers.setCustomer(List.of(customer));

            writer.write(new Chunk<>(List.of(customers)));

            String xml = Files.readString(Path.of("C:/ETLDemo/data/output/customers_test.xml"));
            assertTrue(xml.contains("<Customers>"));
            assertTrue(xml.contains("<Customer>"));
            assertTrue(xml.contains("Jane Doe"));
        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail("Failed to write records to XML: " + e.getMessage());
        }
    }

    @Test
    void testXmlChunkWriterCreationForRecordClass() throws Exception {
        XmlTargetConfig config = getXmlTargetConfig();
        ItemWriter<Object> writer = factory.createWriter(config, Customer.class);
        assertNotNull(writer);
        assertInstanceOf(StaxEventItemWriter.class, writer);

        try {
            @SuppressWarnings("unchecked")
            StaxEventItemWriter<Object> xmlWriter = (StaxEventItemWriter<Object>) writer;
            xmlWriter.afterPropertiesSet();
            xmlWriter.open(new ExecutionContext());

            Customer customer = new Customer();
            customer.setId(2);
            customer.setName("Chunk Jane");
            customer.setEmail("chunk.jane@example.com");

            xmlWriter.write(new Chunk<>(List.of(customer)));
            xmlWriter.close();

            String xml = Files.readString(Path.of("C:/ETLDemo/data/output/customers_test.xml"));
            assertTrue(xml.contains("<Customers>"));
            assertTrue(xml.contains("<Customer>"));
            assertTrue(xml.contains("Chunk Jane"));
        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail("Failed to write chunked records to XML: " + e.getMessage());
        }
    }

    private static XmlTargetConfig getXmlTargetConfig() {
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
        return new XmlTargetConfig(
                "customers",
                "com.etl.model.target",
                columnConfig,
                "C:/ETLDemo/data/output/customers_test.xml",
                "Customers",
                "Customer"
        );
    }
}
