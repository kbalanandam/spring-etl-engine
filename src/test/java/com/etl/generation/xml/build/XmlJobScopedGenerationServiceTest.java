package com.etl.generation.xml.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlJobScopedGenerationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void generatesReferencedXmlSourceAndTargetModelsIntoDedicatedOutputRoots() throws Exception {
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
                    fields:
                      - name: HomeAgencyID
                        type: String
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
                    fields:
                      - name: HomeAgencyID
                        type: String
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
        assertTrue(Files.exists(outputRoot.resolve("source/com/etl/generated/job/tagvalidation/source/TVLTagDetails.java")));
        assertTrue(Files.exists(outputRoot.resolve("target/com/etl/generated/job/tagvalidation/target/TagValidationList.java")));

        Path classesDir = tempDir.resolve("compiled");
        compile(result.allGeneratedFiles(), classesDir);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classesDir.toUri().toURL()}, getClass().getClassLoader())) {
            assertNotNull(classLoader.loadClass("com.etl.generated.job.tagvalidation.source.TVLTagDetails"));
            assertNotNull(classLoader.loadClass("com.etl.generated.job.tagvalidation.target.TagValidationList"));
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
        assertTrue(Files.exists(outputRoot.resolve("source/com/etl/generated/job/events/source/Event.java")));
        assertTrue(Files.exists(outputRoot.resolve("source/com/etl/generated/job/events/source/Events.java")));
        assertTrue(Files.exists(outputRoot.resolve("target/com/etl/generated/job/events/target/EventsCsv.java")));

        Path classesDir = tempDir.resolve("generated-flat-compiled");
        compile(result.allGeneratedFiles(), classesDir);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classesDir.toUri().toURL()}, getClass().getClassLoader())) {
            assertNotNull(classLoader.loadClass("com.etl.generated.job.events.source.Event"));
            assertNotNull(classLoader.loadClass("com.etl.generated.job.events.source.Events"));
            assertNotNull(classLoader.loadClass("com.etl.generated.job.events.target.EventsCsv"));
        }
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
}

