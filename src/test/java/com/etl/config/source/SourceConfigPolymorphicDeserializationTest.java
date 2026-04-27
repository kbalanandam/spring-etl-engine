package com.etl.config.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceConfigPolymorphicDeserializationTest {

    @Test
    void deserializesRelationalSourceConfigFromYaml() throws Exception {
        String yaml = """
                sources:
                  - format: relational
                    sourceName: Customers
                    packageName: com.etl.model.source
                    table: customers
                    schema: dbo
                    fetchSize: 200
                    maxRows: 1000
                    connection:
                      vendor: h2
                      jdbcUrl: jdbc:h2:mem:relational_source_binding;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false
                      username: sa
                      password: ""
                    fields:
                      - name: id
                        type: int
                      - name: name
                        type: String
                      - name: email
                        type: String
                """;

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();

        SourceWrapper wrapper = mapper.readValue(yaml, SourceWrapper.class);
        SourceConfig source = wrapper.getSources().get(0);

        RelationalSourceConfig relationalSource = assertInstanceOf(RelationalSourceConfig.class, source);
        assertEquals("Customers", relationalSource.getSourceName());
        assertEquals("customers", relationalSource.getTable());
        assertEquals("dbo", relationalSource.getEffectiveSchema());
        assertEquals(200, relationalSource.getFetchSize());
        assertEquals(1000, relationalSource.getMaxRows());
        assertEquals("h2", relationalSource.getConnection().getVendor());
    }

  @Test
  void deserializesCsvSourceArchiveConfigFromYaml() throws Exception {
    String yaml = """
    sources:
      - format: csv
        sourceName: Events
        packageName: com.etl.model.source
        filePath: input/events.csv
        delimiter: ","
        archive:
          enabled: true
          successPath: target/archive/success/
          namePattern: "{originalName}-{timestamp}"
        validation:
          allowEmpty: false
          requireHeaderMatch: true
        fields:
          - name: id
            type: String
          - name: eventTime
            type: String
    """;

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    mapper.findAndRegisterModules();

    SourceWrapper wrapper = mapper.readValue(yaml, SourceWrapper.class);
    CsvSourceConfig csvSource = assertInstanceOf(CsvSourceConfig.class, wrapper.getSources().get(0));

    assertEquals("Events", csvSource.getSourceName());
    assertEquals("target/archive/success/", csvSource.getArchive().getSuccessPath());
    assertEquals("{originalName}-{timestamp}", csvSource.getArchive().getNamePattern());
    assertFalse(csvSource.getValidation().isAllowEmpty());
    assertTrue(csvSource.getValidation().isRequireHeaderMatch());
  }

  @Test
  void deserializesXmlSourceConfigFromYaml() throws Exception {
    String yaml = """
        sources:
          - format: xml
            sourceName: CustomersXml
            packageName: com.etl.model.source
            filePath: input/customers.xml
            rootElement: Customers
            recordElement: Customer
            fields:
              - name: id
                type: int
              - name: name
                type: String
        """;

    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    mapper.findAndRegisterModules();

    SourceWrapper wrapper = mapper.readValue(yaml, SourceWrapper.class);
    XmlSourceConfig xmlSource = assertInstanceOf(XmlSourceConfig.class, wrapper.getSources().get(0));

    assertEquals("CustomersXml", xmlSource.getSourceName());
    assertEquals("input/customers.xml", xmlSource.getFilePath());
    assertEquals("Customers", xmlSource.getRootElement());
    assertEquals("Customer", xmlSource.getRecordElement());
  }
}

