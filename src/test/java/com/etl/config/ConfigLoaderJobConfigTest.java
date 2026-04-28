package com.etl.config;

import com.etl.config.exception.ConfigException;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.validation.SourceValidationService;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetWrapper;
import com.etl.processor.validation.NotNullProcessorValidationRule;
import com.etl.processor.validation.ProcessorValidationRule;
import com.etl.processor.validation.TimeFormatProcessorValidationRule;
import com.etl.processor.validation.ValidationIssue;
import com.etl.processor.validation.ValidationRuleEvaluator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
                steps:
                  - name: customers-step
                    source: Customers
                    target: CustomersOut
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
    assertEquals(1, runConfigurationMetadata.steps().size());
    assertEquals("customers-step", runConfigurationMetadata.steps().get(0).getName());
    assertEquals("Customers", runConfigurationMetadata.steps().get(0).getSource());
    assertEquals("CustomersOut", runConfigurationMetadata.steps().get(0).getTarget());

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
                steps:
                  - name: customers-step
                    source: Customers
                    target: CustomersOut
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
    assertEquals(List.of("1-Customers-to-Customers", "2-Department-to-Departments"),
            runConfigurationMetadata.steps().stream().map(JobConfig.JobStepConfig::getName).toList());
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
            steps:
              - name: customers-step
                source: Customers
                target: CustomersOut
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    RunConfigurationMetadata metadata = loader.runConfigurationMetadata();

    assertEquals("customer-load", metadata.scenarioName());
    assertEquals(jobConfig.toString(), metadata.jobConfigPath());
    assertFalse(metadata.demoFallbackMode());
    assertEquals(1, metadata.steps().size());
  }

  @Test
  void failsFastWhenJobConfigDoesNotDefineSteps() throws IOException {
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
            """);
    Files.writeString(processorConfig, """
            type: default
            mappings:
              - source: Customers
                target: CustomersOut
                fields:
                  - from: id
                    to: id
            """);
    Files.writeString(jobConfig, """
            name: missing-steps
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::runConfigurationMetadata);
    assertTrue(exception.getMessage().contains("steps"));
  }

  @Test
  void failsFastWhenStepReferencesUnknownSource() throws IOException {
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
            """);
    Files.writeString(processorConfig, """
            type: default
            mappings:
              - source: Customers
                target: CustomersOut
                fields:
                  - from: id
                    to: id
            """);
    Files.writeString(jobConfig, """
            name: bad-step-reference
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            steps:
              - name: customers-step
                source: MissingSource
                target: CustomersOut
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::runConfigurationMetadata);
    assertTrue(exception.getMessage().contains("unknown source"));
  }

  @Test
  void failsFastWhenDuplicateStepNamesAreConfigured() throws IOException {
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
              - format: csv
                sourceName: Department
                packageName: com.etl.model.source
                filePath: input/departments.csv
                delimiter: ","
                fields:
                  - name: id
                    type: int
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
              - format: csv
                targetName: DepartmentsOut
                packageName: com.etl.model.target
                filePath: output/departments.csv
                delimiter: ","
                fields:
                  - name: id
                    type: int
            """);
    Files.writeString(processorConfig, """
            type: default
            mappings:
              - source: Customers
                target: CustomersOut
                fields:
                  - from: id
                    to: id
              - source: Department
                target: DepartmentsOut
                fields:
                  - from: id
                    to: id
            """);
    Files.writeString(jobConfig, """
            name: duplicate-steps
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            steps:
              - name: shared-step
                source: Customers
                target: CustomersOut
              - name: shared-step
                source: Department
                target: DepartmentsOut
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::runConfigurationMetadata);
    assertTrue(exception.getMessage().contains("duplicate step name"));
  }

  @Test
  void failsFastWhenRelationalTargetStillContainsPlaceholderConnectionValues() throws IOException {
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
              - format: relational
                targetName: CustomersSql
                packageName: com.etl.model.target
                schema: dbo
                table: Customers
                writeMode: insert
                batchSize: 100
                connection:
                  vendor: sqlserver
                  jdbcUrl: jdbc:sqlserver://<SQLSERVER_HOST>:1433;databaseName=<SQLSERVER_DATABASE>;encrypt=true;trustServerCertificate=true
                  schema: dbo
                  username: <SQLSERVER_USERNAME>
                  password: <SQLSERVER_PASSWORD>
                  driverClassName: com.microsoft.sqlserver.jdbc.SQLServerDriver
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
            name: csv-to-sqlserver
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            steps:
              - name: customers-to-sql-step
                source: Customers
                target: CustomersSql
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::runConfigurationMetadata);
    assertTrue(exception.getMessage().contains("Invalid relational target configuration"));
    assertTrue(exception.getMessage().contains("csv-to-sqlserver"));
    assertTrue(exception.getMessage().contains("CustomersSql"));
    assertTrue(exception.getMessage().contains("placeholder value"));
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

  @Test
  void failsFastWhenRejectHandlingIsEnabledWithoutOutputPath() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
        sources:
          - format: csv
            sourceName: Events
            packageName: com.etl.model.source
            filePath: input/events.csv
            delimiter: ","
            fields:
              - name: id
                type: String
        """);
    Files.writeString(targetConfig, """
        targets:
          - format: csv
            targetName: EventsCsv
            packageName: com.etl.model.target
            filePath: output/events.csv
            delimiter: ","
            fields:
              - name: id
                type: String
        """);
    Files.writeString(processorConfig, """
        type: default
        rejectHandling:
          enabled: true
        mappings:
          - source: Events
            target: EventsCsv
            fields:
              - from: id
                to: id
        """);
    Files.writeString(jobConfig, """
        name: reject-missing-path
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: events-step
            source: Events
            target: EventsCsv
        """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::processorConfig);
    assertTrue(messageChain(exception).contains("rejectHandling"));
  }

  @Test
  void failsFastWhenTimeFormatRuleIsMissingPattern() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
        sources:
          - format: csv
            sourceName: Events
            packageName: com.etl.model.source
            filePath: input/events.csv
            delimiter: ","
            fields:
              - name: eventTime
                type: String
        """);
    Files.writeString(targetConfig, """
        targets:
          - format: csv
            targetName: EventsCsv
            packageName: com.etl.model.target
            filePath: output/events.csv
            delimiter: ","
            fields:
              - name: eventTime
                type: String
        """);
    Files.writeString(processorConfig, """
        type: default
        mappings:
          - source: Events
            target: EventsCsv
            fields:
              - from: eventTime
                to: eventTime
                rules:
                  - type: timeFormat
        """);
    Files.writeString(jobConfig, """
        name: time-format-missing-pattern
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: events-step
            source: Events
            target: EventsCsv
        """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::processorConfig);
    assertTrue(messageChain(exception).contains("timeFormat"));
    assertTrue(messageChain(exception).contains("pattern"));
  }

  @Test
  void loadsReferencedConfigsWhenCsvFileValidationHeaderMatches() throws IOException {
    Path sourceFile = tempDir.resolve("events-valid.csv");
    Files.writeString(sourceFile, "id,eventTime\nEVT-1001,08:30:00\n");
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: Events
          packageName: com.etl.model.source
          filePath: %s
          delimiter: ","
          validation:
            allowEmpty: false
            requireHeaderMatch: true
          fields:
            - name: id
              type: String
            - name: eventTime
              type: String
      """.formatted(yamlPath(sourceFile)));
    Files.writeString(targetConfig, """
      targets:
        - format: csv
          targetName: EventsCsv
          packageName: com.etl.model.target
          filePath: output/events.csv
          delimiter: ","
          fields:
            - name: id
              type: String
            - name: eventTime
              type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      mappings:
        - source: Events
          target: EventsCsv
          fields:
            - from: id
              to: id
            - from: eventTime
              to: eventTime
      """);
    Files.writeString(jobConfig, """
      name: csv-file-validation-success
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: events-step
          source: Events
          target: EventsCsv
      """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    RunConfigurationMetadata metadata = loader.runConfigurationMetadata();
    SourceWrapper sourceWrapper = loader.sourceWrapper();

    assertEquals("csv-file-validation-success", metadata.scenarioName());
    assertEquals("Events", sourceWrapper.getSources().get(0).getSourceName());
  }

  @Test
  void loadsProcessorConfigWhenCustomProcessorRuleSpiIsProvided() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: Events
          packageName: com.etl.model.source
          filePath: input/events.csv
          delimiter: ","
          fields:
            - name: id
              type: String
      """);
    Files.writeString(targetConfig, """
      targets:
        - format: csv
          targetName: EventsCsv
          packageName: com.etl.model.target
          filePath: output/events.csv
          delimiter: ","
          fields:
            - name: id
              type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      mappings:
        - source: Events
          target: EventsCsv
          fields:
            - from: id
              to: id
              rules:
                - type: startsWith
                  pattern: EVT-
      """);
    Files.writeString(jobConfig, """
      name: custom-rule-spi
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: events-step
          source: Events
          target: EventsCsv
      """);

    ConfigLoader loader = new ConfigLoader(
        new SourceValidationService(),
        new ValidationRuleEvaluator(List.of(
            new NotNullProcessorValidationRule(),
            new TimeFormatProcessorValidationRule(),
            new StartsWithProcessorValidationRule()
        ))
    );
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ProcessorConfig loadedProcessorConfig = loader.processorConfig();

    assertEquals("startsWith", loadedProcessorConfig.getMappings().get(0).getFields().get(0).getRules().get(0).getType());
  }

  @Test
  void loadsProcessorConfigWhenBuiltInDuplicateRuleIsProvided() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: Events
          packageName: com.etl.model.source
          filePath: input/events.csv
          delimiter: ","
          fields:
            - name: id
              type: String
            - name: description
              type: String
      """);
    Files.writeString(targetConfig, """
      targets:
        - format: csv
          targetName: EventsCsv
          packageName: com.etl.model.target
          filePath: output/events.csv
          delimiter: ","
          fields:
            - name: id
              type: String
            - name: description
              type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      rejectHandling:
        enabled: true
        outputPath: target/rejects/
      mappings:
        - source: Events
          target: EventsCsv
          fields:
              - from: id
                to: id
                rules:
                  - type: duplicate
                    keyFields:
                      - id
                      - description
              - from: description
                to: description
      """);
    Files.writeString(jobConfig, """
      name: duplicate-rule
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: events-step
          source: Events
          target: EventsCsv
      """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ProcessorConfig loadedProcessorConfig = loader.processorConfig();

    assertEquals("duplicate", loadedProcessorConfig.getMappings().get(0).getFields().get(0).getRules().get(0).getType());
      assertEquals(List.of("id", "description"), loadedProcessorConfig.getMappings().get(0).getFields().get(0).getRules().get(0).getKeyFields());
  }

    @Test
    void failsFastWhenDuplicateRuleReferencesUnknownCompositeKeyField() throws IOException {
      Path sourceConfig = tempDir.resolve("source-config.yaml");
      Path targetConfig = tempDir.resolve("target-config.yaml");
      Path processorConfig = tempDir.resolve("processor-config.yaml");
      Path jobConfig = tempDir.resolve("job-config.yaml");

      Files.writeString(sourceConfig, """
        sources:
          - format: csv
            sourceName: Events
            packageName: com.etl.model.source
            filePath: input/events.csv
            delimiter: ","
            fields:
              - name: id
                type: String
              - name: description
                type: String
        """);
      Files.writeString(targetConfig, """
        targets:
          - format: csv
            targetName: EventsCsv
            packageName: com.etl.model.target
            filePath: output/events.csv
            delimiter: ","
            fields:
              - name: id
                type: String
              - name: description
                type: String
        """);
      Files.writeString(processorConfig, """
        type: default
        mappings:
          - source: Events
            target: EventsCsv
            fields:
              - from: id
                to: id
                rules:
                  - type: duplicate
                    keyFields:
                      - id
                      - eventTime
              - from: description
                to: description
        """);
      Files.writeString(jobConfig, """
        name: duplicate-rule-invalid-key-fields
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: events-step
            source: Events
            target: EventsCsv
        """);

      ConfigLoader loader = new ConfigLoader();
      ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
      ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

      ConfigException exception = assertThrows(ConfigException.class, loader::processorConfig);
      assertTrue(messageChain(exception).contains("keyFields"));
      assertTrue(messageChain(exception).contains("eventTime"));
    }

    @Test
    void loadsProcessorConfigWhenDuplicateRuleUsesOrderByWinnerSelection() throws IOException {
      Path sourceConfig = tempDir.resolve("source-config.yaml");
      Path targetConfig = tempDir.resolve("target-config.yaml");
      Path processorConfig = tempDir.resolve("processor-config.yaml");
      Path jobConfig = tempDir.resolve("job-config.yaml");

      Files.writeString(sourceConfig, """
        sources:
          - format: csv
            sourceName: Events
            packageName: com.etl.model.source
            filePath: input/events.csv
            delimiter: ","
            fields:
              - name: id
                type: String
              - name: eventTime
                type: String
              - name: sequenceNo
                type: int
        """);
      Files.writeString(targetConfig, """
        targets:
          - format: csv
            targetName: EventsCsv
            packageName: com.etl.model.target
            filePath: output/events.csv
            delimiter: ","
            fields:
              - name: id
                type: String
              - name: eventTime
                type: String
              - name: sequenceNo
                type: int
        """);
      Files.writeString(processorConfig, """
        type: default
        mappings:
          - source: Events
            target: EventsCsv
            fields:
              - from: id
                to: id
                rules:
                  - type: duplicate
                    keyFields:
                      - id
                    orderBy:
                      - field: eventTime
                        direction: DESC
                      - field: sequenceNo
                        direction: ASC
              - from: eventTime
                to: eventTime
              - from: sequenceNo
                to: sequenceNo
        """);
      Files.writeString(jobConfig, """
        name: duplicate-order-by
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: events-step
            source: Events
            target: EventsCsv
        """);

      ConfigLoader loader = new ConfigLoader();
      ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
      ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

      ProcessorConfig loadedProcessorConfig = loader.processorConfig();

      ProcessorConfig.FieldRule rule = loadedProcessorConfig.getMappings().get(0).getFields().get(0).getRules().get(0);
        assertEquals(2, rule.getOrderBy().size());
        assertEquals("eventTime", rule.getOrderBy().get(0).getField());
        assertEquals("DESC", rule.getOrderBy().get(0).getDirection());
        assertEquals("sequenceNo", rule.getOrderBy().get(1).getField());
        assertEquals("ASC", rule.getOrderBy().get(1).getDirection());
    }

    @Test
      void loadsProcessorConfigWhenDuplicateRuleUsesOnlyKeyFieldsAndDefaultsToKeepFirst() throws IOException {
      Path sourceConfig = tempDir.resolve("source-config.yaml");
      Path targetConfig = tempDir.resolve("target-config.yaml");
      Path processorConfig = tempDir.resolve("processor-config.yaml");
      Path jobConfig = tempDir.resolve("job-config.yaml");

      Files.writeString(sourceConfig, """
        sources:
          - format: csv
            sourceName: Events
            packageName: com.etl.model.source
            filePath: input/events.csv
            delimiter: ","
            fields:
              - name: id
                type: String
              - name: eventTime
                type: String
        """);
      Files.writeString(targetConfig, """
        targets:
          - format: csv
            targetName: EventsCsv
            packageName: com.etl.model.target
            filePath: output/events.csv
            delimiter: ","
            fields:
              - name: id
                type: String
              - name: eventTime
                type: String
        """);
      Files.writeString(processorConfig, """
        type: default
        mappings:
          - source: Events
            target: EventsCsv
            fields:
              - from: id
                to: id
                rules:
                  - type: duplicate
                    keyFields:
                      - id
              - from: eventTime
                to: eventTime
        """);
      Files.writeString(jobConfig, """
        name: duplicate-order-by-missing-order
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: events-step
            source: Events
            target: EventsCsv
        """);

      ConfigLoader loader = new ConfigLoader();
      ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
      ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

      ProcessorConfig loadedProcessorConfig = loader.processorConfig();

        ProcessorConfig.FieldRule rule = loadedProcessorConfig.getMappings().get(0).getFields().get(0).getRules().get(0);
        assertEquals(List.of("id"), rule.getKeyFields());
        assertTrue(rule.getOrderBy() == null || rule.getOrderBy().isEmpty());
    }

    @Test
    void failsFastWhenDuplicateOrderByDirectionIsInvalid() throws IOException {
      Path sourceConfig = tempDir.resolve("source-config.yaml");
      Path targetConfig = tempDir.resolve("target-config.yaml");
      Path processorConfig = tempDir.resolve("processor-config.yaml");
      Path jobConfig = tempDir.resolve("job-config.yaml");

      Files.writeString(sourceConfig, """
        sources:
          - format: csv
            sourceName: Events
            packageName: com.etl.model.source
            filePath: input/events.csv
            delimiter: ","
            fields:
              - name: id
                type: String
              - name: eventTime
                type: String
        """);
      Files.writeString(targetConfig, """
        targets:
          - format: csv
            targetName: EventsCsv
            packageName: com.etl.model.target
            filePath: output/events.csv
            delimiter: ","
            fields:
              - name: id
                type: String
              - name: eventTime
                type: String
        """);
      Files.writeString(processorConfig, """
        type: default
        mappings:
          - source: Events
            target: EventsCsv
            fields:
              - from: id
                to: id
                rules:
                  - type: duplicate
                    keyFields:
                      - id
                    orderBy:
                      - field: eventTime
                        direction: DOWN
              - from: eventTime
                to: eventTime
        """);
      Files.writeString(jobConfig, """
        name: duplicate-order-by-invalid-direction
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: events-step
            source: Events
            target: EventsCsv
        """);

      ConfigLoader loader = new ConfigLoader();
      ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
      ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

      ConfigException exception = assertThrows(ConfigException.class, loader::processorConfig);
      assertTrue(messageChain(exception).contains("ASC or DESC"));
    }

  @Test
  void failsFastWhenXmlSourceValidationFindsWrongRootElement() throws IOException {
    Path sourceFile = tempDir.resolve("customers.xml");
    Files.writeString(sourceFile, """
        <?xml version="1.0" encoding="UTF-8"?>
        <Clients>
          <Customer>
            <id>1</id>
            <name>John Doe</name>
          </Customer>
        </Clients>
        """);
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
        - format: xml
          sourceName: CustomersXml
          packageName: com.etl.model.source
          filePath: %s
          rootElement: Customers
          recordElement: Customer
          fields:
            - name: id
              type: int
            - name: name
              type: String
      """.formatted(yamlPath(sourceFile)));
    Files.writeString(targetConfig, """
      targets:
        - format: csv
          targetName: CustomersCsv
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
        - source: CustomersXml
          target: CustomersCsv
          fields:
            - from: id
              to: id
            - from: name
              to: name
      """);
    Files.writeString(jobConfig, """
      name: xml-source-root-mismatch
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: customers-step
          source: CustomersXml
          target: CustomersCsv
      """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::runConfigurationMetadata);
    assertTrue(messageChain(exception).contains("rootElement"));
    assertTrue(messageChain(exception).contains("expected=Customers"));
  }

  @Test
  void loadsReferencedConfigsWhenXmlSourceValidationPasses() throws IOException {
    Path sourceFile = tempDir.resolve("customers.xml");
    Files.writeString(sourceFile, """
        <?xml version="1.0" encoding="UTF-8"?>
        <Customers>
          <Customer>
            <id>1</id>
            <name>John Doe</name>
          </Customer>
        </Customers>
        """);
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
        - format: xml
          sourceName: CustomersXml
          packageName: com.etl.model.source
          filePath: %s
          rootElement: Customers
          recordElement: Customer
          fields:
            - name: id
              type: int
            - name: name
              type: String
      """.formatted(yamlPath(sourceFile)));
    Files.writeString(targetConfig, """
      targets:
        - format: csv
          targetName: CustomersCsv
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
        - source: CustomersXml
          target: CustomersCsv
          fields:
            - from: id
              to: id
            - from: name
              to: name
      """);
    Files.writeString(jobConfig, """
      name: xml-source-validation-success
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: customers-step
          source: CustomersXml
          target: CustomersCsv
      """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    RunConfigurationMetadata metadata = loader.runConfigurationMetadata();
    SourceWrapper sourceWrapper = loader.sourceWrapper();

    assertEquals("xml-source-validation-success", metadata.scenarioName());
    assertEquals("CustomersXml", sourceWrapper.getSources().get(0).getSourceName());
  }

  @Test
  void failsFastWhenCsvValidationHeaderDoesNotMatchConfiguredFields() throws IOException {
    Path sourceFile = tempDir.resolve("events-bad-header.csv");
    Files.writeString(sourceFile, "event_id,event_time\nEVT-1001,08:30:00\n");
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: Events
          packageName: com.etl.model.source
          filePath: %s
          delimiter: ","
          validation:
            allowEmpty: false
            requireHeaderMatch: true
          fields:
            - name: id
              type: String
            - name: eventTime
              type: String
      """.formatted(yamlPath(sourceFile)));
    Files.writeString(targetConfig, """
      targets:
        - format: csv
          targetName: EventsCsv
          packageName: com.etl.model.target
          filePath: output/events.csv
          delimiter: ","
          fields:
            - name: id
              type: String
            - name: eventTime
              type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      mappings:
        - source: Events
          target: EventsCsv
          fields:
            - from: id
              to: id
            - from: eventTime
              to: eventTime
      """);
    Files.writeString(jobConfig, """
      name: csv-file-validation-header-mismatch
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: events-step
          source: Events
          target: EventsCsv
      """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::runConfigurationMetadata);
    assertTrue(messageChain(exception).contains("header"));
    assertTrue(messageChain(exception).contains("expected=[id, eventTime]"));
  }

  @Test
  void failsFastWhenCsvArchiveIsEnabledWithoutSuccessPath() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
        sources:
          - format: csv
            sourceName: Events
            packageName: com.etl.model.source
            filePath: input/events.csv
            delimiter: ","
            archive:
              enabled: true
            fields:
              - name: id
                type: String
        """);
    Files.writeString(targetConfig, """
        targets:
          - format: csv
            targetName: EventsCsv
            packageName: com.etl.model.target
            filePath: output/events.csv
            delimiter: ","
            fields:
              - name: id
                type: String
        """);
    Files.writeString(processorConfig, """
        type: default
        mappings:
          - source: Events
            target: EventsCsv
            fields:
              - from: id
                to: id
        """);
    Files.writeString(jobConfig, """
        name: archive-missing-success-path
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: events-step
            source: Events
            target: EventsCsv
        """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::runConfigurationMetadata);
    assertTrue(exception.getMessage().contains("archive"));
    assertTrue(exception.getMessage().contains("successPath"));
  }

  private String messageChain(Throwable throwable) {
    StringBuilder builder = new StringBuilder();
    Throwable current = throwable;
    while (current != null) {
      if (current.getMessage() != null) {
        builder.append(current.getMessage()).append(" | ");
      }
      current = current.getCause();
    }
    return builder.toString();
  }

  private String yamlPath(Path path) {
    return path.toString().replace("\\", "\\\\");
  }

  private static final class StartsWithProcessorValidationRule implements ProcessorValidationRule {

    @Override
    public String getRuleType() {
      return "startsWith";
    }

    @Override
    public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
                                     ProcessorConfig.FieldMapping fieldMapping,
                                     ProcessorConfig.FieldRule rule) {
      if (rule.getPattern() == null || rule.getPattern().isBlank()) {
        throw new IllegalStateException("FieldMapping rule 'startsWith' requires a non-blank 'pattern'.");
      }
    }

    @Override
    public ValidationIssue evaluate(String fieldName, Object value, ProcessorConfig.FieldRule rule) {
      if (value == null || value.toString().startsWith(rule.getPattern())) {
        return null;
      }
      return new ValidationIssue(fieldName, getRuleType(), fieldName + " must start with " + rule.getPattern());
    }
  }
}


