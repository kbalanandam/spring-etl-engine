package com.etl.flow;

import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.config.target.XmlTargetConfig;
import com.etl.generation.xml.build.XmlJobScopedGenerationResult;
import com.etl.generation.xml.build.XmlJobScopedGenerationService;
import com.etl.processor.DynamicProcessorFactory;
import com.etl.processor.impl.DefaultDynamicProcessor;
import com.etl.reader.DynamicReaderFactory;
import com.etl.reader.impl.XmlDynamicReader;
import com.etl.writer.DynamicWriterFactory;
import com.etl.writer.impl.XmlDynamicWriter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.xml.StaxEventItemWriter;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlNestedSourceToXmlTargetFlowTest {

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void runsNestedXmlSourceThroughSharedProcessorIntoXmlTarget() throws Exception {
        Path scenarioDir = createScenario();
        Path generatedSourceRoot = tempDir.resolve("generated-flow-sources");

        ObjectMapper mapper = yamlMapper();
        JobConfig jobConfig = mapper.readValue(scenarioDir.resolve("job-config.yaml").toFile(), JobConfig.class);
        SourceWrapper sourceWrapper = mapper.readValue(scenarioDir.resolve(jobConfig.getSourceConfigPath()).toFile(), SourceWrapper.class);
        TargetWrapper targetWrapper = mapper.readValue(scenarioDir.resolve(jobConfig.getTargetConfigPath()).toFile(), TargetWrapper.class);
        ProcessorConfig processorConfig = mapper.readValue(scenarioDir.resolve(jobConfig.getProcessorConfigPath()).toFile(), ProcessorConfig.class);

        XmlSourceConfig sourceConfig = (XmlSourceConfig) sourceWrapper.getSources().get(0);
        XmlTargetConfig targetConfig = (XmlTargetConfig) targetWrapper.getTargets().get(0);

        XmlJobScopedGenerationResult generationResult = new XmlJobScopedGenerationService()
                .generate(scenarioDir.resolve("job-config.yaml"), generatedSourceRoot);
        assertEquals(1, generationResult.sourceResults().size());
        assertEquals(1, generationResult.targetResults().size());

        compile(generationResult.allGeneratedFiles(), Path.of("target", "test-classes"));

        Class<?> sourceRecordClass = Class.forName(sourceConfig.getPackageName() + "." + sourceConfig.getRecordElement());
        Class<?> targetRecordClass = Class.forName(targetConfig.getPackageName() + "." + targetConfig.getRecordElement());
        assertNotNull(sourceRecordClass);
        assertNotNull(targetRecordClass);

        DynamicReaderFactory readerFactory = new DynamicReaderFactory(List.of(new XmlDynamicReader<>()));
        DynamicProcessorFactory processorFactory = new DynamicProcessorFactory(Map.of("default", new DefaultDynamicProcessor()));
        DynamicWriterFactory writerFactory = new DynamicWriterFactory(List.of(new XmlDynamicWriter()));

        ResolvedModelMetadata metadata = GeneratedModelClassResolver.resolveMetadata(sourceConfig, targetConfig);
        ItemReader<Object> reader = (ItemReader<Object>) (ItemReader) readerFactory.createReader(sourceConfig, (Class<Object>) sourceRecordClass);
        ItemProcessor<Object, Object> processor = processorFactory.getProcessor(processorConfig, sourceConfig, targetConfig, metadata);
        ItemWriter<Object> writer = writerFactory.createWriter(targetConfig, targetRecordClass);

        List<Object> processedItems = new ArrayList<>();
        Object sourceItem;
        while ((sourceItem = reader.read()) != null) {
            Object processed = processor.process(sourceItem);
            if (processed != null) {
                processedItems.add(processed);
            }
        }

        assertEquals(1, processedItems.size());
        Object processedRecord = processedItems.get(0);
        assertEquals("0056", processedRecord.getClass().getMethod("getHomeAgencyId").invoke(processedRecord));
        assertEquals("1300", processedRecord.getClass().getMethod("getTagAgencyId").invoke(processedRecord));
        assertEquals("US", processedRecord.getClass().getMethod("getPlateCountry").invoke(processedRecord));
        assertEquals("KS", processedRecord.getClass().getMethod("getPlateState").invoke(processedRecord));
        assertEquals("4773316", processedRecord.getClass().getMethod("getAccountNumber").invoke(processedRecord));

        StaxEventItemWriter<Object> xmlWriter = (StaxEventItemWriter<Object>) writer;
        xmlWriter.open(new ExecutionContext());
        try {
            xmlWriter.write(new Chunk<>(processedItems));
        } finally {
            xmlWriter.close();
        }

        String xml = Files.readString(Path.of(targetConfig.getFilePath()));
        assertTrue(xml.contains("<TagValidationExports>"));
        assertTrue(xml.contains("<TagValidationExport>"));
        assertTrue(xml.contains("<homeAgencyId>0056</homeAgencyId>"));
        assertTrue(xml.contains("<tagAgencyId>1300</tagAgencyId>"));
        assertTrue(xml.contains("<plateCountry>US</plateCountry>"));
        assertTrue(xml.contains("<plateState>KS</plateState>"));
        assertTrue(xml.contains("<accountNumber>4773316</accountNumber>"));
    }

    private Path createScenario() throws Exception {
        Path scenarioDir = tempDir.resolve("nested-xml-flow-proof");
        Files.createDirectories(scenarioDir.resolve("definitions"));

        Files.writeString(scenarioDir.resolve("job-config.yaml"), """
                name: nested-xml-flow-proof
                sourceConfigPath: source-config.yaml
                targetConfigPath: target-config.yaml
                processorConfigPath: processor-config.yaml
                steps:
                  - name: nested-xml-to-xml-step
                    source: TagValidationSource
                    target: TagValidationExport
                """);

        Files.writeString(scenarioDir.resolve("source-config.yaml"), """
                sources:
                  - format: xml
                    sourceName: TagValidationSource
                    packageName: com.etl.generated.job.flowproof.source
                    filePath: %s
                    rootElement: TagValidationList
                    recordElement: TVLTagDetails
                    flatteningStrategy: NestedXml
                    modelDefinitionPath: definitions/nested-source-model.yaml
                    fields:
                      - name: HomeAgencyID
                        type: String
                """.formatted(toYamlPath(Path.of("src", "main", "resources", "config-scenarios", "xml-model-spike", "nested-sample.xml"))));

        Files.writeString(scenarioDir.resolve("target-config.yaml"), """
                targets:
                  - format: xml
                    targetName: TagValidationExport
                    packageName: com.etl.generated.job.flowproof.target
                    filePath: %s
                    rootElement: TagValidationExports
                    recordElement: TagValidationExport
                    fields:
                      - name: homeAgencyId
                        type: String
                      - name: tagAgencyId
                        type: String
                      - name: plateCountry
                        type: String
                      - name: plateState
                        type: String
                      - name: accountNumber
                        type: String
                """.formatted(toYamlPath(tempDir.resolve("tag-validation-export.xml"))));

        Files.writeString(scenarioDir.resolve("processor-config.yaml"), """
                type: default
                mappings:
                  - source: TagValidationSource
                    target: TagValidationExport
                    fields:
                      - from: HomeAgencyID
                        to: homeAgencyId
                      - from: TagAgencyID
                        to: tagAgencyId
                      - from: TVLPlateDetails.PlateCountry
                        to: plateCountry
                      - from: TVLPlateDetails.PlateState
                        to: plateState
                      - from: TVLAccountDetails.AccountNumber
                        to: accountNumber
                """);

        Files.writeString(scenarioDir.resolve("definitions/nested-source-model.yaml"), Files.readString(
                Path.of("src", "main", "resources", "config-scenarios", "xml-model-spike", "nested-source-model.yaml")
        ));
        return scenarioDir;
    }

    private ObjectMapper yamlMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.findAndRegisterModules();
        return mapper;
    }

    private void compile(List<Path> javaFiles, Path classRoot) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A JDK is required to compile generated sources during the flow proof test.");
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
            assertEquals(Boolean.TRUE, success, "Generated XML flow proof sources must compile successfully.");
        }
    }

    private String toYamlPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }
}

