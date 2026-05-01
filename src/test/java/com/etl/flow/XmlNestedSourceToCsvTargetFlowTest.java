package com.etl.flow;

import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.generation.xml.build.XmlJobScopedGenerationResult;
import com.etl.generation.xml.build.XmlJobScopedGenerationService;
import com.etl.processor.DynamicProcessorFactory;
import com.etl.processor.impl.DefaultDynamicProcessor;
import com.etl.reader.DynamicReaderFactory;
import com.etl.reader.impl.XmlDynamicReader;
import com.etl.writer.DynamicWriterFactory;
import com.etl.writer.impl.CsvDynamicWriter;
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
import org.springframework.batch.item.file.FlatFileItemWriter;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class XmlNestedSourceToCsvTargetFlowTest {

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void runsNestedXmlSourceThroughSharedProcessorIntoCsvTarget() throws Exception {
        Path scenarioDir = prepareScenarioBundle();
        Path generatedSourceRoot = tempDir.resolve("generated-flow-sources");

        ObjectMapper mapper = yamlMapper();
        JobConfig jobConfig = mapper.readValue(scenarioDir.resolve("job-config.yaml").toFile(), JobConfig.class);
        SourceWrapper sourceWrapper = mapper.readValue(scenarioDir.resolve(jobConfig.getSourceConfigPath()).toFile(), SourceWrapper.class);
        TargetWrapper targetWrapper = mapper.readValue(scenarioDir.resolve(jobConfig.getTargetConfigPath()).toFile(), TargetWrapper.class);
        ProcessorConfig processorConfig = mapper.readValue(scenarioDir.resolve(jobConfig.getProcessorConfigPath()).toFile(), ProcessorConfig.class);

        XmlSourceConfig sourceConfig = (XmlSourceConfig) sourceWrapper.getSources().get(0);
        CsvTargetConfig targetConfig = (CsvTargetConfig) targetWrapper.getTargets().get(0);

        XmlJobScopedGenerationResult generationResult = new XmlJobScopedGenerationService()
                .generate(scenarioDir.resolve("job-config.yaml"), generatedSourceRoot);
        assertEquals(1, generationResult.sourceResults().size());
        assertEquals(1, generationResult.targetResults().size());

        compile(generationResult.allGeneratedFiles(), Path.of("target", "test-classes"));

        Class<?> sourceRecordClass = Class.forName(sourceConfig.getPackageName() + "." + sourceConfig.getRecordElement());
        Class<?> targetRecordClass = Class.forName(targetConfig.getPackageName() + "." + targetConfig.getTargetName());
        assertNotNull(sourceRecordClass);
        assertNotNull(targetRecordClass);

        DynamicReaderFactory readerFactory = new DynamicReaderFactory(List.of(new XmlDynamicReader<>()));
        DynamicProcessorFactory processorFactory = new DynamicProcessorFactory(Map.of("default", new DefaultDynamicProcessor()));
        DynamicWriterFactory writerFactory = new DynamicWriterFactory(List.of(new CsvDynamicWriter()));

        ResolvedModelMetadata metadata = GeneratedModelClassResolver.resolveMetadata(sourceConfig, targetConfig);
        ItemReader<Object> reader = readerFactory.createReader(sourceConfig, (Class<Object>) sourceRecordClass);
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

        Object firstProcessedRecord = processedItems.get(0);
        assertEquals("0056", firstProcessedRecord.getClass().getMethod("getHomeAgencyId").invoke(firstProcessedRecord));
        assertEquals("1300", firstProcessedRecord.getClass().getMethod("getTagAgencyId").invoke(firstProcessedRecord));
        assertEquals("0003518358", firstProcessedRecord.getClass().getMethod("getTagSerialNumber").invoke(firstProcessedRecord));
        assertEquals("7064AFP", firstProcessedRecord.getClass().getMethod("getPlateNumber").invoke(firstProcessedRecord));
        assertEquals("US", firstProcessedRecord.getClass().getMethod("getPlateCountry").invoke(firstProcessedRecord));
        assertEquals("KS", firstProcessedRecord.getClass().getMethod("getPlateState").invoke(firstProcessedRecord));
        assertEquals("4773316", firstProcessedRecord.getClass().getMethod("getAccountNumber").invoke(firstProcessedRecord));

        FlatFileItemWriter<Object> csvWriter = (FlatFileItemWriter<Object>) writer;
        csvWriter.open(new ExecutionContext());
        try {
            csvWriter.write(new Chunk<>(processedItems));
        } finally {
            csvWriter.close();
        }

        List<String> csvLines = Files.readAllLines(Path.of(targetConfig.getFilePath())).stream()
                .filter(line -> !line.isBlank())
                .toList();
        assertEquals(List.of(
                "0056,1300,0003518358,7064AFP,US,KS,4773316"
        ), csvLines);
    }

    private Path prepareScenarioBundle() throws Exception {
        Path sourceScenarioDir = Path.of("src", "main", "resources", "config-scenarios", "xml-nested-to-csv-tag-validation");
        Path sharedSampleDir = Path.of("src", "main", "resources", "config-scenarios", "xml-nested-tag-validation", "input");
        Path scenarioDir = tempDir.resolve("xml-nested-to-csv-tag-validation");
        copyDirectory(sourceScenarioDir, scenarioDir);
        Files.copy(sharedSampleDir.resolve("nested-sample.xml"), scenarioDir.resolve("input/nested-sample.xml"));

        Path inputFile = scenarioDir.resolve("input/nested-sample.xml").toAbsolutePath().normalize();
        Path outputFile = scenarioDir.resolve("target/tag-validation-export.csv").toAbsolutePath().normalize();
        Files.createDirectories(outputFile.getParent());

        Files.writeString(
                scenarioDir.resolve("source-config.yaml"),
                Files.readString(scenarioDir.resolve("source-config.yaml"))
                        .replaceFirst("(?m)^\\s*filePath:.*$", "    filePath: " + Matcher.quoteReplacement(toYamlPath(inputFile)))
        );
        Files.writeString(
                scenarioDir.resolve("target-config.yaml"),
                Files.readString(scenarioDir.resolve("target-config.yaml"))
                        .replaceFirst("(?m)^\\s*filePath:.*$", "    filePath: " + Matcher.quoteReplacement(toYamlPath(outputFile)))
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




