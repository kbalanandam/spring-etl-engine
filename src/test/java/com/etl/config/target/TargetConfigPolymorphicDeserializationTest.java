package com.etl.config.target;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TargetConfigPolymorphicDeserializationTest {

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
}


