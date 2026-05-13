package com.etl.generation.xml.build;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlJobScopedGenerationServiceTest {

    private final Logger generationServiceLogger = (Logger) LoggerFactory.getLogger(XmlJobScopedGenerationService.class);

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        generationServiceLogger.detachAndStopAllAppenders();
    }

    @Test
    void failsFastWhenExplicitJobConfigNameIsBlank() throws Exception {
        Path scenarioDir = tempDir.resolve("blank-name-job");
        Files.createDirectories(scenarioDir);

        Files.writeString(scenarioDir.resolve("job-config.yaml"), """
                name:
                sourceConfigPath: source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                steps:
                  - name: blank-name-step
                    source: CustomersCsv
                    target: CustomersCsv
                """);
        Files.writeString(scenarioDir.resolve("processor-config.yaml"), "type: default\n");
        Files.writeString(scenarioDir.resolve("source-config.yaml"), """
                sources:
                  - format: csv
                    sourceName: CustomersCsv
                    filePath: input/customers.csv
                    delimiter: ","
                    fields:
                      - name: id
                        type: String
                """);
        Files.writeString(scenarioDir.resolve("target-config.yaml"), """
                targets:
                  - format: csv
                    targetName: CustomersCsv
                    filePath: output/customers.csv
                    fields:
                      - name: id
                        type: String
                """);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new XmlJobScopedGenerationService().generate(scenarioDir.resolve("job-config.yaml"), tempDir.resolve("generated-output"))
        );

        assertTrue(exception.getMessage().contains("require a non-blank 'name'"));
        assertTrue(exception.getMessage().contains(scenarioDir.resolve("job-config.yaml").toString()));
    }

    @Test
    void generatesReferencedXmlSourceAndTargetModelsIntoDedicatedOutputRootsWhenNestedSourceUsesExternalModelDefinition() throws Exception {
        Path scenarioDir = tempDir.resolve("tag-validation-job");
        Files.createDirectories(scenarioDir.resolve("definitions"));

        Files.writeString(scenarioDir.resolve("job-config.yaml"), """
                name: tag-validation-job
                sourceConfigPath: source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                steps:
                  - name: tag-validation-step
                    source: TagValidationXml
                    target: TagValidationExport
                """);
        Files.writeString(scenarioDir.resolve("processor-config.yaml"), "type: default\n");
        Files.writeString(scenarioDir.resolve("source-config.yaml"), """
                sources:
                  - format: xml
                    sourceName: TagValidationXml
                    packageName: com.etl.generated.job.tagvalidation.source
                    filePath: input/tag-validation.xml
                    rootElement: TagValidationList
                    recordElement: TVLTagDetails
                    modelDefinitionPath: definitions/nested-source-model.yaml
                  - format: csv
                    sourceName: IgnoredCsvSource
                    packageName: com.etl.generated.job.tagvalidation.ignored
                    filePath: ignored.csv
                    delimiter: ","
                    fields:
                      - name: ignored
                        type: String
                """);
        Files.writeString(scenarioDir.resolve("target-config.yaml"), """
                targets:
                  - format: xml
                    targetName: TagValidationExport
                    packageName: com.etl.generated.job.tagvalidation.target
                    filePath: output/tag-validation.xml
                    rootElement: TagValidationList
                    recordElement: TVLTagDetails
                    modelDefinitionPath: definitions/nested-target-model.yaml
                  - format: csv
                    targetName: IgnoredCsvTarget
                    packageName: com.etl.generated.job.tagvalidation.ignored
                    filePath: ignored.csv
                    delimiter: ","
                    fields:
                      - name: ignored
                        type: String
                """);
        Files.writeString(scenarioDir.resolve("definitions/nested-source-model.yaml"), """
                packageName: ignored.by.service
                rootElement: TagValidationList
                recordElement: TVLTagDetails
                fields:
                  - name: HomeAgencyID
                    type: String
                  - name: TVLPlateDetails
                    className: TVLPlateDetails
                    fields:
                      - name: PlateCountry
                        type: String
                      - name: PlateState
                        type: String
                """);
        Files.writeString(scenarioDir.resolve("definitions/nested-target-model.yaml"), """
                packageName: ignored.by.service
                rootElement: TagValidationList
                recordElement: TVLTagDetails
                fields:
                  - name: HomeAgencyID
                    type: String
                  - name: TVLAccountDetails
                    className: TVLAccountDetails
                    fields:
                      - name: AccountNumber
                        type: String
                """);

        Path outputRoot = tempDir.resolve("generated-output");
        XmlJobScopedGenerationResult result = new XmlJobScopedGenerationService()
                .generate(scenarioDir.resolve("job-config.yaml"), outputRoot);

        assertEquals("tag-validation-job", result.jobName());
        assertEquals(1, result.sourceResults().size());
        assertEquals(1, result.targetResults().size());
        assertTrue(result.allGeneratedFiles().stream().allMatch(Files::exists));
        assertTrue(Files.exists(outputRoot.resolve("source/com/etl/generated/job/tagvalidation/source/TagValidationXmlXmlRecord.java")));
        assertTrue(Files.exists(outputRoot.resolve("target/com/etl/generated/job/tagvalidation/target/TagValidationExportXmlRoot.java")));
        assertTrue(Files.readString(outputRoot.resolve("target/com/etl/generated/job/tagvalidation/target/TagValidationExportXmlRoot.java")).contains("Generated by OneFlow"));

        Path classesDir = tempDir.resolve("compiled");
        compile(result.allGeneratedFiles(), classesDir);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classesDir.toUri().toURL()}, getClass().getClassLoader())) {
            assertNotNull(classLoader.loadClass("com.etl.generated.job.tagvalidation.source.TagValidationXmlXmlRecord"));
            assertNotNull(classLoader.loadClass("com.etl.generated.job.tagvalidation.target.TagValidationExportXmlRoot"));
        }
    }

    @Test
    void mapsFlatXmlConfigsToDefinitionsWhenNoExternalModelDefinitionIsProvided() throws Exception {
        Path scenarioDir = tempDir.resolve("events-job");
        Files.createDirectories(scenarioDir);

        Files.writeString(scenarioDir.resolve("job-config.yaml"), """
                name: events-job
                sourceConfigPath: source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                steps:
                  - name: events-step
                    source: EventsXml
                    target: EventsCsv
                """);
        Files.writeString(scenarioDir.resolve("processor-config.yaml"), "type: default\n");
        Files.writeString(scenarioDir.resolve("source-config.yaml"), """
                sources:
                  - format: xml
                    sourceName: EventsXml
                    packageName: com.etl.generated.job.events.source
                    filePath: input/events.xml
                    rootElement: Events
                    recordElement: Event
                    fields:
                      - name: eventCode
                        type: String
                      - name: eventTime
                        type: String
                """);
        Files.writeString(scenarioDir.resolve("target-config.yaml"), """
                targets:
                  - format: csv
                    targetName: EventsCsv
                    packageName: com.etl.generated.job.events.target
                    filePath: output/events.csv
                    delimiter: ","
                    fields:
                      - name: eventCode
                        type: String
                      - name: eventTime
                        type: String
                """);

        Path outputRoot = tempDir.resolve("generated-flat-output");
        XmlJobScopedGenerationResult result = new XmlJobScopedGenerationService()
                .generate(scenarioDir.resolve("job-config.yaml"), outputRoot);

        assertEquals("events-job", result.jobName());
        assertEquals(1, result.sourceResults().size());
        assertEquals(1, result.targetResults().size());
        assertTrue(Files.exists(outputRoot.resolve("source/com/etl/generated/job/events/source/EventsXmlXmlRecord.java")));
        assertTrue(Files.exists(outputRoot.resolve("source/com/etl/generated/job/events/source/EventsXmlXmlRoot.java")));
        assertTrue(Files.exists(outputRoot.resolve("target/com/etl/generated/job/events/target/EventsCsvModel.java")));

        Path classesDir = tempDir.resolve("generated-flat-compiled");
        compile(result.allGeneratedFiles(), classesDir);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classesDir.toUri().toURL()}, getClass().getClassLoader())) {
            assertNotNull(classLoader.loadClass("com.etl.generated.job.events.source.EventsXmlXmlRecord"));
            assertNotNull(classLoader.loadClass("com.etl.generated.job.events.source.EventsXmlXmlRoot"));
            assertNotNull(classLoader.loadClass("com.etl.generated.job.events.target.EventsCsvModel"));
        }
    }

    @Test
    void generatesFlatJsonTargetAndDerivesPackagesWhenSelectedJobOmitsPackageNames() throws Exception {
        Path scenarioDir = tempDir.resolve("xml-to-json-events-job");
        Files.createDirectories(scenarioDir.resolve("input"));
        Files.createDirectories(scenarioDir.resolve("definitions"));

        Files.writeString(scenarioDir.resolve("job-config.yaml"), """
                name: xml-to-json-events
                sourceConfigPath: source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                steps:
                  - name: events-step
                    source: Events
                    target: EventsJson
                """);
        Files.writeString(scenarioDir.resolve("processor-config.yaml"), "type: default\n");
        Files.writeString(scenarioDir.resolve("definitions/events-source-model.yaml"), """
                packageName: ignored.by.runtime.bundle
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

        Path outputRoot = tempDir.resolve("generated-json-output");
        XmlJobScopedGenerationResult result = new XmlJobScopedGenerationService()
                .generate(scenarioDir.resolve("job-config.yaml"), outputRoot);

        assertEquals("xml-to-json-events", result.jobName());
        assertEquals(1, result.sourceResults().size());
        assertEquals(1, result.targetResults().size());
        assertTrue(Files.exists(outputRoot.resolve("source/com/etl/generated/job/xmltojsonevents/source/EventsXmlRecord.java")));
        assertTrue(Files.exists(outputRoot.resolve("source/com/etl/generated/job/xmltojsonevents/source/EventsXmlRoot.java")));
        assertTrue(Files.exists(outputRoot.resolve("target/com/etl/generated/job/xmltojsonevents/target/EventsJsonModel.java")));

        Path classesDir = tempDir.resolve("generated-json-compiled");
        compile(result.allGeneratedFiles(), classesDir);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classesDir.toUri().toURL()}, getClass().getClassLoader())) {
            assertNotNull(classLoader.loadClass("com.etl.generated.job.xmltojsonevents.source.EventsXmlRecord"));
            assertNotNull(classLoader.loadClass("com.etl.generated.job.xmltojsonevents.source.EventsXmlRoot"));
            assertNotNull(classLoader.loadClass("com.etl.generated.job.xmltojsonevents.target.EventsJsonModel"));
        }
    }

    @Test
    void warnsWhenExplicitGeneratedPackageNameDiffersFromSelectedJobDerivedPackage() throws Exception {
        Path scenarioDir = tempDir.resolve("xml-to-json-events-warning-job");
        Files.createDirectories(scenarioDir.resolve("input"));

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
        Files.writeString(scenarioDir.resolve("processor-config.yaml"), "type: default\n");
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

        ListAppender<ILoggingEvent> appender = attachAppender();
        Path outputRoot = tempDir.resolve("generated-warning-output");
        XmlJobScopedGenerationResult result = new XmlJobScopedGenerationService()
                .generate(scenarioDir.resolve("job-config.yaml"), outputRoot);

        assertEquals("xml-to-json-events-warning", result.jobName());
        assertTrue(Files.exists(outputRoot.resolve("source/com/etl/generated/job/xmltojsonevents/source/EventsXmlRecord.java")));
        assertTrue(Files.exists(outputRoot.resolve("target/com/etl/generated/job/xmltojsonevents/target/EventsJsonModel.java")));
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("source config 'Events'")
                && event.getFormattedMessage().contains("com.etl.generated.job.xmltojsonevents.source")
                && event.getFormattedMessage().contains("com.etl.generated.job.xmltojsoneventswarning.source")
                && event.getFormattedMessage().contains("still honored for compatibility")));
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("target config 'EventsJson'")
                && event.getFormattedMessage().contains("com.etl.generated.job.xmltojsonevents.target")
                && event.getFormattedMessage().contains("com.etl.generated.job.xmltojsoneventswarning.target")));
    }

    @Test
    void generatesSelectedFlatCsvAndRelationalSourceAndTargetModels() throws Exception {
        Path scenarioDir = tempDir.resolve("flat-job");
        Files.createDirectories(scenarioDir);

        Files.writeString(scenarioDir.resolve("job-config.yaml"), """
                name: flat-job
                sourceConfigPath: source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                steps:
                  - name: customers-to-sql-step
                    source: CustomersCsv
                    target: CustomersSql
                """);
        Files.writeString(scenarioDir.resolve("processor-config.yaml"), "type: default\n");
        Files.writeString(scenarioDir.resolve("source-config.yaml"), """
                sources:
                  - format: csv
                    sourceName: CustomersCsv
                    packageName: com.etl.generated.job.flatjob.source
                    filePath: input/customers.csv
                    delimiter: ","
                    fields:
                      - name: id
                        type: int
                      - name: name
                        type: String
                  - format: relational
                    sourceName: IgnoredRelationalSource
                    packageName: com.etl.generated.job.flatjob.ignored
                    table: Customers
                    schema: dbo
                    connection:
                      vendor: sqlserver
                      jdbcUrl: jdbc:sqlserver://ignored
                      schema: dbo
                      username: ignored
                      password: ignored
                      driverClassName: com.microsoft.sqlserver.jdbc.SQLServerDriver
                    fields:
                      - name: ignored
                        type: String
                """);
        Files.writeString(scenarioDir.resolve("target-config.yaml"), """
                targets:
                  - format: relational
                    targetName: CustomersSql
                    packageName: com.etl.generated.job.flatjob.target
                    schema: dbo
                    table: Customers
                    writeMode: insert
                    batchSize: 100
                    connection:
                      vendor: sqlserver
                      jdbcUrl: jdbc:sqlserver://ignored
                      schema: dbo
                      username: ignored
                      password: ignored
                      driverClassName: com.microsoft.sqlserver.jdbc.SQLServerDriver
                    fields:
                      - name: id
                        type: int
                      - name: name
                        type: String
                  - format: csv
                    targetName: IgnoredCsvTarget
                    packageName: com.etl.generated.job.flatjob.ignored
                    filePath: output/ignored.csv
                    delimiter: ","
                    fields:
                      - name: ignored
                        type: String
                """);

        Path outputRoot = tempDir.resolve("generated-flat-model-output");
        XmlJobScopedGenerationResult result = new XmlJobScopedGenerationService()
                .generate(scenarioDir.resolve("job-config.yaml"), outputRoot);

        assertEquals("flat-job", result.jobName());
        assertEquals(1, result.sourceResults().size());
        assertEquals(1, result.targetResults().size());
        assertTrue(Files.exists(outputRoot.resolve("source/com/etl/generated/job/flatjob/source/CustomersCsvModel.java")));
        assertTrue(Files.exists(outputRoot.resolve("target/com/etl/generated/job/flatjob/target/CustomersSqlModel.java")));

        Path classesDir = tempDir.resolve("generated-flat-model-compiled");
        compile(result.allGeneratedFiles(), classesDir);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classesDir.toUri().toURL()}, getClass().getClassLoader())) {
            assertNotNull(classLoader.loadClass("com.etl.generated.job.flatjob.source.CustomersCsvModel"));
            assertNotNull(classLoader.loadClass("com.etl.generated.job.flatjob.target.CustomersSqlModel"));
        }
    }

    @Test
    void failsFastWhenLogicalHandoffNameIsConsumedBeforeAnyEarlierStepProducesIt() throws Exception {
        Path scenarioDir = tempDir.resolve("naming-guardrail-job");
        Files.createDirectories(scenarioDir);

        Files.writeString(scenarioDir.resolve("job-config.yaml"), """
                name: naming-guardrail-job
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
        Files.writeString(scenarioDir.resolve("processor-config.yaml"), "type: default\n");
        Files.writeString(scenarioDir.resolve("source-config.yaml"), """
                sources:
                  - format: csv
                    sourceName: CustomersIntermediate
                    packageName: com.etl.model.source
                    filePath: input/customers.csv
                    delimiter: ","
                    fields:
                      - name: id
                        type: int
                  - format: csv
                    sourceName: IngressCustomers
                    packageName: com.etl.model.source
                    filePath: input/customers.csv
                    delimiter: ","
                    fields:
                      - name: id
                        type: int
                """);
        Files.writeString(scenarioDir.resolve("target-config.yaml"), """
                targets:
                  - format: json
                    targetName: FinalCustomersJson
                    packageName: com.etl.model.target
                    filePath: output/customers.json
                    fields:
                      - name: id
                        type: int
                  - format: json
                    targetName: CustomersIntermediate
                    packageName: com.etl.model.target
                    filePath: output/intermediate.json
                    fields:
                      - name: id
                        type: int
                """);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new XmlJobScopedGenerationService().generate(scenarioDir.resolve("job-config.yaml"), tempDir.resolve("generated-output"))
        );

        assertTrue(exception.getMessage().contains("CustomersIntermediate"));
        assertTrue(exception.getMessage().contains("before it is produced"));
    }

  @Test
  void derivesDefaultPackagesWhenSelectedJobOmitsPackageNames() throws Exception {
    Path scenarioDir = tempDir.resolve("derived-package-job");
    Files.createDirectories(scenarioDir.resolve("definitions"));

    Files.writeString(scenarioDir.resolve("job-config.yaml"), """
        name: csv-to-nested-xml
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: customers-to-nested-xml-step
            source: CustomersCsv
            target: CustomersNestedXml
        """);
    Files.writeString(scenarioDir.resolve("processor-config.yaml"), "type: default\n");
    Files.writeString(scenarioDir.resolve("source-config.yaml"), """
        sources:
          - format: csv
            sourceName: CustomersCsv
            filePath: input/customers.csv
            delimiter: ","
            fields:
              - name: id
                type: String
              - name: email
                type: String
        """);
    Files.writeString(scenarioDir.resolve("target-config.yaml"), """
        targets:
          - format: xml
            targetName: CustomersNestedXml
            filePath: output/customers-nested.xml
            rootElement: Customers
            recordElement: CustomerRecord
            modelDefinitionPath: definitions/nested-target-model.yaml
        """);
    Files.writeString(scenarioDir.resolve("definitions/nested-target-model.yaml"), """
        packageName: ignored.by.service
        rootElement: Customers
        recordElement: CustomerRecord
        fields:
          - name: id
            type: String
          - name: profile
            className: Profile
            fields:
              - name: email
                type: String
        """);

    Path outputRoot = tempDir.resolve("derived-package-output");
    XmlJobScopedGenerationResult result = new XmlJobScopedGenerationService()
        .generate(scenarioDir.resolve("job-config.yaml"), outputRoot);

    assertEquals("csv-to-nested-xml", result.jobName());
    assertTrue(Files.exists(outputRoot.resolve("source/com/etl/generated/job/csvtonestedxml/source/CustomersCsvModel.java")));
    assertTrue(Files.exists(outputRoot.resolve("target/com/etl/generated/job/csvtonestedxml/target/CustomersNestedXmlXmlRoot.java")));
    assertTrue(Files.exists(outputRoot.resolve("target/com/etl/generated/job/csvtonestedxml/target/CustomersNestedXmlXmlRecord.java")));
  }

  @Test
  void resolvesLegacyConfigScenariosAliasPathToCanonicalConfigJobsBundle() throws Exception {
    Path canonicalScenarioDir = tempDir.resolve("src/main/resources/config-jobs/alias-job");
    Files.createDirectories(canonicalScenarioDir.resolve("definitions"));

    Files.writeString(canonicalScenarioDir.resolve("job-config.yaml"), """
        name: alias-job
        sourceConfigPath: source-config.yaml
        targetConfigPath: target-config.yaml
        processorConfigPath: processor-config.yaml
        steps:
          - name: alias-step
            source: EventsXml
            target: EventsCsv
        """);
    Files.writeString(canonicalScenarioDir.resolve("processor-config.yaml"), "type: default\n");
    Files.writeString(canonicalScenarioDir.resolve("source-config.yaml"), """
        sources:
          - format: xml
            sourceName: EventsXml
            packageName: com.etl.generated.job.aliasjob.source
            filePath: input/events.xml
            rootElement: Events
            recordElement: Event
            fields:
              - name: eventCode
                type: String
        """);
    Files.writeString(canonicalScenarioDir.resolve("target-config.yaml"), """
        targets:
          - format: csv
            targetName: EventsCsv
            packageName: com.etl.generated.job.aliasjob.target
            filePath: output/events.csv
            delimiter: ","
            fields:
              - name: eventCode
                type: String
        """);

    Path requestedAliasJobConfig = tempDir.resolve("src/main/resources/config-scenarios/alias-job/job-config.yaml");
    Path outputRoot = tempDir.resolve("generated-alias-output");

    XmlJobScopedGenerationResult result = new XmlJobScopedGenerationService().generate(requestedAliasJobConfig, outputRoot);

    assertEquals("alias-job", result.jobName());
    assertEquals(1, result.sourceResults().size());
    assertEquals(1, result.targetResults().size());
    assertTrue(Files.exists(outputRoot.resolve("source/com/etl/generated/job/aliasjob/source/EventsXmlXmlRecord.java")));
    assertTrue(Files.exists(outputRoot.resolve("target/com/etl/generated/job/aliasjob/target/EventsCsvModel.java")));
  }

    private void compile(List<Path> javaFiles, Path classRoot) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A JDK is required to compile generated sources during the build-time generation test.");
        Files.createDirectories(classRoot);
        String runtimeClasspath = System.getProperty("java.class.path", "");
        List<String> options = List.of(
                "-d", classRoot.toString(),
                "-classpath", runtimeClasspath
        );
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(
                    javaFiles.stream().map(Path::toFile).toList()
            );
            Boolean success = compiler.getTask(null, fileManager, null, options, null, compilationUnits).call();
            assertEquals(Boolean.TRUE, success, "Generated XML build-time sources must compile successfully.");
        }
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        generationServiceLogger.detachAndStopAllAppenders();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        generationServiceLogger.addAppender(appender);
        return appender;
    }
}

