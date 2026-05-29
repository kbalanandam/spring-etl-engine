package com.etl.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.etl.config.exception.ConfigException;
import com.etl.config.exception.ProcessorExtensionBindingConfigException;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.source.validation.SourceValidationService;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.JsonTargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.common.util.JobScopedPackageNameResolver;
import com.etl.common.util.GeneratedModelNamingPolicy;
import com.etl.enums.ModelFormat;
import com.etl.processor.ProcessorExtensionDefaults;
import com.etl.processor.transform.ProcessorFieldTransform;
import com.etl.processor.transform.TransformEvaluator;
import com.etl.runtime.job.JobRuntimeDescriptor;
import com.etl.processor.validation.NotNullProcessorValidationRule;
import com.etl.processor.validation.ProcessorValidationRule;
import com.etl.processor.validation.TimeFormatProcessorValidationRule;
import com.etl.processor.validation.ValidationIssue;
import com.etl.processor.validation.ValidationRuleEvaluator;
import com.etl.runtime.FileIngestionRuntimeSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderJobConfigTest {

    private final Logger configLoaderLogger = (Logger) LoggerFactory.getLogger(ConfigLoader.class);

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        configLoaderLogger.detachAndStopAllAppenders();
    }

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

        ensureSelectedJobFlatModels("csv-to-csv-test", List.of("Customers"), List.of("CustomersOut"));

        RunConfigurationMetadata runConfigurationMetadata = loader.buildRunConfigurationMetadata();
        SourceWrapper sourceWrapper = loader.sourceWrapper();
        TargetWrapper targetWrapper = loader.targetWrapper();
        ProcessorConfig loadedProcessorConfig = loader.processorConfig();
            JobRuntimeDescriptor jobRuntimeDescriptor = loader.jobRuntimeDescriptor(
                sourceWrapper,
                targetWrapper,
                loadedProcessorConfig,
                    loader.createJobRuntimeDescriptorAssembler()
        );

		assertEquals("csv-to-csv-test", runConfigurationMetadata.scenarioName());
		assertEquals(jobConfig.toString(), runConfigurationMetadata.jobConfigPath());
    assertFalse(runConfigurationMetadata.demoFallbackMode());
    assertEquals(1, runConfigurationMetadata.steps().size());
    assertEquals("customers-step", runConfigurationMetadata.steps().get(0).getName());
    assertEquals("Customers", runConfigurationMetadata.steps().get(0).getSource());
    assertEquals("CustomersOut", runConfigurationMetadata.steps().get(0).getTarget());
    assertEquals("csv-to-csv-test", jobRuntimeDescriptor.scenarioName());
    assertEquals(runConfigurationMetadata.steps().size(), jobRuntimeDescriptor.steps().size());
    assertEquals(runConfigurationMetadata.steps().get(0).getName(), jobRuntimeDescriptor.steps().get(0).stepName());

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
  void failsFastWhenSelectedProcessorConfigUsesLegacyProcessorType() throws IOException {
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
        """);

    Files.writeString(targetConfig, """
        targets:
          - format: csv
            targetName: CustomersOut
            filePath: output/customers.csv
            delimiter: ","
            fields:
              - name: id
                type: int
        """);

    Files.writeString(processorConfig, """
        type: customerProcessor
        mappings:
          - source: Customers
            target: CustomersOut
            fields:
              - from: id
                to: id
        """);

    Files.writeString(jobConfig, """
        name: legacy-processor-type
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

    ConfigException failure = assertThrows(ConfigException.class, loader::processorConfig);
    assertTrue(failure.getMessage().contains("type='customerProcessor'"));
    assertTrue(failure.getMessage().contains("type: default"));
  }

    @Test
    void normalizesScenarioRelativePathsInsideReferencedConfigs() throws IOException {
        Path scenarioDir = tempDir.resolve("csv-roundtrip");
        Files.createDirectories(scenarioDir.resolve("input"));
        Files.createDirectories(scenarioDir.resolve("output"));
        Files.writeString(scenarioDir.resolve("input/customers.csv"), "id,name\n1,Alice\n");

        Files.writeString(scenarioDir.resolve("source-config.yaml"), """
                sources:
                  - format: csv
                    sourceName: Customers
                    filePath: input/customers.csv
                    delimiter: ","
                    archive:
                      enabled: true
                      successPath: archive/success
                      packageAsZip: true
                    fields:
                      - name: id
                        type: int
                      - name: name
                        type: String
                """);

        Files.writeString(scenarioDir.resolve("target-config.yaml"), """
                targets:
                  - format: csv
                    targetName: CustomersOut
                    filePath: output/customers.csv
                    packageAsZip: true
                    delimiter: ","
                    fields:
                      - name: id
                        type: int
                      - name: name
                        type: String
                """);

        Files.writeString(scenarioDir.resolve("processor-config.yaml"), """
                type: default
                rejectHandling:
                  enabled: true
                  outputPath: rejects
                  packageAsZip: true
                mappings:
                  - source: Customers
                    target: CustomersOut
                    fields:
                      - from: id
                        to: id
                      - from: name
                        to: name
                """);

        Files.writeString(scenarioDir.resolve("job-config.yaml"), """
                name: csv-roundtrip
                sourceConfigPath: source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                steps:
                  - name: customers-step
                    source: Customers
                    target: CustomersOut
                """);

        ConfigLoader loader = new ConfigLoader();
        ReflectionTestUtils.setField(loader, "jobConfigPath", scenarioDir.resolve("job-config.yaml").toString());
        ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

        ensureSelectedJobFlatModels("csv-roundtrip", List.of("Customers"), List.of("CustomersOut"));

        SourceWrapper sourceWrapper = loader.sourceWrapper();
        TargetWrapper targetWrapper = loader.targetWrapper();
        ProcessorConfig processorConfig = loader.processorConfig();

        CsvSourceConfig sourceConfig = (CsvSourceConfig) sourceWrapper.getSources().get(0);
        CsvTargetConfig targetConfig = (CsvTargetConfig) targetWrapper.getTargets().get(0);

        assertEquals(scenarioDir.resolve("input/customers.csv").toAbsolutePath().normalize().toString(), sourceConfig.getFilePath());
        assertEquals(scenarioDir.resolve("archive/success").toAbsolutePath().normalize().toString(), sourceConfig.getArchive().getSuccessPath());
        assertTrue(sourceConfig.getArchive().isPackageAsZip());
        assertEquals(scenarioDir.resolve("output/customers.csv").toAbsolutePath().normalize().toString(), targetConfig.getFilePath());
        assertTrue(targetConfig.isPackageAsZip());
        assertEquals(scenarioDir.resolve("rejects").toAbsolutePath().normalize().toString(), processorConfig.getRejectHandling().getOutputPath());
        assertTrue(processorConfig.getRejectHandling().isPackageAsZip());
    }

  @Test
  void normalizesScenarioRelativeUnzipExtractDirInsideReferencedSourceConfig() throws IOException {
    Path scenarioDir = tempDir.resolve("csv-zipped-input");
    Files.createDirectories(scenarioDir.resolve("input"));
    Files.writeString(scenarioDir.resolve("source-config.yaml"), """
        sources:
          - format: csv
            sourceName: Customers
            filePath: input/customers.zip
            delimiter: ","
            unzip:
              enabled: true
              extractDir: working/unzipped
            fields:
              - name: id
                type: int
              - name: name
                type: String
        """);

    Files.writeString(scenarioDir.resolve("target-config.yaml"), """
        targets:
          - format: csv
            targetName: CustomersOut
            filePath: output/customers.csv
            delimiter: ","
            fields:
              - name: id
                type: int
              - name: name
                type: String
        """);

    Files.writeString(scenarioDir.resolve("processor-config.yaml"), """
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

    Files.writeString(scenarioDir.resolve("job-config.yaml"), """
        name: csv-zipped-input
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: customers-step
            source: Customers
            target: CustomersOut
        """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", scenarioDir.resolve("job-config.yaml").toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ensureSelectedJobFlatModels("csv-zipped-input", List.of("Customers"), List.of("CustomersOut"));

    SourceWrapper sourceWrapper = loader.sourceWrapper();
    CsvSourceConfig sourceConfig = (CsvSourceConfig) sourceWrapper.getSources().get(0);

    assertEquals(scenarioDir.resolve("input/customers.zip").toAbsolutePath().normalize().toString(), sourceConfig.getFilePath());
    assertNotNull(sourceConfig.getUnzipConfig());
    assertEquals(scenarioDir.resolve("working/unzipped").toAbsolutePath().normalize().toString(), sourceConfig.getUnzipConfig().getExtractDir());
  }

  @Test
  void trimsReferencedConfigPathsBeforeResolvingThemRelativeToJobConfig() throws IOException {
    Path scenarioDir = tempDir.resolve("trimmed-relative-config-paths");
    Files.createDirectories(scenarioDir.resolve("input"));
    Files.createDirectories(scenarioDir.resolve("output"));
    Files.writeString(scenarioDir.resolve("input/customers.csv"), "id,name\n1,Alice\n");

    Files.writeString(scenarioDir.resolve("source-config.yaml"), """
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
    Files.writeString(scenarioDir.resolve("target-config.yaml"), """
            targets:
              - format: csv
                targetName: CustomersOut
                filePath: output/customers.csv
                delimiter: ","
                fields:
                  - name: id
                    type: int
                  - name: name
                    type: String
            """);
    Files.writeString(scenarioDir.resolve("processor-config.yaml"), """
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
    Files.writeString(scenarioDir.resolve("job-config.yaml"), """
            name: trimmed-relative-config-paths
            sourceConfigPath:   source-config.yaml
            targetConfigPath:   target-config.yaml
            processorConfigPath:   processor-config.yaml
            steps:
              - name: customers-step
                source: Customers
                target: CustomersOut
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", scenarioDir.resolve("job-config.yaml").toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ensureSelectedJobFlatModels("trimmed-relative-config-paths", List.of("Customers"), List.of("CustomersOut"));

    RunConfigurationMetadata metadata = loader.buildRunConfigurationMetadata();
    SourceWrapper sourceWrapper = loader.sourceWrapper();
    TargetWrapper targetWrapper = loader.targetWrapper();
    ProcessorConfig processorConfig = loader.processorConfig();

    assertEquals("trimmed-relative-config-paths", metadata.scenarioName());
    assertEquals("Customers", sourceWrapper.getSources().get(0).getSourceName());
    assertEquals("CustomersOut", targetWrapper.getTargets().get(0).getTargetName());
    assertEquals("Customers", processorConfig.getMappings().get(0).getSource());
  }

    @Test
    void derivesDefaultPackagesWhenExplicitJobOmitsSourceAndJsonTargetPackageNames() throws IOException {
        Path scenarioDir = tempDir.resolve("xml-to-json-events");
        Files.createDirectories(scenarioDir.resolve("input"));
        Files.createDirectories(scenarioDir.resolve("definitions"));

        Files.writeString(scenarioDir.resolve("input/events.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <Events>
                  <Event>
                    <eventCode>LOGIN</eventCode>
                    <eventTime>2026-05-10T08:00:00</eventTime>
                  </Event>
                </Events>
                """);

        Files.writeString(scenarioDir.resolve("definitions/events-source-model.yaml"), """
                rootElement: Events
                recordElement: Event
                fields:
                  - name: eventCode
                    type: String
                  - name: eventTime
                    type: String
                """);

        Files.writeString(scenarioDir.resolve("source-config.yaml"), """
                sources:
                  - format: xml
                    sourceName: Events
                    filePath: input/events.xml
                    rootElement: Events
                    recordElement: Event
                    flatteningStrategy: DirectXml
                    modelDefinitionPath: definitions/events-source-model.yaml
                """);

        Files.writeString(scenarioDir.resolve("target-config.yaml"), """
                targets:
                  - format: json
                    targetName: EventsJson
                    filePath: output/events.json
                    fields:
                      - name: eventCode
                        type: String
                      - name: eventTime
                        type: String
                """);

        Files.writeString(scenarioDir.resolve("processor-config.yaml"), """
                type: default
                mappings:
                  - source: Events
                    target: EventsJson
                    fields:
                      - from: eventCode
                        to: eventCode
                      - from: eventTime
                        to: eventTime
                """);

        Files.writeString(scenarioDir.resolve("job-config.yaml"), """
                name: xml-to-json-events
                sourceConfigPath: source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                steps:
                  - name: events-xml-to-json-step
                    source: Events
                    target: EventsJson
                """);

        ConfigLoader loader = new ConfigLoader();
        ReflectionTestUtils.setField(loader, "jobConfigPath", scenarioDir.resolve("job-config.yaml").toString());
        ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

        SourceWrapper sourceWrapper = loader.sourceWrapper();
        TargetWrapper targetWrapper = loader.targetWrapper();

        XmlSourceConfig sourceConfig = (XmlSourceConfig) sourceWrapper.getSources().get(0);
        JsonTargetConfig targetConfig = (JsonTargetConfig) targetWrapper.getTargets().get(0);

        assertEquals(JobScopedPackageNameResolver.resolveSourcePackage("xml-to-json-events"), sourceConfig.getPackageName());
        assertEquals(JobScopedPackageNameResolver.resolveTargetPackage("xml-to-json-events"), targetConfig.getPackageName());
        assertEquals(scenarioDir.resolve("input/events.xml").toAbsolutePath().normalize().toString(), sourceConfig.getFilePath());
        assertEquals("DirectXml", sourceConfig.getFlatteningStrategy());
        assertEquals(scenarioDir.resolve("definitions/events-source-model.yaml").toAbsolutePath().normalize().toString(), sourceConfig.getModelDefinitionPath());
        assertEquals(scenarioDir.resolve("output/events.json").toAbsolutePath().normalize().toString(), targetConfig.getFilePath());
    }

  @Test
  void normalizesXmlValidationSchemaPathInsideReferencedConfigs() throws IOException {
    Path scenarioDir = tempDir.resolve("xml-to-json-events");
    Files.createDirectories(scenarioDir.resolve("input"));
    Files.createDirectories(scenarioDir.resolve("definitions"));
    Files.createDirectories(scenarioDir.resolve("schemas"));

    Files.writeString(scenarioDir.resolve("input/events.xml"), """
        <?xml version="1.0" encoding="UTF-8"?>
        <Events>
          <Event>
            <eventCode>LOGIN</eventCode>
          </Event>
        </Events>
        """);
    Files.writeString(scenarioDir.resolve("schemas/events.xsd"), """
        <?xml version="1.0" encoding="UTF-8"?>
        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
          <xs:element name="Events">
            <xs:complexType>
              <xs:sequence>
                <xs:element name="Event" maxOccurs="unbounded">
                  <xs:complexType>
                    <xs:sequence>
                      <xs:element name="eventCode" type="xs:string"/>
                    </xs:sequence>
                  </xs:complexType>
                </xs:element>
              </xs:sequence>
            </xs:complexType>
          </xs:element>
        </xs:schema>
        """);
    Files.writeString(scenarioDir.resolve("definitions/events-source-model.yaml"), """
        rootElement: Events
        recordElement: Event
        fields:
          - name: eventCode
            type: String
        """);

    Files.writeString(scenarioDir.resolve("source-config.yaml"), """
        sources:
          - format: xml
            sourceName: Events
            filePath: input/events.xml
            rootElement: Events
            recordElement: Event
            flatteningStrategy: DirectXml
            modelDefinitionPath: definitions/events-source-model.yaml
            validation:
              schemaPath: schemas/events.xsd
              onFailure: rejectFile
              rejectPath: rejects
        """);
    Files.writeString(scenarioDir.resolve("target-config.yaml"), """
        targets:
          - format: json
            targetName: EventsJson
            filePath: output/events.json
            fields:
              - name: eventCode
                type: String
        """);
    Files.writeString(scenarioDir.resolve("processor-config.yaml"), """
        type: default
        mappings:
          - source: Events
            target: EventsJson
            fields:
              - from: eventCode
                to: eventCode
        """);
    Files.writeString(scenarioDir.resolve("job-config.yaml"), """
        name: xml-to-json-events
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: events-xml-to-json-step
            source: Events
            target: EventsJson
        """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", scenarioDir.resolve("job-config.yaml").toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    SourceWrapper sourceWrapper = loader.sourceWrapper();
    XmlSourceConfig sourceConfig = (XmlSourceConfig) sourceWrapper.getSources().get(0);

    assertNotNull(sourceConfig.getValidation());
    assertEquals(scenarioDir.resolve("definitions/events-source-model.yaml").toAbsolutePath().normalize().toString(), sourceConfig.getModelDefinitionPath());
    assertEquals(scenarioDir.resolve("schemas/events.xsd").toAbsolutePath().normalize().toString(), sourceConfig.getValidation().getSchemaPath());
    assertEquals(scenarioDir.resolve("rejects").toAbsolutePath().normalize().toString(), sourceConfig.getValidation().getRejectPath());
  }

  @Test
  void failsFastWithDerivedTargetPackageWhenExplicitJobOmitsPackageName() throws IOException {
    Path sourceFile = tempDir.resolve("customers.csv");
    Files.writeString(sourceFile, "id,name\n1,John Doe\n");
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: Customers
          filePath: %s
          delimiter: ","
          fields:
            - name: id
              type: int
            - name: name
              type: String
      """.formatted(yamlPath(sourceFile)));
    Files.writeString(targetConfig, """
      targets:
        - format: json
          targetName: CustomersJson
          filePath: output/customers.json
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
          target: CustomersJson
          fields:
            - from: id
              to: id
            - from: name
              to: name
      """);
    Files.writeString(jobConfig, """
      name: csv-to-json-derived-package
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: customers-step
          source: Customers
          target: CustomersJson
      """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ensureSelectedJobFlatSourceModels("csv-to-json-derived-package", List.of("Customers"));

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    String messages = messageChain(exception);

    assertTrue(messages.contains("Target model class not found"));
    assertTrue(messages.contains(JobScopedPackageNameResolver.resolveTargetPackage("csv-to-json-derived-package") + ".CustomersJsonModel"));
    assertFalse(messages.contains("null.CustomersJsonModel"));
    assertFalse(messages.contains("must define a non-blank packageName"));
  }

  @Test
  void failsFastWhenExplicitSelectedJobProvidesPackageName() throws IOException {
    Path scenarioDir = tempDir.resolve("xml-to-json-events-warning");
    Files.createDirectories(scenarioDir.resolve("input"));
    Files.createDirectories(scenarioDir.resolve("output"));
    Files.writeString(scenarioDir.resolve("input/events.xml"), "<Events><Event><eventCode>LOGIN</eventCode></Event></Events>");

    Files.writeString(scenarioDir.resolve("source-config.yaml"), """
        sources:
          - format: xml
            sourceName: Events
            packageName: com.etl.generated.job.xmltojsonevents.source
            filePath: input/events.xml
            rootElement: Events
            recordElement: Event
            fields:
              - name: eventCode
                type: String
        """);
    Files.writeString(scenarioDir.resolve("target-config.yaml"), """
        targets:
          - format: json
            targetName: EventsJson
            packageName: com.etl.generated.job.xmltojsonevents.target
            filePath: output/events.json
            fields:
              - name: eventCode
                type: String
        """);
    Files.writeString(scenarioDir.resolve("processor-config.yaml"), """
        type: default
        mappings:
          - source: Events
            target: EventsJson
            fields:
              - from: eventCode
                to: eventCode
        """);
    Files.writeString(scenarioDir.resolve("job-config.yaml"), """
        name: xml-to-json-events-warning
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: events-step
            source: Events
            target: EventsJson
        """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", scenarioDir.resolve("job-config.yaml").toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    String messages = messageChain(exception);

    assertTrue(messages.contains("Selected job 'xml-to-json-events-warning' does not allow explicit packageName"));
    assertTrue(messages.contains("source config 'Events'"));
    assertTrue(messages.contains("com.etl.generated.job.xmltojsonevents.source"));
    assertTrue(messages.contains("com.etl.generated.job.xmltojsoneventswarning.source"));
    assertTrue(messages.contains("Remove packageName"));
  }

  @Test
  void categorizesMissingGeneratedTargetClassWithScenarioAndStepContext() throws IOException {
    Path sourceFile = tempDir.resolve("customers.csv");
    Files.writeString(sourceFile, "id,name\n1,John Doe\n");
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: Customers
          filePath: %s
          delimiter: ","
          fields:
            - name: id
              type: int
            - name: name
              type: String
      """.formatted(yamlPath(sourceFile)));
    Files.writeString(targetConfig, """
      targets:
        - format: json
          targetName: CustomersJson
          filePath: output/customers.json
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
          target: CustomersJson
          fields:
            - from: id
              to: id
            - from: name
              to: name
      """);
    Files.writeString(jobConfig, """
      name: missing-generated-model-target
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: customers-step
          source: Customers
          target: CustomersJson
      """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ensureSelectedJobFlatSourceModels("missing-generated-model-target", List.of("Customers"));

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    String messages = messageChain(exception);

    assertTrue(messages.contains("Invalid generated model configuration for scenario 'missing-generated-model-target'"));
    assertTrue(messages.contains(jobConfig.toString()));
    assertTrue(messages.contains("step='customers-step'"));
    assertTrue(messages.contains("source='Customers'"));
    assertTrue(messages.contains("target='CustomersJson'"));
    assertTrue(messages.contains("Target model class not found"));
    assertFalse(messages.contains("Failed to resolve runtime configuration metadata"));
  }

  @Test
  void failsFastWhenLogicalHandoffNameIsConsumedBeforeAnyEarlierStepProducesIt() throws IOException {
    Path sourceFile = tempDir.resolve("customers.csv");
    Files.writeString(sourceFile, "id,name\n1,John Doe\n");
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: CustomersIntermediate
          filePath: %s
          delimiter: ","
          fields:
            - name: id
              type: int
            - name: name
              type: String
        - format: csv
          sourceName: IngressCustomers
          filePath: %s
          delimiter: ","
          fields:
            - name: id
              type: int
            - name: name
              type: String
      """.formatted(yamlPath(sourceFile), yamlPath(sourceFile)));
    Files.writeString(targetConfig, """
      targets:
        - format: json
          targetName: FinalCustomersJson
          filePath: output/customers.json
          fields:
            - name: id
              type: int
            - name: name
              type: String
        - format: json
          targetName: CustomersIntermediate
          filePath: output/intermediate.json
          fields:
            - name: id
              type: int
            - name: name
              type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      mappings:
        - source: CustomersIntermediate
          target: FinalCustomersJson
          fields:
            - from: id
              to: id
            - from: name
              to: name
        - source: IngressCustomers
          target: CustomersIntermediate
          fields:
            - from: id
              to: id
            - from: name
              to: name
      """);
    Files.writeString(jobConfig, """
      name: naming-guardrail
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: final-step
          source: CustomersIntermediate
          target: FinalCustomersJson
        - name: ingest-step
          source: IngressCustomers
          target: CustomersIntermediate
      """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    String messages = messageChain(exception);

    assertTrue(messages.contains("CustomersIntermediate"));
    assertTrue(messages.contains("before it is produced"));
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

    RunConfigurationMetadata runConfigurationMetadata = loader.buildRunConfigurationMetadata();
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
    assertEquals("com.etl.model.source", sourceWrapper.getSources().get(0).getPackageName());
    assertEquals("com.etl.model.target", targetWrapper.getTargets().get(0).getPackageName());
  }

  @Test
  void failsFastWhenDirectConfigProvidesPackageName() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");

    Files.writeString(sourceConfig, """
        sources:
          - format: csv
            sourceName: Customers
            packageName: com.etl.model.source
            filePath: input/customers.csv
            delimiter: ","
            fields:
              - name: id
                type: String
        """);
    Files.writeString(targetConfig, """
        targets:
          - format: json
            targetName: CustomersJson
            filePath: output/customers.json
            fields:
              - name: id
                type: String
        """);
    Files.writeString(processorConfig, """
        type: default
        mappings:
          - source: Customers
            target: CustomersJson
            fields:
              - from: id
                to: id
        """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", "");
    ReflectionTestUtils.setField(loader, "allowDemoFallback", true);
    ReflectionTestUtils.setField(loader, "sourceConfigPath", sourceConfig.toString());
    ReflectionTestUtils.setField(loader, "targetConfigPath", targetConfig.toString());
    ReflectionTestUtils.setField(loader, "processorConfigPath", processorConfig.toString());

    ConfigException exception = assertThrows(ConfigException.class, loader::sourceWrapper);
    String messages = messageChain(exception);

    assertTrue(messages.contains("Direct-config runtime does not allow explicit packageName"));
    assertTrue(messages.contains("source config 'Customers'"));
    assertTrue(messages.contains("com.etl.model.source"));
    assertTrue(messages.contains("Remove packageName"));
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
  void failsFastWhenJobConfigNameIsBlankForExplicitJobRuns() throws IOException {
    Path scenarioDir = tempDir.resolve("customer-load");
    Files.createDirectories(scenarioDir);

    Files.writeString(scenarioDir.resolve("source-config.yaml"), """
            sources:
              - format: csv
                sourceName: Customers
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

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(messageChain(exception).contains("require a non-blank 'name'"));
    assertTrue(messageChain(exception).contains(jobConfig.toString()));
  }

  @Test
  void failsFastWhenSelectedExplicitJobIsInactiveBeforeReferencedConfigsAreResolved() throws IOException {
    Path scenarioDir = tempDir.resolve("inactive-job");
    Files.createDirectories(scenarioDir);

    Path jobConfig = scenarioDir.resolve("job-config.yaml");
    Files.writeString(jobConfig, """
            name: inactive-job
            isActive: false
            sourceConfigPath: missing-source-config.yaml
            targetConfigPath: missing-target-config.yaml
            processorConfigPath: missing-processor-config.yaml
            steps:
              - name: customers-step
                source: Customers
                target: CustomersOut
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::sourceWrapper);
    String messages = messageChain(exception);

    assertTrue(messages.contains("Selected job 'inactive-job' is inactive"));
    assertTrue(messages.contains(jobConfig.toAbsolutePath().normalize().toString()));
    assertFalse(messages.contains("missing-source-config.yaml"));
  }

  @Test
  void preservesCurrentExplicitJobBehaviorWhenIsActiveIsTrue() throws IOException {
    Path scenarioDir = tempDir.resolve("active-job");
    Files.createDirectories(scenarioDir.resolve("input"));
    Files.createDirectories(scenarioDir.resolve("output"));
    Files.writeString(scenarioDir.resolve("input/customers.csv"), "id,name\n1,Alice\n");

    Files.writeString(scenarioDir.resolve("source-config.yaml"), """
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
    Files.writeString(scenarioDir.resolve("target-config.yaml"), """
            targets:
              - format: csv
                targetName: CustomersOut
                filePath: output/customers.csv
                delimiter: ","
                fields:
                  - name: id
                    type: int
                  - name: name
                    type: String
            """);
    Files.writeString(scenarioDir.resolve("processor-config.yaml"), """
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
    Files.writeString(scenarioDir.resolve("job-config.yaml"), """
            name: active-job
            isActive: true
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            steps:
              - name: customers-step
                source: Customers
                target: CustomersOut
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", scenarioDir.resolve("job-config.yaml").toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ensureSelectedJobFlatModels("active-job", List.of("Customers"), List.of("CustomersOut"));

    RunConfigurationMetadata metadata = loader.buildRunConfigurationMetadata();

    assertEquals("active-job", metadata.scenarioName());
    assertEquals(1, metadata.steps().size());
    assertEquals("customers-step", metadata.steps().get(0).getName());
  }

  @Test
  void loadsJobConfigWhenLegacyConfigScenariosAliasPointsAtCanonicalConfigJobsBundle() throws IOException {
    Path canonicalScenarioDir = tempDir.resolve("src/main/resources/config-jobs/customer-load");
    Files.createDirectories(canonicalScenarioDir.resolve("input"));
    Files.createDirectories(canonicalScenarioDir.resolve("output"));
    Files.writeString(canonicalScenarioDir.resolve("input/customers.csv"), "id,name\n1,Alice\n");

    Files.writeString(canonicalScenarioDir.resolve("source-config.yaml"), """
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
    Files.writeString(canonicalScenarioDir.resolve("target-config.yaml"), """
        targets:
          - format: csv
            targetName: CustomersOut
            filePath: output/customers.csv
            delimiter: ","
            fields:
              - name: id
                type: int
              - name: name
                type: String
        """);
    Files.writeString(canonicalScenarioDir.resolve("processor-config.yaml"), """
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
    Files.writeString(canonicalScenarioDir.resolve("job-config.yaml"), """
        name: customer-load
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: customers-step
            source: Customers
            target: CustomersOut
        """);

    Path requestedAliasJobConfig = tempDir.resolve("src/main/resources/config-scenarios/customer-load/job-config.yaml");

    ensureSelectedJobFlatModels("customer-load", List.of("Customers"), List.of("CustomersOut"));

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", requestedAliasJobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    RunConfigurationMetadata metadata = loader.buildRunConfigurationMetadata();
    assertEquals("customer-load", metadata.scenarioName());
    assertEquals(canonicalScenarioDir.resolve("job-config.yaml").toAbsolutePath().normalize().toString(), metadata.jobConfigPath());
  }

  @Test
  void doesNotResolveLegacyBundleAliasForReferencedYamlLoadsInsideConfigLoader() throws Exception {
    Path canonicalSourceConfig = tempDir.resolve("src/main/resources/config-jobs/customer-load/source-config.yaml");
    Files.createDirectories(canonicalSourceConfig.getParent());
    Files.writeString(canonicalSourceConfig, "sources:\n  - format: csv\n    sourceName: Customers\n");

    Path requestedAliasSourceConfig = tempDir.resolve("src/main/resources/config-scenarios/customer-load/source-config.yaml");

    ConfigLoader loader = new ConfigLoader();

    Class<?> packageNameContractType = null;
    for (Class<?> nestedType : ConfigLoader.class.getDeclaredClasses()) {
      if ("PackageNameContract".equals(nestedType.getSimpleName())) {
        packageNameContractType = nestedType;
        break;
      }
    }
    assertNotNull(packageNameContractType);

    Method method = ConfigLoader.class.getDeclaredMethod(
            "loadRequiredExternalYamlConfig",
            String.class,
            Class.class,
            ObjectMapper.class,
            packageNameContractType
    );
    method.setAccessible(true);

    IOException exception = assertThrows(IOException.class,
            () -> {
              try {
                method.invoke(
                        loader,
                        requestedAliasSourceConfig.toString(),
                        SourceWrapper.class,
                        new ObjectMapper(new YAMLFactory()),
                        null
                );
              } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
              }
            });

    assertTrue(exception.getMessage().contains("Required YAML file not found"));
    assertTrue(exception.getMessage().contains(requestedAliasSourceConfig.toString()));
  }

  @Test
  void appliesDerivedGeneratedPackagesWhenExplicitJobConfigsOmitPackageName() {
    CsvSourceConfig derivedSource = new CsvSourceConfig(
        "CustomersCsv",
        null,
        List.of(column("id", "String")),
        "input/customers.csv",
        ","
    );
    CsvSourceConfig explicitSource = new CsvSourceConfig(
        "Customers",
        "com.example.explicit.source",
        List.of(column("id", "String")),
        "input/customers.csv",
        ","
    );
    SourceWrapper sourceWrapper = new SourceWrapper();
    sourceWrapper.setSources(List.of(derivedSource, explicitSource));

    TargetWrapper targetWrapper = new TargetWrapper();
    targetWrapper.setTargets(List.of(
        new CsvTargetConfig(
            "CustomersCsv",
            null,
            List.of(column("id", "String")),
            "output/customers.csv",
            ","
        ),
        new CsvTargetConfig(
            "CustomersOut",
            "com.example.explicit.target",
            List.of(column("id", "String")),
            "output/customers-out.csv",
            ","
        )
    ));

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.invokeMethod(loader, "applyJobScopedPackageDefaults", sourceWrapper, targetWrapper, "csv-to-nested-xml");

    assertEquals("com.etl.generated.job.csvtonestedxml.source", sourceWrapper.getSources().get(0).getPackageName());
    assertEquals("com.etl.generated.job.csvtonestedxml.source", sourceWrapper.getSources().get(1).getPackageName());
    assertEquals("com.etl.generated.job.csvtonestedxml.target", targetWrapper.getTargets().get(0).getPackageName());
    assertEquals("com.etl.generated.job.csvtonestedxml.target", targetWrapper.getTargets().get(1).getPackageName());
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

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(exception.getMessage().contains("steps"));
  }

  @Test
  void resolvesStepSkipPolicyForExplicitCsvStep() throws IOException {
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
            """);
    Files.writeString(targetConfig, """
            targets:
              - format: csv
                targetName: CustomersOut
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
            name: csv-skip-policy
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            steps:
              - name: customers-step
                source: Customers
                target: CustomersOut
                skipPolicy:
                  enabled: true
                  skipLimit: 5
                  skippableCategories:
                    - runtime
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ensureSelectedJobFlatModels("csv-skip-policy", List.of("Customers"), List.of("CustomersOut"));

    RunConfigurationMetadata metadata = loader.buildRunConfigurationMetadata();

    assertEquals(1, metadata.steps().size());
    assertNotNull(metadata.steps().get(0).getSkipPolicy());
    assertTrue(metadata.steps().get(0).getSkipPolicy().isEnabled());
    assertEquals(5, metadata.steps().get(0).getSkipPolicy().getSkipLimit());
    assertEquals(List.of("runtime"),
            metadata.steps().get(0).getSkipPolicy().getSkippableCategories());
    assertEquals(List.of(),
            metadata.steps().get(0).getSkipPolicy().getSkippableExceptions());
  }

  @Test
  void resolvesStepSkipPolicyWithCategoryAndExceptionCompatibility() throws IOException {
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
            """);
    Files.writeString(targetConfig, """
            targets:
              - format: csv
                targetName: CustomersOut
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
            name: csv-skip-policy-category-compat
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            steps:
              - name: customers-step
                source: Customers
                target: CustomersOut
                skipPolicy:
                  enabled: true
                  skipLimit: 5
                  skippableCategories:
                    - RUNTIME
                  skippableExceptions:
                    - org.springframework.batch.item.file.FlatFileParseException
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ensureSelectedJobFlatModels("csv-skip-policy-category-compat", List.of("Customers"), List.of("CustomersOut"));

    RunConfigurationMetadata metadata = loader.buildRunConfigurationMetadata();

    assertEquals(List.of("runtime"), metadata.steps().get(0).getSkipPolicy().getSkippableCategories());
    assertEquals(List.of("org.springframework.batch.item.file.FlatFileParseException"),
            metadata.steps().get(0).getSkipPolicy().getSkippableExceptions());
  }

  @Test
  void failsFastWhenSkipPolicyReferencesUnknownExceptionClass() throws IOException {
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
            """);
    Files.writeString(targetConfig, """
            targets:
              - format: csv
                targetName: CustomersOut
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
            name: csv-skip-policy-invalid
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            steps:
              - name: customers-step
                source: Customers
                target: CustomersOut
                skipPolicy:
                  enabled: true
                  skipLimit: 5
                  skippableExceptions:
                    - com.example.UnknownSkipException
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(messageChain(exception).contains("skipPolicy.skippableExceptions contains unknown class"));
    assertTrue(messageChain(exception).contains("customers-step"));
  }

  @Test
  void failsFastWhenSkipPolicyReferencesUnknownEtlCategory() throws IOException {
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
            """);
    Files.writeString(targetConfig, """
            targets:
              - format: csv
                targetName: CustomersOut
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
            name: csv-skip-policy-invalid-category
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            steps:
              - name: customers-step
                source: Customers
                target: CustomersOut
                skipPolicy:
                  enabled: true
                  skipLimit: 5
                  skippableCategories:
                    - parser
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(messageChain(exception).contains("skipPolicy.skippableCategories contains unknown ETL category"));
    assertTrue(messageChain(exception).contains("customers-step"));
  }

  @Test
  void resolvesStepRetryPolicyWithCategoryAndExceptionCompatibility() throws IOException {
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
            """);
    Files.writeString(targetConfig, """
            targets:
              - format: csv
                targetName: CustomersOut
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
            name: csv-retry-policy
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            steps:
              - name: customers-step
                source: Customers
                target: CustomersOut
                retryPolicy:
                  enabled: true
                  maxAttempts: 3
                  backoffMs: 250
                  retryableCategories:
                    - RUNTIME
                  retryableExceptions:
                    - org.springframework.batch.item.file.FlatFileParseException
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ensureSelectedJobFlatModels("csv-retry-policy", List.of("Customers"), List.of("CustomersOut"));

    RunConfigurationMetadata metadata = loader.buildRunConfigurationMetadata();

    assertNotNull(metadata.steps().get(0).getRetryPolicy());
    assertTrue(metadata.steps().get(0).getRetryPolicy().isEnabled());
    assertEquals(3, metadata.steps().get(0).getRetryPolicy().getMaxAttempts());
    assertEquals(250L, metadata.steps().get(0).getRetryPolicy().getBackoffMs());
    assertEquals(List.of("runtime"), metadata.steps().get(0).getRetryPolicy().getRetryableCategories());
    assertEquals(List.of("org.springframework.batch.item.file.FlatFileParseException"),
            metadata.steps().get(0).getRetryPolicy().getRetryableExceptions());
  }

  @Test
  void failsFastWhenRetryPolicyReferencesUnknownEtlCategory() throws IOException {
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
            """);
    Files.writeString(targetConfig, """
            targets:
              - format: csv
                targetName: CustomersOut
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
            name: csv-retry-policy-invalid-category
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            steps:
              - name: customers-step
                source: Customers
                target: CustomersOut
                retryPolicy:
                  enabled: true
                  maxAttempts: 3
                  backoffMs: 10
                  retryableCategories:
                    - transient
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(messageChain(exception).contains("retryPolicy.retryableCategories contains unknown ETL category"));
  }

  @Test
  void failsFastWhenStepConfiguresBothSkipPolicyAndRetryPolicy() throws IOException {
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
            """);
    Files.writeString(targetConfig, """
            targets:
              - format: csv
                targetName: CustomersOut
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
            name: csv-skip-retry-conflict
            sourceConfigPath: source-config.yaml
            targetConfigPath: target-config.yaml
            processorConfigPath: processor-config.yaml
            steps:
              - name: customers-step
                source: Customers
                target: CustomersOut
                skipPolicy:
                  enabled: true
                  skipLimit: 1
                  skippableCategories:
                    - runtime
                retryPolicy:
                  enabled: true
                  maxAttempts: 3
                  backoffMs: 10
                  retryableCategories:
                    - runtime
            """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(messageChain(exception).contains("configures both skipPolicy and retryPolicy"));
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

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
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
                filePath: input/customers.csv
                delimiter: ","
                fields:
                  - name: id
                    type: int
              - format: csv
                sourceName: Department
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
                filePath: output/customers.csv
                delimiter: ","
                fields:
                  - name: id
                    type: int
              - format: csv
                targetName: DepartmentsOut
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

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
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

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
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
  void failsFastOnScenarioAwareProcessorConfigErrorBeforeGeneratedTargetValidation() throws IOException {
    Path sourceFile = tempDir.resolve("customers.csv");
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceFile, "id,name\n1,John Doe\n");

    Files.writeString(sourceConfig, """
        sources:
          - format: csv
            sourceName: Customers
            filePath: %s
            delimiter: ","
            fields:
              - name: id
                type: int
              - name: name
                type: String
        """.formatted(yamlPath(sourceFile)));
    Files.writeString(targetConfig, """
        targets:
          - format: csv
            targetName: MissingTarget
            filePath: output/missing-target.csv
            delimiter: ","
            fields:
              - name: id
                type: int
              - name: name
                type: String
        """);
    Files.writeString(processorConfig, """
        type: default
        rejectHandling:
          enabled: true
        mappings:
          - source: Customers
            target: MissingTarget
            fields:
              - from: id
                to: id
              - from: name
                to: name
        """);
    Files.writeString(jobConfig, """
        name: processor-validation-before-generated-models
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: customers-step
            source: Customers
            target: MissingTarget
        """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(messageChain(exception).contains("Invalid processor configuration for scenario 'processor-validation-before-generated-models'"));
    assertTrue(messageChain(exception).contains(processorConfig.toString()));
    assertTrue(messageChain(exception).contains("rejectHandling"));
    assertFalse(messageChain(exception).contains("Target model class not found"));
  }

  @Test
  void failsFastWhenRuleRequestsRejectRecordWithoutRejectHandling() throws IOException {
    Path sourceFile = tempDir.resolve("customers.csv");
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceFile, "id\n1\n");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: Customers
          filePath: %s
          delimiter: ","
          fields:
            - name: id
              type: String
      """.formatted(yamlPath(sourceFile)));
    Files.writeString(targetConfig, """
      targets:
        - format: csv
          targetName: Customers
          filePath: output/customers.csv
          delimiter: ","
          fields:
            - name: id
              type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      mappings:
        - source: Customers
          target: Customers
          fields:
            - from: id
              to: id
              rules:
                - type: notNull
                  onFailure: rejectRecord
      """);
    Files.writeString(jobConfig, """
      name: reject-rule-without-reject-handling
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: customers-step
          source: Customers
          target: Customers
      """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::processorConfig);
    assertTrue(messageChain(exception).contains("onFailure=rejectRecord"));
    assertTrue(messageChain(exception).contains("rejectHandling.enabled"));
  }

  @Test
  void failsFastWhenRejectHandlingOutputPathLooksLikeAFilePath() throws IOException {
    Path sourceFile = tempDir.resolve("customers.csv");
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceFile, "id,name\n1,Alice\n");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: Customers
          filePath: %s
          delimiter: ","
          fields:
            - name: id
              type: String
            - name: name
              type: String
      """.formatted(yamlPath(sourceFile)));
    Files.writeString(targetConfig, """
      targets:
        - format: csv
          targetName: CustomersOut
          filePath: output/customers-out.csv
          delimiter: ","
          fields:
            - name: id
              type: String
            - name: name
              type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      rejectHandling:
        enabled: true
        outputPath: output/rejects.csv
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
      name: reject-output-path-file-style
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

    ConfigException exception = assertThrows(ConfigException.class, loader::processorConfig);
    assertTrue(messageChain(exception).contains("rejectHandling.outputPath must be a directory-style path"));
    assertTrue(messageChain(exception).contains("<step-name>-rejects.csv"));
  }

  @Test
  void failsFastWhenRejectHandlingQuarantinePathLooksLikeAFilePath() throws IOException {
    Path sourceFile = tempDir.resolve("customers.csv");
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceFile, "id,name\n1,Alice\n");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: Customers
          filePath: %s
          delimiter: ","
          fields:
            - name: id
              type: String
            - name: name
              type: String
      """.formatted(yamlPath(sourceFile)));
    Files.writeString(targetConfig, """
      targets:
        - format: csv
          targetName: CustomersOut
          filePath: output/customers-out.csv
          delimiter: ","
          fields:
            - name: id
              type: String
            - name: name
              type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      rejectHandling:
        enabled: true
        outputPath: output/rejects/
        quarantinePath: output/quarantine.csv
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
      name: reject-quarantine-path-file-style
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

    ConfigException exception = assertThrows(ConfigException.class, loader::processorConfig);
    assertTrue(messageChain(exception).contains("rejectHandling.quarantinePath must be a directory-style path"));
    assertTrue(messageChain(exception).contains("<step-name>-rejects.csv"));
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
  void failsFastWhenConditionalTransformHasNoCases() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: Orders
          filePath: input/orders.csv
          delimiter: ","
          fields:
            - name: amount
              type: int
      """);
    Files.writeString(targetConfig, """
      targets:
        - format: csv
          targetName: OrdersOut
          filePath: output/orders.csv
          delimiter: ","
          fields:
            - name: tier
              type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      mappings:
        - source: Orders
          target: OrdersOut
          fields:
            - from: amount
              to: tier
              transforms:
                - type: conditional
      """);
    Files.writeString(jobConfig, """
      name: conditional-missing-cases
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: orders-step
          source: Orders
          target: OrdersOut
      """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::processorConfig);
    assertTrue(messageChain(exception).contains("Invalid processor configuration for scenario 'conditional-missing-cases'"));
    assertTrue(messageChain(exception).contains("transform 'conditional' requires a non-empty 'cases'"));
  }

  @Test
  void failsFastWhenDuplicateStorageModeIsInvalid() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: Events
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
                      direction: DESC
                  storageMode: disk
            - from: eventTime
              to: eventTime
      """);
    Files.writeString(jobConfig, """
      name: duplicate-storage-mode-invalid
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
    assertTrue(messageChain(exception).contains("duplicate-storage-mode-invalid"));
    assertTrue(messageChain(exception).contains("storageMode"));
    assertTrue(messageChain(exception).contains("auto, memory, or embeddedDb"));
  }

  @Test
  void failsFastWhenDuplicateStorageModeIsConfiguredWithoutOrderByWinnerSelection() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
        - format: csv
          sourceName: Events
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
                - type: duplicate
                  storageMode: memory
      """);
    Files.writeString(jobConfig, """
      name: duplicate-storage-mode-without-orderby
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
    assertTrue(messageChain(exception).contains("duplicate-storage-mode-without-orderby"));
    assertTrue(messageChain(exception).contains("storageMode"));
    assertTrue(messageChain(exception).contains("only supported when 'orderBy' winner selection is configured"));
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

    ensureSelectedJobFlatModels("csv-file-validation-success", List.of("Events"), List.of("EventsCsv"));

    RunConfigurationMetadata metadata = loader.buildRunConfigurationMetadata();
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

    ensureSelectedJobFlatModels("custom-rule-spi", List.of("Events"), List.of("EventsCsv"));

    ProcessorConfig loadedProcessorConfig = loader.processorConfig();

    assertEquals("startsWith", loadedProcessorConfig.getMappings().get(0).getFields().get(0).getRules().get(0).getType());
  }

  @Test
  void loadsProcessorConfigWhenCsvScopedTransformSpiIsProvided() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
      - format: csv
        sourceName: Events
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
          transforms:
          - type: csvOnly
      """);
    Files.writeString(jobConfig, """
      name: csv-scoped-transform-spi
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
        new ValidationRuleEvaluator(ProcessorExtensionDefaults.defaultValidationRules(new FileIngestionRuntimeSupport())),
        transformEvaluatorWith(new CsvOnlyNoOpTransform())
    );
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ensureSelectedJobFlatModels("csv-scoped-transform-spi", List.of("Events"), List.of("EventsCsv"));

    ProcessorConfig loadedProcessorConfig = loader.processorConfig();
    assertEquals("csvOnly", loadedProcessorConfig.getMappings().get(0).getFields().get(0).getTransforms().get(0).getType());
  }

  @Test
  void loadsProcessorConfigWhenCustomTransformUsesProviderOwnedConfigEnvelope() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
      - format: csv
        sourceName: Events
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
          transforms:
          - type: configPrefix
            config:
              prefix: CFG-
      """);
    Files.writeString(jobConfig, """
      name: csv-custom-transform-config
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
        new ValidationRuleEvaluator(ProcessorExtensionDefaults.defaultValidationRules(new FileIngestionRuntimeSupport())),
        transformEvaluatorWith(new ConfigPrefixNoOpTransform())
    );
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ensureSelectedJobFlatModels("csv-custom-transform-config", List.of("Events"), List.of("EventsCsv"));

    ProcessorConfig loadedProcessorConfig = loader.processorConfig();
    ProcessorConfig.FieldTransform transform = loadedProcessorConfig.getMappings().get(0).getFields().get(0).getTransforms().get(0);
    assertEquals("configPrefix", transform.getType());
    assertEquals("CFG-", String.valueOf(transform.getConfig().get("prefix")));
  }

  @Test
  void failsFastWhenCustomTransformConfigEnvelopeIsInvalid() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
      - format: csv
        sourceName: Events
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
          transforms:
          - type: configPrefix
      """);
    Files.writeString(jobConfig, """
      name: csv-custom-transform-config-invalid
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
        new ValidationRuleEvaluator(ProcessorExtensionDefaults.defaultValidationRules(new FileIngestionRuntimeSupport())),
        transformEvaluatorWith(new ConfigPrefixNoOpTransform())
    );
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::processorConfig);
    assertTrue(messageChain(exception).contains("transforms[].config.prefix"));
  }

  @Test
  void failsFastWhenZoneConvertTransformHasInvalidToZoneInSelectedJobConfig() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
      - format: csv
        sourceName: Events
        filePath: input/events.csv
        delimiter: ","
        fields:
        - name: eventTimeUtc
          type: String
      """);
    Files.writeString(targetConfig, """
      targets:
      - format: csv
        targetName: EventsCsv
        filePath: output/events.csv
        delimiter: ","
        fields:
        - name: eventTimeLocal
          type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      mappings:
      - source: Events
        target: EventsCsv
        fields:
        - from: eventTimeUtc
          to: eventTimeLocal
          transforms:
          - type: zoneConvert
            config:
              fromZone: UTC
              toZone: NotAZone
              inputPattern: yyyy-MM-dd HH:mm:ss
              outputPattern: yyyy-MM-dd HH:mm:ss
      """);
    Files.writeString(jobConfig, """
      name: zone-convert-invalid-zone
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

    ensureSelectedJobFlatModels("zone-convert-invalid-zone", List.of("Events"), List.of("EventsCsv"));

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(messageChain(exception).contains("Invalid processor configuration for scenario 'zone-convert-invalid-zone'"));
    assertTrue(messageChain(exception).contains("invalid transforms[].config.toZone"));
    assertFalse(messageChain(exception).contains("Target model class not found"));
  }

  @Test
  void failsFastWhenZoneConvertTransformHasInvalidInputPatternInSelectedJobConfig() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
      - format: csv
        sourceName: Events
        filePath: input/events.csv
        delimiter: ","
        fields:
        - name: eventTimeUtc
          type: String
      """);
    Files.writeString(targetConfig, """
      targets:
      - format: csv
        targetName: EventsCsv
        filePath: output/events.csv
        delimiter: ","
        fields:
        - name: eventTimeLocal
          type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      mappings:
      - source: Events
        target: EventsCsv
        fields:
        - from: eventTimeUtc
          to: eventTimeLocal
          transforms:
          - type: zoneConvert
            config:
              fromZone: UTC
              toZone: America/Chicago
              inputPattern: yyyy-MM-dd HH:mm:ss'
              outputPattern: yyyy-MM-dd HH:mm:ss
      """);
    Files.writeString(jobConfig, """
      name: zone-convert-invalid-input-pattern
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

    ensureSelectedJobFlatModels("zone-convert-invalid-input-pattern", List.of("Events"), List.of("EventsCsv"));

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(messageChain(exception).contains("Invalid processor configuration for scenario 'zone-convert-invalid-input-pattern'"));
    assertTrue(messageChain(exception).contains("invalid transforms[].config.inputPattern"));
    assertFalse(messageChain(exception).contains("Target model class not found"));
  }

  @Test
  void failsFastWhenZoneConvertTransformHasInvalidOutputPatternInSelectedJobConfig() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
      - format: csv
        sourceName: Events
        filePath: input/events.csv
        delimiter: ","
        fields:
        - name: eventTimeUtc
          type: String
      """);
    Files.writeString(targetConfig, """
      targets:
      - format: csv
        targetName: EventsCsv
        filePath: output/events.csv
        delimiter: ","
        fields:
        - name: eventTimeLocal
          type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      mappings:
      - source: Events
        target: EventsCsv
        fields:
        - from: eventTimeUtc
          to: eventTimeLocal
          transforms:
          - type: zoneConvert
            config:
              fromZone: UTC
              toZone: America/Chicago
              inputPattern: yyyy-MM-dd HH:mm:ss
              outputPattern: yyyy-MM-dd HH:mm:ss'
      """);
    Files.writeString(jobConfig, """
      name: zone-convert-invalid-output-pattern
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

    ensureSelectedJobFlatModels("zone-convert-invalid-output-pattern", List.of("Events"), List.of("EventsCsv"));

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(messageChain(exception).contains("Invalid processor configuration for scenario 'zone-convert-invalid-output-pattern'"));
    assertTrue(messageChain(exception).contains("invalid transforms[].config.outputPattern"));
    assertFalse(messageChain(exception).contains("Target model class not found"));
  }

  @Test
  void failsFastWhenCsvMappingUsesXmlScopedTransform() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
      - format: csv
        sourceName: Events
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
          transforms:
          - type: xmlOnly
      """);
    Files.writeString(jobConfig, """
      name: csv-xml-scope-mismatch
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
        new ValidationRuleEvaluator(ProcessorExtensionDefaults.defaultValidationRules(new FileIngestionRuntimeSupport())),
        transformEvaluatorWith(new XmlOnlyNoOpTransform())
    );
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::processorConfig);
    assertTrue(exception.getMessage().contains("xmlOnly"));
    assertTrue(exception.getMessage().contains("source format csv"));
  }

  @Test
  void failsFastWhenCsvMappingUsesUndefinedTransformType() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
      - format: csv
        sourceName: Events
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
          transforms:
          - type: missingTransform
      """);
    Files.writeString(jobConfig, """
      name: csv-missing-transform
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
    assertTrue(exception instanceof ProcessorExtensionBindingConfigException);
    assertTrue(exception.getMessage().contains("missingTransform"));
    assertTrue(exception.getMessage().contains("source format csv"));
  }

  @Test
  void failsFastWhenCsvMappingUsesUndefinedRuleType() throws IOException {
    Path sourceConfig = tempDir.resolve("source-config.yaml");
    Path targetConfig = tempDir.resolve("target-config.yaml");
    Path processorConfig = tempDir.resolve("processor-config.yaml");
    Path jobConfig = tempDir.resolve("job-config.yaml");

    Files.writeString(sourceConfig, """
      sources:
      - format: csv
        sourceName: Events
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
          - type: missingRule
      """);
    Files.writeString(jobConfig, """
      name: csv-missing-rule
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
    assertTrue(exception instanceof ProcessorExtensionBindingConfigException);
    assertTrue(exception.getMessage().contains("missingRule"));
    assertTrue(exception.getMessage().contains("source format csv"));
  }

  @Test
  void loadsProcessorConfigWhenExpressionTransformDefinesDerivedFieldWithoutSource() throws IOException {
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
            - name: firstName
              type: String
            - name: lastName
              type: String
      """);
    Files.writeString(targetConfig, """
      targets:
        - format: csv
          targetName: CustomersOut
          filePath: output/customers.csv
          delimiter: ","
          fields:
            - name: fullName
              type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      mappings:
        - source: Customers
          target: CustomersOut
          fields:
            - to: fullName
              transforms:
                - type: expression
                  expression: "#input.firstName + ' ' + #input.lastName"
      """);
    Files.writeString(jobConfig, """
      name: expression-derived-field
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

    ensureSelectedJobFlatModels("expression-derived-field", List.of("Customers"), List.of("CustomersOut"));

    ProcessorConfig loadedProcessorConfig = loader.processorConfig();

    ProcessorConfig.FieldMapping fieldMapping = loadedProcessorConfig.getMappings().get(0).getFields().get(0);
    assertEquals("fullName", fieldMapping.getTo());
    assertTrue(fieldMapping.getFrom() == null || fieldMapping.getFrom().isBlank());
    assertEquals("expression", fieldMapping.getTransforms().get(0).getType());
    assertEquals("#input.firstName + ' ' + #input.lastName", fieldMapping.getTransforms().get(0).getExpression());
  }

  @Test
  void failsFastWhenExpressionTransformHasInvalidSyntax() throws IOException {
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
            - name: firstName
              type: String
      """);
    Files.writeString(targetConfig, """
      targets:
        - format: csv
          targetName: CustomersOut
          filePath: output/customers.csv
          delimiter: ","
          fields:
            - name: fullName
              type: String
      """);
    Files.writeString(processorConfig, """
      type: default
      mappings:
        - source: Customers
          target: CustomersOut
          fields:
            - to: fullName
              transforms:
                - type: expression
                  expression: "#input.firstName + "
      """);
    Files.writeString(jobConfig, """
      name: expression-invalid-syntax
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

    ConfigException exception = assertThrows(ConfigException.class, loader::processorConfig);
    assertTrue(messageChain(exception).contains("expression"));
    assertTrue(messageChain(exception).contains("invalid SpEL"));
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

    ensureSelectedJobFlatModels("duplicate-rule", List.of("Events"), List.of("EventsCsv"));

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

      ensureSelectedJobFlatModels("duplicate-order-by", List.of("Events"), List.of("EventsCsv"));

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

      ensureSelectedJobFlatModels("duplicate-order-by-missing-order", List.of("Events"), List.of("EventsCsv"));

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

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
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

    ensureSelectedJobXmlSourceModels("xml-source-validation-success", List.of("CustomersXml"));
    ensureSelectedJobFlatTargetModels("xml-source-validation-success", List.of("CustomersCsv"));

    RunConfigurationMetadata metadata = loader.buildRunConfigurationMetadata();
    SourceWrapper sourceWrapper = loader.sourceWrapper();

    assertEquals("xml-source-validation-success", metadata.scenarioName());
    assertEquals("CustomersXml", sourceWrapper.getSources().get(0).getSourceName());
  }

  @Test
  void failsFastWhenSelectedXmlGeneratedSourcePackageIsMissing() throws IOException {
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
        - format: xml
          targetName: CustomersXmlTarget
          filePath: output/customers.xml
          rootElement: Customers
          recordElement: Customer
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
          target: CustomersXmlTarget
          fields:
            - from: id
              to: id
            - from: name
              to: name
      """);
    Files.writeString(jobConfig, """
      name: xml-generated-source-package-missing
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: customers-step
          source: CustomersXml
          target: CustomersXmlTarget
      """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(messageChain(exception).contains("XML source root class not found"));
    assertTrue(messageChain(exception).contains(JobScopedPackageNameResolver.resolveSourcePackage("xml-generated-source-package-missing") + ".CustomersXmlXmlRoot"));
  }

  @Test
  void failsFastWhenSelectedNestedXmlGeneratedRecordClassIsMissing() throws IOException {
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
          filePath: %s
          rootElement: Customers
          recordElement: Customer
          flatteningStrategy: NestedXml
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
      name: nested-xml-generated-record-missing
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

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(messageChain(exception).contains("XML source record class not found"));
    assertTrue(messageChain(exception).contains(JobScopedPackageNameResolver.resolveSourcePackage("nested-xml-generated-record-missing") + ".CustomersXmlXmlRecord"));
    assertFalse(messageChain(exception).contains("XML source root class not found"));
  }

  @Test
  void loadsReferencedConfigsWhenSelectedXmlGeneratedPackagesAreAvailable() throws IOException {
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
        - format: xml
          targetName: CustomersXmlTarget
          filePath: output/customers.xml
          rootElement: Customers
          recordElement: Customer
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
          target: CustomersXmlTarget
          fields:
            - from: id
              to: id
            - from: name
              to: name
      """);
    Files.writeString(jobConfig, """
      name: xml-generated-packages-present
      sourceConfigPath: source-config.yaml
      targetConfigPath: target-config.yaml
      processorConfigPath: processor-config.yaml
      steps:
        - name: customers-step
          source: CustomersXml
          target: CustomersXmlTarget
      """);

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.setField(loader, "jobConfigPath", jobConfig.toString());
    ReflectionTestUtils.setField(loader, "allowDemoFallback", false);

    ensureSelectedJobXmlSourceModels("xml-generated-packages-present", List.of("CustomersXml"));
    ensureSelectedJobXmlTargetModels("xml-generated-packages-present", List.of("CustomersXmlTarget"));

    RunConfigurationMetadata metadata = loader.buildRunConfigurationMetadata();
    assertEquals("xml-generated-packages-present", metadata.scenarioName());
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

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
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

    ConfigException exception = assertThrows(ConfigException.class, loader::buildRunConfigurationMetadata);
    assertTrue(exception.getMessage().contains("archive"));
    assertTrue(exception.getMessage().contains("successPath"));
  }

  @Test
  void normalizesRelativeXmlArchivePathsFromSelectedScenarioDirectory() throws IOException {
    Path scenarioDir = tempDir.resolve("xml-archive-scenario");
    Files.createDirectories(scenarioDir.resolve("input"));
    Files.createDirectories(scenarioDir.resolve("definitions"));
    Files.writeString(scenarioDir.resolve("input/events.xml"), "<Events><Event><id>1</id></Event></Events>");
    Files.writeString(scenarioDir.resolve("definitions/events-source-model.yaml"), "type: xml\n");

      XmlSourceConfig xmlSourceConfig = getXmlSourceConfig();

      SourceWrapper sourceWrapper = new SourceWrapper();
    sourceWrapper.setSources(List.of(xmlSourceConfig));

    ConfigLoader loader = new ConfigLoader();
    ReflectionTestUtils.invokeMethod(loader, "normalizeSourceConfigPaths", sourceWrapper, scenarioDir);

    assertEquals(scenarioDir.resolve("input/events.xml").normalize().toString(), xmlSourceConfig.getFilePath());
    assertEquals(scenarioDir.resolve("definitions/events-source-model.yaml").normalize().toString(), xmlSourceConfig.getModelDefinitionPath());
    assertNotNull(xmlSourceConfig.getArchive());
    assertEquals(scenarioDir.resolve("archive/success").normalize().toString(), xmlSourceConfig.getArchive().getSuccessPath());
  }

    private XmlSourceConfig getXmlSourceConfig() {
        XmlSourceConfig xmlSourceConfig = new XmlSourceConfig();
        xmlSourceConfig.setSourceName("EventsXml");
        xmlSourceConfig.setPackageName("com.etl.model.source");
        xmlSourceConfig.setFilePath("input/events.xml");
        xmlSourceConfig.setRootElement("Events");
        xmlSourceConfig.setRecordElement("Event");
        xmlSourceConfig.setModelDefinitionPath("definitions/events-source-model.yaml");
        var archive = new com.etl.config.source.FileArchiveConfig();
        archive.setEnabled(true);
        archive.setSuccessPath("archive/success/");
        archive.setNamePattern("{originalName}-{timestamp}");
        xmlSourceConfig.setArchive(archive);
        return xmlSourceConfig;
    }

    private void ensureSelectedJobFlatModels(String jobName, List<String> sourceNames, List<String> targetNames) throws IOException {
    ensureSelectedJobFlatSourceModels(jobName, sourceNames);
    ensureSelectedJobFlatTargetModels(jobName, targetNames);
  }

  private void ensureSelectedJobFlatSourceModels(String jobName, List<String> sourceNames) throws IOException {
    List<Path> javaFiles = new ArrayList<>();
    String packageName = JobScopedPackageNameResolver.resolveSourcePackage(jobName);
    for (String sourceName : sourceNames) {
      javaFiles.add(writeMinimalClass(packageName, GeneratedModelNamingPolicy.resolveFlatSimpleClassName(sourceName)));
    }
    compileGeneratedSources(javaFiles);
  }

  private void ensureSelectedJobFlatTargetModels(String jobName, List<String> targetNames) throws IOException {
    List<Path> javaFiles = new ArrayList<>();
    String packageName = JobScopedPackageNameResolver.resolveTargetPackage(jobName);
    for (String targetName : targetNames) {
      javaFiles.add(writeMinimalClass(packageName, GeneratedModelNamingPolicy.resolveFlatSimpleClassName(targetName)));
    }
    compileGeneratedSources(javaFiles);
  }

  private void ensureSelectedJobXmlSourceModels(String jobName, List<String> sourceNames) throws IOException {
    List<Path> javaFiles = new ArrayList<>();
    String packageName = JobScopedPackageNameResolver.resolveSourcePackage(jobName);
    for (String sourceName : sourceNames) {
      javaFiles.add(writeMinimalClass(packageName, GeneratedModelNamingPolicy.resolveXmlRootSimpleClassName(sourceName)));
      javaFiles.add(writeMinimalClass(packageName, GeneratedModelNamingPolicy.resolveXmlRecordSimpleClassName(sourceName)));
    }
    compileGeneratedSources(javaFiles);
  }

  private void ensureSelectedJobXmlTargetModels(String jobName, List<String> targetNames) throws IOException {
    List<Path> javaFiles = new ArrayList<>();
    String packageName = JobScopedPackageNameResolver.resolveTargetPackage(jobName);
    for (String targetName : targetNames) {
      javaFiles.add(writeMinimalClass(packageName, GeneratedModelNamingPolicy.resolveXmlRootSimpleClassName(targetName)));
      javaFiles.add(writeMinimalClass(packageName, GeneratedModelNamingPolicy.resolveXmlRecordSimpleClassName(targetName)));
    }
    compileGeneratedSources(javaFiles);
  }

  private Path writeMinimalClass(String packageName, String simpleClassName) throws IOException {
    Path sourceRoot = tempDir.resolve("generated-test-models-src");
    Path packageDirectory = sourceRoot.resolve(packageName.replace('.', '/'));
    Files.createDirectories(packageDirectory);
    Path javaFile = packageDirectory.resolve(simpleClassName + ".java");
    Files.writeString(javaFile, """
        package %s;

        public class %s {
          private Integer id;
          private Integer sequenceNo;
          private String name;
          private String email;
          private String eventTime;
          private String description;
          private String firstName;
          private String lastName;
          private String fullName;
          private String eventCode;

          public Integer getId() {
            return id;
          }

          public void setId(Integer id) {
            this.id = id;
          }

          public Integer getSequenceNo() {
            return sequenceNo;
          }

          public void setSequenceNo(Integer sequenceNo) {
            this.sequenceNo = sequenceNo;
          }

          public String getName() {
            return name;
          }

          public void setName(String name) {
            this.name = name;
          }

          public String getEmail() {
            return email;
          }

          public void setEmail(String email) {
            this.email = email;
          }

          public String getEventTime() {
            return eventTime;
          }

          public void setEventTime(String eventTime) {
            this.eventTime = eventTime;
          }

          public String getDescription() {
            return description;
          }

          public void setDescription(String description) {
            this.description = description;
          }

          public String getFirstName() {
            return firstName;
          }

          public void setFirstName(String firstName) {
            this.firstName = firstName;
          }

          public String getLastName() {
            return lastName;
          }

          public void setLastName(String lastName) {
            this.lastName = lastName;
          }

          public String getFullName() {
            return fullName;
          }

          public void setFullName(String fullName) {
            this.fullName = fullName;
          }

          public String getEventCode() {
            return eventCode;
          }

          public void setEventCode(String eventCode) {
            this.eventCode = eventCode;
          }
        }
        """.formatted(packageName, simpleClassName));
    return javaFile;
  }

  private void compileGeneratedSources(List<Path> javaFiles) throws IOException {
    if (javaFiles == null || javaFiles.isEmpty()) {
      return;
    }
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assertNotNull(compiler, "A JDK is required to compile generated test support classes.");
    Path classRoot = Path.of("target", "test-classes");
    Files.createDirectories(classRoot);
    List<String> options = List.of(
        "-d", classRoot.toString(),
        "-classpath", System.getProperty("java.class.path", "")
    );
    try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
      Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
          javaFiles.stream().map(Path::toFile).toList()
      );
      Boolean success = compiler.getTask(null, fileManager, null, options, null, compilationUnits).call();
      assertEquals(Boolean.TRUE, success, "Generated selected-job test support classes must compile successfully.");
    }
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

  private ListAppender<ILoggingEvent> attachConfigLoaderAppender() {
    configLoaderLogger.detachAndStopAllAppenders();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    configLoaderLogger.addAppender(appender);
    return appender;
  }

  private ColumnConfig column(String name, String type) {
    ColumnConfig column = new ColumnConfig();
    column.setName(name);
    column.setType(type);
    return column;
  }

  private TransformEvaluator transformEvaluatorWith(ProcessorFieldTransform transform) {
    List<ProcessorFieldTransform> transforms = new ArrayList<>(ProcessorExtensionDefaults.defaultTransforms());
    transforms.add(transform);
    return new TransformEvaluator(transforms);
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

  private static final class CsvOnlyNoOpTransform implements ProcessorFieldTransform {

    @Override
    public String getTransformType() {
      return "csvOnly";
    }

    @Override
    public java.util.Set<ModelFormat> supportedSourceFormats() {
      return java.util.Set.of(ModelFormat.CSV);
    }

    @Override
    public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
      return value;
    }
  }

  private static final class XmlOnlyNoOpTransform implements ProcessorFieldTransform {

    @Override
    public String getTransformType() {
      return "xmlOnly";
    }

    @Override
    public java.util.Set<ModelFormat> supportedSourceFormats() {
      return java.util.Set.of(ModelFormat.XML);
    }

    @Override
    public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
      return value;
    }
  }

  private static final class ConfigPrefixNoOpTransform implements ProcessorFieldTransform {

    @Override
    public String getTransformType() {
      return "configPrefix";
    }

    @Override
    public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
                                      ProcessorConfig.FieldMapping fieldMapping,
                                      ProcessorConfig.FieldTransform transform) {
      if (transform == null || transform.getConfig() == null || transform.getConfig().get("prefix") == null
          || String.valueOf(transform.getConfig().get("prefix")).isBlank()) {
        throw new IllegalStateException("FieldMapping transform 'configPrefix' requires transforms[].config.prefix.");
      }
    }

    @Override
    public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
      return value;
    }
  }
}


