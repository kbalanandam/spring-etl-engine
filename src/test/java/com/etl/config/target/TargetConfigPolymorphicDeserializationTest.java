package com.etl.config.target;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class TargetConfigPolymorphicDeserializationTest {

    @Test
    void deserializesXmlTargetConfigFromYaml() throws Exception {
        String yaml = """
                targets:
                  - format: xml
                    targetName: CustomersXml
                    packageName: com.etl.model.target
                    filePath: output/customers.xml
                    rootElement: Customers
                    recordElement: Customer
                    modelDefinitionPath: definitions/customer-target-model.yaml
                    fields:
                      - name: id
                        type: int
                      - name: name
                        type: String
                """;

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();

        TargetWrapper wrapper = mapper.readValue(yaml, TargetWrapper.class);
        XmlTargetConfig xmlTarget = assertInstanceOf(XmlTargetConfig.class, wrapper.getTargets().get(0));

        assertEquals("CustomersXml", xmlTarget.getTargetName());
        assertEquals("output/customers.xml", xmlTarget.getFilePath());
        assertEquals("Customers", xmlTarget.getRootElement());
        assertEquals("Customer", xmlTarget.getRecordElement());
        assertEquals("definitions/customer-target-model.yaml", xmlTarget.getModelDefinitionPath());
    }

    @Test
    void deserializesRelationalTargetConfigFromYaml() throws Exception {
        String yaml = """
                targets:
                  - format: relational
                    targetName: CustomersSql
                    packageName: com.etl.model.target
                    schema: dbo
                    table: customers
                    writeMode: insert
                    batchSize: 50
                    connection:
                      vendor: sqlserver
                      host: 192.168.50.195
                      port: 1433
                      database: testdb
                      username: sa
                      password: secret
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

        TargetWrapper wrapper = mapper.readValue(yaml, TargetWrapper.class);
        TargetConfig target = wrapper.getTargets().get(0);

        RelationalTargetConfig relationalTarget = assertInstanceOf(RelationalTargetConfig.class, target);
        assertEquals("CustomersSql", relationalTarget.getTargetName());
        assertEquals("customers", relationalTarget.getTable());
        assertEquals(WriteMode.INSERT, relationalTarget.getWriteMode());
        assertEquals("sqlserver", relationalTarget.getConnection().getVendor());
        assertEquals("dbo", relationalTarget.getEffectiveSchema());
    }

    @Test
    void deserializesJsonTargetConfigFromYamlWithoutPackageName() throws Exception {
        String yaml = """
                targets:
                  - format: json
                    targetName: EventsJson
                    filePath: output/events.json
                    fields:
                      - name: eventCode
                        type: String
                      - name: eventTime
                        type: String
                """;

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();

        TargetWrapper wrapper = mapper.readValue(yaml, TargetWrapper.class);
        JsonTargetConfig jsonTarget = assertInstanceOf(JsonTargetConfig.class, wrapper.getTargets().get(0));

        assertEquals("EventsJson", jsonTarget.getTargetName());
        assertEquals("output/events.json", jsonTarget.getFilePath());
        assertNull(jsonTarget.getPackageName());
        assertEquals(2, jsonTarget.getFields().size());
    }

    @Test
    void defaultsCsvTargetDelimiterToCommaWhenOmitted() throws Exception {
        String yaml = """
                targets:
                  - format: csv
                    targetName: EventsCsv
                    filePath: output/events.csv
                    fields:
                      - name: eventCode
                        type: String
                      - name: eventTime
                        type: String
                """;

        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.findAndRegisterModules();

        TargetWrapper wrapper = mapper.readValue(yaml, TargetWrapper.class);
        CsvTargetConfig csvTarget = assertInstanceOf(CsvTargetConfig.class, wrapper.getTargets().get(0));

        assertEquals("EventsCsv", csvTarget.getTargetName());
        assertEquals("output/events.csv", csvTarget.getFilePath());
        assertEquals(CsvTargetConfig.DEFAULT_DELIMITER, csvTarget.getDelimiter());
    }
}


