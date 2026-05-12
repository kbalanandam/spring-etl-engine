package com.etl.flow;

import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.JobScopedPackageNameResolver;
import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetWrapper;
import com.etl.config.target.XmlTargetConfig;
import com.etl.generation.xml.build.XmlJobScopedGenerationResult;
import com.etl.generation.xml.build.XmlJobScopedGenerationService;
import com.etl.processor.DynamicProcessorFactory;
import com.etl.processor.impl.DefaultDynamicProcessor;
import com.etl.reader.DynamicReaderFactory;
import com.etl.reader.impl.CsvDynamicReader;
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
import org.springframework.batch.item.ItemStream;
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
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvSourceToNestedXmlTargetFlowTest {

    @TempDir
    Path tempDir;

    @Test
    void runsCsvSourceThroughSharedProcessorIntoNestedXmlTarget() throws Exception {
        Path scenarioDir = prepareScenarioBundle();
        Path generatedSourceRoot = tempDir.resolve("generated-flow-sources");

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

        compile(generationResult.allGeneratedFiles(), Path.of("target", "test-classes"));

        Class<?> sourceRecordClass = Class.forName(sourceConfig.getPackageName() + "." + sourceConfig.getSourceName());
        Class<?> targetRecordClass = Class.forName(targetConfig.getPackageName() + "." + targetConfig.getRecordElement());
        assertNotNull(sourceRecordClass);
        assertNotNull(targetRecordClass);

        DynamicReaderFactory readerFactory = new DynamicReaderFactory(List.of(new CsvDynamicReader<>()));
        DynamicProcessorFactory processorFactory = new DynamicProcessorFactory(Map.of("default", new DefaultDynamicProcessor()));
        DynamicWriterFactory writerFactory = new DynamicWriterFactory(List.of(new XmlDynamicWriter()));

        ResolvedModelMetadata metadata = GeneratedModelClassResolver.resolveMetadata(sourceConfig, targetConfig);
        ItemReader<Object> reader = readerFactory.createReader(sourceConfig, (Class<Object>) sourceRecordClass);
        ItemProcessor<Object, Object> processor = processorFactory.getProcessor(processorConfig, sourceConfig, targetConfig, metadata);
        ItemWriter<Object> writer = writerFactory.createWriter(targetConfig, targetRecordClass);

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

        assertEquals(1, processedItems.size());

        Object processedRecord = processedItems.get(0);
        assertEquals("1001", processedRecord.getClass().getMethod("getId").invoke(processedRecord));
        Object profile = processedRecord.getClass().getMethod("getProfile").invoke(processedRecord);
        assertNotNull(profile);
        assertEquals("alice@example.com", profile.getClass().getMethod("getEmail").invoke(profile));
        Object address = processedRecord.getClass().getMethod("getAddress").invoke(processedRecord);
        assertNotNull(address);
        assertEquals("Chennai", address.getClass().getMethod("getCity").invoke(address));
        assertEquals("IN", address.getClass().getMethod("getCountry").invoke(address));

        StaxEventItemWriter<Object> xmlWriter = (StaxEventItemWriter<Object>) writer;
        xmlWriter.open(new ExecutionContext());
        try {
            xmlWriter.write(new Chunk<>(processedItems));
        } finally {
            xmlWriter.close();
        }

        String xml = Files.readString(Path.of(targetConfig.getFilePath()));
        assertTrue(xml.contains("<Customers>"));
        assertTrue(xml.contains("<CustomerRecord>"));
        assertTrue(xml.contains("<id>1001</id>"));
        assertTrue(xml.contains("<profile>"));
        assertTrue(xml.contains("<email>alice@example.com</email>"));
        assertTrue(xml.contains("<address>"));
        assertTrue(xml.contains("<city>Chennai</city>"));
        assertTrue(xml.contains("<country>IN</country>"));
    }

    private Path prepareScenarioBundle() throws Exception {
        Path sourceScenarioDir = Path.of("src", "main", "resources", "config-jobs", "csv-to-nested-xml");
        Path scenarioDir = tempDir.resolve("csv-to-nested-xml");
        copyDirectory(sourceScenarioDir, scenarioDir);

        Path inputFile = scenarioDir.resolve("input/customers.csv").toAbsolutePath().normalize();
        Path outputFile = scenarioDir.resolve("output/customers-nested.xml").toAbsolutePath().normalize();
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

  private void applyDerivedPackages(JobConfig jobConfig,
                                    Path scenarioDir,
                                    SourceWrapper sourceWrapper,
                                    TargetWrapper targetWrapper) {
    String jobName = JobScopedPackageNameResolver.deriveJobName(jobConfig, scenarioDir);
    if (sourceWrapper.getSources() != null) {
      for (var sourceConfig : sourceWrapper.getSources()) {
        if (sourceConfig.getPackageName() == null || sourceConfig.getPackageName().isBlank()) {
          sourceConfig.setPackageName(JobScopedPackageNameResolver.resolveSourcePackage(jobName));
        }
      }
    }
    if (targetWrapper.getTargets() != null) {
      java.util.List<com.etl.config.target.TargetConfig> defaultedTargets = new java.util.ArrayList<>();
      for (var target : targetWrapper.getTargets()) {
        XmlTargetConfig targetConfig = (XmlTargetConfig) target;
        if (targetConfig.getPackageName() == null || targetConfig.getPackageName().isBlank()) {
          defaultedTargets.add(new XmlTargetConfig(
              targetConfig.getTargetName(),
              JobScopedPackageNameResolver.resolveTargetPackage(jobName),
              targetConfig.getFields().stream().map(field -> {
                com.etl.config.ColumnConfig column = new com.etl.config.ColumnConfig();
                column.setName(field.getName());
                column.setType(field.getType());
                return column;
              }).toList(),
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
            assertEquals(Boolean.TRUE, success, "Generated CSV to nested XML flow proof sources must compile successfully.");
        }
    }

    private String toYamlPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }

}





