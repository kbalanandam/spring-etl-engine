package com.etl.config;

import com.etl.config.relational.RelationalConnectionConfig;
import com.etl.config.source.validation.SourceValidationService;
import com.etl.exception.config.ConfigException;
import com.etl.processor.ProcessorExtensionDefaults;
import com.etl.processor.transform.TransformEvaluator;
import com.etl.processor.validation.ValidationRuleEvaluator;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderRelationalConnectionRefTest {

    @TempDir
    Path tempDir;

    @Test
    void failsFastWhenSelectedJobRelationalConnectionRefIsMissingAtStartup() throws IOException {
        Path sourceConfig = tempDir.resolve("source-config.yaml");
        Path targetConfig = tempDir.resolve("target-config.yaml");
        Path processorConfig = tempDir.resolve("processor-config.yaml");
        Path jobConfig = tempDir.resolve("job-config.yaml");

        Files.writeString(sourceConfig, """
                sources:
                  - format: csv
                    sourceName: Customers
                    filePath: input/customers.csv
                    delimiter: ","
                    fields:
                      - name: id
                        type: int
                      - name: name
                        type: String
                """);

        Files.writeString(targetConfig, """
                targets:
                  - format: relational
                    targetName: CustomersSql
                    table: Customers
                    writeMode: insert
                    batchSize: 100
                    connectionRef: sqlserver-main
                    fields:
                      - name: id
                        type: int
                      - name: name
                        type: String
                """);

        Files.writeString(processorConfig, """
                type: default
                mappings:
                  - source: Customers
                    target: CustomersSql
                    fields:
                      - from: id
                        to: id
                      - from: name
                        to: name
                """);

        Files.writeString(jobConfig, """
                name: missing-connection-ref
                sourceConfigPath: source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                steps:
                  - name: customers-step
                    source: Customers
                    target: CustomersSql
                """);

        EtlConfigProperties properties = new EtlConfigProperties();
        properties.setJob(jobConfig.toString());
        properties.setAllowDemoFallback(false);

        ConfigLoader loader = new ConfigLoader(
                properties,
                new SourceValidationService(),
                new ValidationRuleEvaluator(ProcessorExtensionDefaults.defaultValidationRules(new FileIngestionRuntimeSupport())),
                new TransformEvaluator(ProcessorExtensionDefaults.defaultTransforms())
        );

        ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
        assertTrue(exception.getMessage().contains("Missing relational connectionRef 'sqlserver-main'"));
        assertTrue(exception.getMessage().contains("missing-connection-ref"));
        assertTrue(exception.getMessage().contains("CustomersSql"));
    }

    @Test
    void resolvesSelectedJobRelationalConnectionRefWhenRegistryEntryIsConfigured() throws IOException {
        Path sourceConfig = tempDir.resolve("source-config.yaml");
        Path targetConfig = tempDir.resolve("target-config.yaml");
        Path processorConfig = tempDir.resolve("processor-config.yaml");
        Path jobConfig = tempDir.resolve("job-config.yaml");

        Files.writeString(sourceConfig, """
                sources:
                  - format: csv
                    sourceName: Customers
                    filePath: input/customers.csv
                    delimiter: ","
                    fields:
                      - name: id
                        type: int
                      - name: name
                        type: String
                """);

        Files.writeString(targetConfig, """
                targets:
                  - format: relational
                    targetName: CustomersSql
                    table: Customers
                    writeMode: insert
                    batchSize: 100
                    connectionRef: sqlserver-main
                    fields:
                      - name: id
                        type: int
                      - name: name
                        type: String
                """);

        Files.writeString(processorConfig, """
                type: default
                mappings:
                  - source: Customers
                    target: CustomersSql
                    fields:
                      - from: id
                        to: id
                      - from: name
                        to: name
                """);

        Files.writeString(jobConfig, """
                name: resolved-connection-ref
                sourceConfigPath: source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                steps:
                  - name: customers-step
                    source: Customers
                    target: CustomersSql
                """);

        EtlConfigProperties properties = new EtlConfigProperties();
        properties.setJob(jobConfig.toString());
        properties.setAllowDemoFallback(false);
        EtlConfigProperties.Relational relational = new EtlConfigProperties.Relational();
        RelationalConnectionConfig connection = new RelationalConnectionConfig();
        connection.setVendor("h2");
        connection.setJdbcUrl("jdbc:h2:mem:config_loader_connection_ref;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false");
        connection.setUsername("sa");
        connection.setPassword("");
        connection.setDriverClassName("org.h2.Driver");
        relational.setConnections(Map.of("sqlserver-main", connection));
        properties.setRelational(relational);

        ConfigLoader loader = new ConfigLoader(
                properties,
                new SourceValidationService(),
                new ValidationRuleEvaluator(ProcessorExtensionDefaults.defaultValidationRules(new FileIngestionRuntimeSupport())),
                new TransformEvaluator(ProcessorExtensionDefaults.defaultTransforms())
        );

        ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
        assertTrue(exception.getMessage().contains("Source model class not found"));
        assertTrue(!exception.getMessage().contains("Missing relational connectionRef 'sqlserver-main'"));
    }
}






