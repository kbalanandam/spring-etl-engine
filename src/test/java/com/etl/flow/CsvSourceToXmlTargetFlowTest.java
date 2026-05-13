package com.etl.flow;

import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.JobScopedPackageNameResolver;
import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.ColumnConfig;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.config.target.XmlTargetConfig;
import com.etl.generation.xml.build.XmlJobScopedGenerationResult;
import com.etl.generation.xml.build.XmlJobScopedGenerationService;
import com.etl.processor.DynamicProcessorFactory;
import com.etl.processor.impl.DefaultDynamicProcessor;
import com.etl.reader.DynamicReaderFactory;
import com.etl.reader.impl.CsvDynamicReader;
import com.etl.writer.DynamicWriterFactory;
import com.etl.writer.impl.SingleObjectXmlWriter;
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
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemWriter;

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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvSourceToXmlTargetFlowTest {

    @TempDir
    Path tempDir;

    @Test
    void runsCustomerLoadCsvSourceThroughSharedProcessorIntoFlatXmlWrapperTarget() throws Exception {
        Path scenarioDir = prepareScenarioBundle();
        Path generatedSourceRoot = tempDir.resolve("generated-customer-load-sources");

        ObjectMapper mapper = yamlMapper();
        JobConfig jobConfig = mapper.readValue(scenarioDir.resolve("job-config.yaml").toFile(), JobConfig.class);
        SourceWrapper sourceWrapper = mapper.readValue(scenarioDir.resolve(jobConfig.getSourceConfigPath()).toFile(), SourceWrapper.class);
        TargetWrapper targetWrapper = mapper.readValue(scenarioDir.resolve(jobConfig.getTargetConfigPath()).toFile(), TargetWrapper.class);
        ProcessorConfig processorConfig = mapper.readValue(scenarioDir.resolve(jobConfig.getProcessorConfigPath()).toFile(), ProcessorConfig.class);
        applyDerivedPackages(jobConfig, scenarioDir, sourceWrapper, targetWrapper);

        CsvSourceConfig sourceConfig = (CsvSourceConfig) sourceWrapper.getSources().get(0);
        XmlTargetConfig targetConfig = (XmlTargetConfig) targetWrapper.getTargets().get(0);

        XmlJobScopedGenerationResult generationResult = new XmlJobScopedGenerationService()
                .generate(scenarioDir.resolve("job-config.yaml"), generatedSourceRoot);
        assertEquals(1, generationResult.sourceResults().size());
        assertEquals(1, generationResult.targetResults().size());
        assertTrue(generationResult.allGeneratedFiles().size() >= 3);

        compile(generationResult.allGeneratedFiles(), Path.of("target", "test-classes"));

        Class<?> sourceRecordClass = GeneratedModelClassResolver.resolveSourceClass(sourceConfig);
        Class<?> targetProcessingClass = GeneratedModelClassResolver.resolveTargetProcessingClass(targetConfig);
        Class<?> targetWriteClass = GeneratedModelClassResolver.resolveTargetWriteClass(targetConfig);
        assertNotNull(sourceRecordClass);
        assertNotNull(targetProcessingClass);
        assertNotNull(targetWriteClass);

        DynamicReaderFactory readerFactory = new DynamicReaderFactory(List.of(new CsvDynamicReader<>()));
        DynamicProcessorFactory processorFactory = new DynamicProcessorFactory(Map.of("default", new DefaultDynamicProcessor()));
        DynamicWriterFactory writerFactory = new DynamicWriterFactory(List.of(new XmlDynamicWriter()));

        ResolvedModelMetadata metadata = GeneratedModelClassResolver.resolveMetadata(sourceConfig, targetConfig);
        ItemReader<Object> reader = createReader(readerFactory, sourceConfig, sourceRecordClass);
        ItemProcessor<Object, Object> processor = processorFactory.getProcessor(processorConfig, sourceConfig, targetConfig, metadata);
        ItemWriter<Object> writer = writerFactory.createWriter(targetConfig, targetWriteClass);
        assertInstanceOf(SingleObjectXmlWriter.class, writer);

        List<Object> processedItems = new ArrayList<>();
        ExecutionContext readerContext = new ExecutionContext();
        if (reader instanceof ItemStream itemStreamReader) {
            itemStreamReader.open(readerContext);
        }
        try {
            Object sourceItem;
            while ((sourceItem = reader.read()) != null) {
                Object processed = processor.process(sourceItem);
                if (processed != null) {
                    processedItems.add(processed);
                }
            }
        } finally {
            if (reader instanceof ItemStream itemStreamReader) {
                itemStreamReader.close();
            }
        }

        assertEquals(3, sourceConfig.getRecordCount());
        assertEquals(3, processedItems.size());
        assertEquals(targetProcessingClass, processedItems.get(0).getClass());

        Object firstRecord = processedItems.get(0);
        assertEquals(1, firstRecord.getClass().getMethod("getId").invoke(firstRecord));
        assertEquals("John Doe", firstRecord.getClass().getMethod("getName").invoke(firstRecord));
        assertEquals("john.doe@example.com", firstRecord.getClass().getMethod("getEmail").invoke(firstRecord));

        Object wrapper = GeneratedModelClassResolver.createWrapper(metadata, processedItems);
        assertEquals(targetWriteClass, wrapper.getClass());
        Object wrapperRecords = wrapper.getClass().getMethod("getCustomersXmlRecord").invoke(wrapper);
        assertInstanceOf(List.class, wrapperRecords);
        assertEquals(3, ((List<?>) wrapperRecords).size());

        writer.write(new Chunk<>(List.of(wrapper)));

        String xml = Files.readString(Path.of(targetConfig.getFilePath()));
        assertTrue(xml.contains("<Customers>"));
        assertTrue(xml.contains("<Customer>"));
        assertTrue(xml.contains("<id>1</id>"));
        assertTrue(xml.contains("<name>John Doe</name>"));
        assertTrue(xml.contains("<email>john.doe@example.com</email>"));
        assertTrue(xml.contains("<name>Jane Smith</name>"));
        assertTrue(xml.contains("<name>Ravi Kumar</name>"));
    }

    private Path prepareScenarioBundle() throws Exception {
        Path sourceScenarioDir = Path.of("src", "main", "resources", "config-jobs", "customer-load");
        Path scenarioDir = tempDir.resolve("customer-load");
        copyDirectory(sourceScenarioDir, scenarioDir);

        Path inputFile = scenarioDir.resolve("input/customers.csv").toAbsolutePath().normalize();
        Path outputFile = scenarioDir.resolve("output/customers.xml").toAbsolutePath().normalize();
        Files.createDirectories(inputFile.getParent());
        Files.createDirectories(outputFile.getParent());
        Files.copy(Path.of("src", "main", "resources", "demo-input", "Customers.csv"), inputFile);

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

    private void applyDerivedPackages(JobConfig jobConfig,
                                      Path scenarioDir,
                                      SourceWrapper sourceWrapper,
                                      TargetWrapper targetWrapper) {
        String jobName = JobScopedPackageNameResolver.deriveJobName(jobConfig, scenarioDir);
        if (sourceWrapper.getSources() != null) {
            for (var source : sourceWrapper.getSources()) {
                if (source.getPackageName() == null || source.getPackageName().isBlank()) {
                    source.setPackageName(JobScopedPackageNameResolver.resolveSourcePackage(jobName));
                }
            }
        }
        if (targetWrapper.getTargets() != null) {
            List<TargetConfig> defaultedTargets = new ArrayList<>();
            for (var target : targetWrapper.getTargets()) {
                XmlTargetConfig targetConfig = (XmlTargetConfig) target;
                if (targetConfig.getPackageName() == null || targetConfig.getPackageName().isBlank()) {
                    defaultedTargets.add(new XmlTargetConfig(
                            targetConfig.getTargetName(),
                            JobScopedPackageNameResolver.resolveTargetPackage(jobName),
                            copyFields(targetConfig),
                            targetConfig.getFilePath(),
                            targetConfig.getRootElement(),
                            targetConfig.getRecordElement(),
                            targetConfig.getModelDefinitionPath()
                    ));
                } else {
                    defaultedTargets.add(targetConfig);
                }
            }
            targetWrapper.setTargets(defaultedTargets);
        }
    }

    private List<ColumnConfig> copyFields(XmlTargetConfig targetConfig) {
        if (targetConfig.getFields() == null) {
            return null;
        }
        return targetConfig.getFields().stream().map(field -> {
            ColumnConfig column = new ColumnConfig();
            column.setName(field.getName());
            column.setType(field.getType());
            return column;
        }).toList();
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

    @SuppressWarnings("unchecked")
    private ItemReader<Object> createReader(DynamicReaderFactory readerFactory,
                                            CsvSourceConfig sourceConfig,
                                            Class<?> sourceRecordClass) throws Exception {
        return readerFactory.createReader(sourceConfig, (Class<Object>) sourceRecordClass);
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
            assertEquals(Boolean.TRUE, success, "Generated CSV to flat XML flow proof sources must compile successfully.");
        }
    }

    private String toYamlPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }
}
