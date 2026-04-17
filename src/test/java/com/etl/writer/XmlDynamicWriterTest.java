package com.etl.writer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import com.etl.config.ColumnConfig;
import com.etl.config.target.XmlTargetConfig;
import com.etl.model.target.Customer;
import com.etl.model.target.Customers;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.xml.StaxEventItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SuppressWarnings({"rawtypes", "unchecked"})
@SpringBootTest
class XmlDynamicWriterTest {

    @Autowired
    private DynamicWriterFactory factory;

    @Test
    void testXmlWriterCreation() throws Exception {
        XmlTargetConfig config = getXmlTargetConfig();
        ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
        assertNotNull(writer);
        System.out.println("XML Writer created successfully: " + writer.getClass().getName());
        try {
            if (writer instanceof StaxEventItemWriter<?> xmlWriter) {
                xmlWriter.afterPropertiesSet();
                ExecutionContext executionContext = new ExecutionContext();
                xmlWriter.open(executionContext);
                Customer customer = new Customer();
                customer.setId(1);
                customer.setName("Jane Doe");
                customer.setEmail("jane@example.com");
                Customers customers = new Customers();
                customers.getCustomer().add(customer);
                xmlWriter.write(new Chunk(List.of(customer)));
                xmlWriter.close();
            } else {
                System.out.println("Writer is not a StaxEventItemWriter!");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Assertions.fail("Failed to write records to XML: " + e.getMessage());
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
