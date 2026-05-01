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
        Path scenarioDir = prepareScenarioBundle();
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
        ItemReader<Object> reader = (ItemReader<Object>) readerFactory.createReader(sourceConfig, (Class<Object>) sourceRecordClass);
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

    private Path prepareScenarioBundle() throws Exception {
        Path sourceScenarioDir = Path.of("src", "main", "resources", "config-scenarios", "xml-nested-tag-validation");
        Path scenarioDir = tempDir.resolve("xml-nested-tag-validation");
        copyDirectory(sourceScenarioDir, scenarioDir);

        Path inputFile = scenarioDir.resolve("input/nested-sample.xml").toAbsolutePath().normalize();
        Path outputFile = scenarioDir.resolve("target/tag-validation-export.xml").toAbsolutePath().normalize();
        Files.createDirectories(outputFile.getParent());

        Files.writeString(
                scenarioDir.resolve("source-config.yaml"),
                Files.readString(scenarioDir.resolve("source-config.yaml"))
                        .replace("filePath: input/nested-sample.xml", "filePath: " + toYamlPath(inputFile))
        );
        Files.writeString(
                scenarioDir.resolve("target-config.yaml"),
                Files.readString(scenarioDir.resolve("target-config.yaml"))
                        .replace("filePath: target/tag-validation-export.xml", "filePath: " + toYamlPath(outputFile))
        );

        return scenarioDir;
    }

    private void copyDirectory(Path source, Path target) throws Exception {
        try (var stream = Files.walk(source)) {
            for (Path path : stream.toList()) {
                Path relative = source.relativize(path);
                Path destination = target.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                }
            }
        }
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



