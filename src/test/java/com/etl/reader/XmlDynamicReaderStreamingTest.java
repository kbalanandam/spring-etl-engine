package com.etl.reader;

import com.etl.config.source.XmlSourceConfig;
import com.etl.model.target.Customer;
import com.etl.model.target.Customers;
import com.etl.reader.impl.XmlDynamicReader;
import com.etl.source.xml.runtime.XmlFlatteningResult;
import com.etl.source.xml.runtime.XmlSourceRuntimeContext;
import com.etl.source.xml.strategy.JobSpecificXmlSourceStrategy;
import com.etl.source.xml.strategy.JobSpecificXmlStrategyResolver;
import com.etl.source.xml.strategy.NestedXmlSourceStrategy;
import com.etl.source.xml.strategy.XmlSourceStrategyRegistry;
import com.etl.source.xml.strategy.XmlSourceStrategySelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.context.support.StaticApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class XmlDynamicReaderStreamingTest {

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void buffersMultipleFlattenedRowsPerFragmentWithoutLosingOrder() throws Exception {
        Path inputFile = tempDir.resolve("customers.xml");
        Files.writeString(inputFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <Customers>
                  <Customer>
                    <id>1</id>
                    <name>John Doe</name>
                    <email>john@example.com</email>
                  </Customer>
                  <Customer>
                    <id>2</id>
                    <name>Jane Doe</name>
                    <email>jane@example.com</email>
                  </Customer>
                </Customers>
                """);

        XmlSourceStrategySelector selector = new XmlSourceStrategySelector(
                new XmlSourceStrategyRegistry(List.of(new DuplicatingNestedXmlSourceStrategy())),
                new JobSpecificXmlStrategyResolver(new StaticApplicationContext())
        );

            Class<Object> recordClass = (Class<Object>) (Class<?>) Customer.class;
            ItemReader<Object> reader = new XmlDynamicReader<>(selector).getReader(xmlConfig(inputFile), recordClass);
        assertInstanceOf(ItemStream.class, reader);

        ItemStream itemStream = (ItemStream) reader;
        itemStream.open(new ExecutionContext());
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            Object item;
            while ((item = reader.read()) != null) {
                assertInstanceOf(Map.class, item);
                rows.add((Map<String, Object>) item);
            }
        } finally {
            itemStream.close();
        }

        assertEquals(List.of(
                row(1, "primary", "john@example.com"),
                row(1, "audit", "john@example.com"),
                row(2, "primary", "jane@example.com"),
                row(2, "audit", "jane@example.com")
        ), rows);
    }

  @Test
  @SuppressWarnings("unchecked")
  void preservesRootWrapperInputForJobSpecificXmlStrategies() throws Exception {
    Path inputFile = tempDir.resolve("job-specific-customers.xml");
    Files.writeString(inputFile, """
        <?xml version="1.0" encoding="UTF-8"?>
        <Customers>
          <Customer>
            <id>1</id>
            <name>John Doe</name>
            <email>john@example.com</email>
          </Customer>
          <Customer>
            <id>2</id>
            <name>Jane Doe</name>
            <email>jane@example.com</email>
          </Customer>
        </Customers>
        """);

    RecordingJobSpecificXmlSourceStrategy customStrategy = new RecordingJobSpecificXmlSourceStrategy();
    StaticApplicationContext applicationContext = new StaticApplicationContext();
    applicationContext.getBeanFactory().registerSingleton("customerSummaryStrategy", customStrategy);

    XmlSourceStrategySelector selector = new XmlSourceStrategySelector(
        new XmlSourceStrategyRegistry(List.of(new NestedXmlSourceStrategy())),
        new JobSpecificXmlStrategyResolver(applicationContext)
    );

    Class<Object> recordClass = (Class<Object>) (Class<?>) Customer.class;
    ItemReader<Object> reader = new XmlDynamicReader<>(selector).getReader(jobSpecificXmlConfig(inputFile), recordClass);
    assertInstanceOf(ItemStream.class, reader);

    ItemStream itemStream = (ItemStream) reader;
    itemStream.open(new ExecutionContext());
    try {
      Object item = reader.read();
      assertInstanceOf(Map.class, item);
      Map<String, Object> row = (Map<String, Object>) item;
      assertEquals(2, row.get("customerCount"));
      assertEquals("john@example.com", row.get("firstEmail"));
      assertEquals(Customers.class.getName(), row.get("inputType"));
      assertNull(reader.read());
    } finally {
      itemStream.close();
    }

    assertEquals(Customers.class, customStrategy.lastInputClass);
    assertEquals(2, customStrategy.lastCustomerCount);
  }

    private XmlSourceConfig xmlConfig(Path inputFile) {
        XmlSourceConfig config = new XmlSourceConfig();
        config.setSourceName("Customers");
        config.setPackageName("com.etl.model.target");
        config.setFilePath(inputFile.toString());
        config.setRootElement("Customers");
        config.setRecordElement("Customer");
        config.setFlatteningStrategy("NestedXml");
        return config;
    }

  private XmlSourceConfig jobSpecificXmlConfig(Path inputFile) {
    XmlSourceConfig config = xmlConfig(inputFile);
    config.setFlatteningStrategy("JobSpecificXml");
    config.setJobSpecificStrategyBean("customerSummaryStrategy");
    return config;
  }

    private static Map<String, Object> row(int customerId, String phase, String email) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("customerId", customerId);
        row.put("phase", phase);
        row.put("email", email);
        return row;
    }

    private static final class DuplicatingNestedXmlSourceStrategy extends NestedXmlSourceStrategy {

        @Override
        public XmlFlatteningResult flatten(XmlSourceRuntimeContext context, Object xmlRoot) {
            Customer customer = (Customer) xmlRoot;
            return XmlFlatteningResult.ofRows(List.of(
                    row(customer.getId(), "primary", customer.getEmail()),
                    row(customer.getId(), "audit", customer.getEmail())
            ));
        }
    }

  private static final class RecordingJobSpecificXmlSourceStrategy extends JobSpecificXmlSourceStrategy {

    private Class<?> lastInputClass;
    private int lastCustomerCount;

    @Override
    public String getStrategyName() {
      return "customerSummaryStrategy";
    }

    @Override
    public XmlFlatteningResult flatten(XmlSourceRuntimeContext context, Object xmlRoot) {
      lastInputClass = xmlRoot.getClass();
      Customers customers = (Customers) xmlRoot;
      lastCustomerCount = customers.getCustomer().size();
      Customer firstCustomer = customers.getCustomer().get(0);
      return XmlFlatteningResult.ofRows(List.of(summaryRow(lastCustomerCount, firstCustomer.getEmail(), lastInputClass.getName())));
    }

    private Map<String, Object> summaryRow(int customerCount, String firstEmail, String inputType) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("customerCount", customerCount);
      row.put("firstEmail", firstEmail);
      row.put("inputType", inputType);
      return row;
    }
  }
}



