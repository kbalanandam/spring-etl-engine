package com.etl.config;

import com.etl.config.exception.ConfigException;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderJobConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsReferencedConfigsFromJobConfigUsingRelativePaths() throws IOException {
        Path sourceConfig = tempDir.resolve("source-config.yaml");
        Path targetConfig = tempDir.resolve("target-config.yaml");
        Path processorConfig = tempDir.resolve("processor-config.yaml");
        Path jobConfig = tempDir.resolve("job-config.yaml");

        Files.writeString(sourceConfig, """
                sources:
                  - format: csv
                    sourceName: Customers
                    packageName: com.etl.model.source
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
                  - format: csv
                    targetName: CustomersOut
                    packageName: com.etl.model.target
                    filePath: output/customers.csv
                    delimiter: ","
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
                    target: CustomersOut
                    fields:
                      - from: id
                        to: id
                      - from: name
                        to: name
                """);

        Files.writeString(jobConfig, """
                name: csv-to-csv-test
                sourceConfigPath: source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                """);

        ConfigLoader loader = new ConfigLoader();
        ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
        ReflectionTestUtils.setField(loader, "allowDemoFallback", false);
        ReflectionTestUtils.setField(loader, "sourceConfigPath", "missing-source.yaml");
        ReflectionTestUtils.setField(loader, "targetConfigPath", "missing-target.yaml");
        ReflectionTestUtils.setField(loader, "processorConfigPath", "missing-processor.yaml");

        RunConfigurationMetadata runConfigurationMetadata = loader.runConfigurationMetadata();
        SourceWrapper sourceWrapper = loader.sourceWrapper();
        TargetWrapper targetWrapper = loader.targetWrapper();
        ProcessorConfig loadedProcessorConfig = loader.processorConfig();

		assertEquals("csv-to-csv-test", runConfigurationMetadata.scenarioName());
		assertEquals(jobConfig.toString(), runConfigurationMetadata.jobConfigPath());
    assertFalse(runConfigurationMetadata.demoFallbackMode());

        assertEquals(1, sourceWrapper.getSources().size());
        assertEquals("Customers", sourceWrapper.getSources().get(0).getSourceName());

        assertEquals(1, targetWrapper.getTargets().size());
        assertEquals("CustomersOut", targetWrapper.getTargets().get(0).getTargetName());

        assertEquals("default", loadedProcessorConfig.getType());
        assertEquals(1, loadedProcessorConfig.getMappings().size());
        assertEquals("Customers", loadedProcessorConfig.getMappings().get(0).getSource());
        assertEquals("CustomersOut", loadedProcessorConfig.getMappings().get(0).getTarget());
    }

    @Test
    void failsFastWhenJobConfigReferencesMissingFiles() throws IOException {
        Path jobConfig = tempDir.resolve("job-config.yaml");
        Files.writeString(jobConfig, """
                name: broken-job
                sourceConfigPath: missing-source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                """);

        ConfigLoader loader = new ConfigLoader();
        ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
        ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

        assertThrows(ConfigException.class, loader::sourceWrapper);
    }

  @Test
  void failsFastWhenJobConfigIsMissingAndDemoFallbackIsDisabled() {
    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", "   ");
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::sourceWrapper);
    assertTrue(exception.getMessage().contains("etl.config.job"));
  }

  @Test
  void allowsBundledFallbackWhenDemoFallbackIsEnabled() {
    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", "");
    ReflectionTestUtils.setField(loader, "allowDemoFallback", true);
    ReflectionTestUtils.setField(loader, "sourceConfigPath", "missing-source.yaml");
    ReflectionTestUtils.setField(loader, "targetConfigPath", "missing-target.yaml");
    ReflectionTestUtils.setField(loader, "processorConfigPath", "missing-processor.yaml");

    RunConfigurationMetadata runConfigurationMetadata = loader.runConfigurationMetadata();
    SourceWrapper sourceWrapper = loader.sourceWrapper();
    TargetWrapper targetWrapper = loader.targetWrapper();
    ProcessorConfig processorConfig = loader.processorConfig();

    assertEquals("demo-fallback", runConfigurationMetadata.scenarioName());
    assertEquals("", runConfigurationMetadata.jobConfigPath());
    assertTrue(runConfigurationMetadata.demoFallbackMode());
    assertNotNull(sourceWrapper);
    assertNotNull(targetWrapper);
    assertNotNull(processorConfig);
    assertTrue(sourceWrapper.getSources() != null && !sourceWrapper.getSources().isEmpty());
    assertTrue(targetWrapper.getTargets() != null && !targetWrapper.getTargets().isEmpty());
    assertTrue(processorConfig.getMappings() != null && !processorConfig.getMappings().isEmpty());
  }

  @Test
  void explicitMissingJobConfigNeverFallsBackEvenWhenDemoFallbackIsEnabled() {
    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", tempDir.resolve("missing-job-config.yaml").toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", true);

    ConfigException exception = assertThrows(ConfigException.class, loader::sourceWrapper);
    assertTrue(exception.getMessage().contains("Configured job config YAML not found"));
  }

  @Test
  void derivesScenarioNameFromFolderWhenJobConfigNameIsBlank() throws IOException {
    Path scenarioDir = tempDir.resolve("customer-load");
    Files.createDirectories(scenarioDir);

    Files.writeString(scenarioDir.resolve("source-config.yaml"), """
            sources:
              - format: csv
                sourceName: Customers
                packageName: com.etl.model.source
                filePath: input/customers.csv
                delimiter: ","
                fields:
                  - name: id
                    type: int
            """);

    Files.writeString(scenarioDir.resolve("target-config.yaml"), """
            targets:
              - format: csv
                targetName: CustomersOut
                packageName: com.etl.model.target
                filePath: output/customers.csv
                delimiter: ","
                fields:
                  - name: id
                    type: int
            """);

    Files.writeString(scenarioDir.resolve("processor-config.yaml"), """
            type: default
            mappings:
              - source: Customers
                target: CustomersOut
                fields:
                  - from: id
                    to: id
            """);

    Path jobConfig = scenarioDir.resolve("job-config.yaml");
    Files.writeString(jobConfig, """
            name:
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    RunConfigurationMetadata metadata = loader.runConfigurationMetadata();

    assertEquals("customer-load", metadata.scenarioName());
    assertEquals(jobConfig.toString(), metadata.jobConfigPath());
    assertFalse(metadata.demoFallbackMode());
  }

  @Test
  void failsFastWhenJobConfigYamlIsMalformed() throws IOException {
    Path malformedJobConfig = tempDir.resolve("job-config.yaml");
    Files.writeString(malformedJobConfig, "name: broken-job\nsourceConfigPath source-config.yaml\n");

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", malformedJobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    assertThrows(ConfigException.class, loader::sourceWrapper);
  }
}


