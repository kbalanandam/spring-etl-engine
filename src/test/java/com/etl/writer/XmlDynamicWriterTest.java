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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.xml.StaxEventItemWriter;

class XmlDynamicWriterTest {

    private final DynamicWriterFactory factory = new DynamicWriterFactory(List.of(new com.etl.writer.impl.XmlDynamicWriter()));

    @Test
    void createsWrapperXmlWriterForTaskletMode(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_test.xml");
    XmlTargetConfig config = getXmlTargetConfig(outputFile);
        ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
        assertNotNull(writer);
        assertInstanceOf(SingleObjectXmlWriter.class, writer);

    Customer customer = new Customer();
    customer.setId(1);
    customer.setName("Jane Doe");
    customer.setEmail("jane@example.com");

    Customers customers = new Customers();
    customers.setCustomer(List.of(customer));

    writer.write(new Chunk<>(List.of(customers)));

    String xml = Files.readString(outputFile);
    assertTrue(xml.contains("<Customers>"));
    assertTrue(xml.contains("<Customer>"));
    assertTrue(xml.contains("Jane Doe"));
    }

    @Test
    void createsChunkXmlWriterForRecordClass(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_test.xml");
    XmlTargetConfig config = getXmlTargetConfig(outputFile);
        ItemWriter<Object> writer = factory.createWriter(config, Customer.class);
        assertNotNull(writer);
        assertInstanceOf(StaxEventItemWriter.class, writer);

    StaxEventItemWriter<Object> xmlWriter = (StaxEventItemWriter<Object>) writer;
    xmlWriter.afterPropertiesSet();
    xmlWriter.open(new ExecutionContext());

    Customer firstCustomer = new Customer();
    firstCustomer.setId(2);
    firstCustomer.setName("Chunk Jane");
    firstCustomer.setEmail("chunk.jane@example.com");

    Customer secondCustomer = new Customer();
    secondCustomer.setId(3);
    secondCustomer.setName("Chunk John");
    secondCustomer.setEmail("chunk.john@example.com");

    xmlWriter.write(new Chunk<>(List.of(firstCustomer, secondCustomer)));
    xmlWriter.close();

    String xml = Files.readString(outputFile);
    assertTrue(xml.contains("<Customers>"));
    assertTrue(xml.contains("<Customer>"));
    assertTrue(xml.contains("Chunk Jane"));
    assertTrue(xml.contains("Chunk John"));
    }

  private static XmlTargetConfig getXmlTargetConfig(Path outputFile) {
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
                outputFile.toString(),
                "Customers",
                "Customer"
        );
    }
}
