package com.etl.flow;

import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.config.target.XmlTargetConfig;
import com.etl.generation.xml.build.XmlJobScopedGenerationResult;
import com.etl.generation.xml.build.XmlJobScopedGenerationService;
import com.etl.processor.DynamicProcessorFactory;
import com.etl.processor.impl.DefaultDynamicProcessor;
import com.etl.reader.DynamicReaderFactory;
import com.etl.reader.impl.CsvDynamicReader;
import com.etl.reader.impl.XmlDynamicReader;
import com.etl.writer.DynamicWriterFactory;
import com.etl.writer.impl.CsvDynamicWriter;
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
import org.springframework.batch.item.file.FlatFileItemWriter;
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

class XmlNestedToCsvToNestedXmlFlowTest {

    @TempDir
    Path tempDir;

    @Test
    void runsTwoOrderedFlowsInsideOneScenario() throws Exception {
        Path scenarioDir = prepareScenarioBundle();
        Path generatedSourceRoot = tempDir.resolve("generated-roundtrip-sources");

        ObjectMapper mapper = yamlMapper();
        JobConfig jobConfig = mapper.readValue(scenarioDir.resolve("job-config.yaml").toFile(), JobConfig.class);
        SourceWrapper sourceWrapper = mapper.readValue(scenarioDir.resolve(jobConfig.getSourceConfigPath()).toFile(), SourceWrapper.class);
        TargetWrapper targetWrapper = mapper.readValue(scenarioDir.resolve(jobConfig.getTargetConfigPath()).toFile(), TargetWrapper.class);
        ProcessorConfig processorConfig = mapper.readValue(scenarioDir.resolve(jobConfig.getProcessorConfigPath()).toFile(), ProcessorConfig.class);

        XmlSourceConfig xmlSourceConfig = (XmlSourceConfig) sourceWrapper.getSources().stream()
                .filter(source -> "TagValidationSource".equals(source.getSourceName()))
                .findFirst()
                .orElseThrow();
        com.etl.config.source.CsvSourceConfig csvSourceConfig = (com.etl.config.source.CsvSourceConfig) sourceWrapper.getSources().stream()
                .filter(source -> "TagValidationCsvIntermediate".equals(source.getSourceName()))
                .findFirst()
                .orElseThrow();
        CsvTargetConfig csvTargetConfig = (CsvTargetConfig) targetWrapper.getTargets().stream()
                .filter(target -> "TagValidationCsvIntermediate".equals(target.getTargetName()))
                .findFirst()
                .orElseThrow();
        XmlTargetConfig xmlTargetConfig = (XmlTargetConfig) targetWrapper.getTargets().stream()
                .filter(target -> "TagValidationNestedXml".equals(target.getTargetName()))
                .findFirst()
                .orElseThrow();

        XmlJobScopedGenerationResult generationResult = new XmlJobScopedGenerationService()
                .generate(scenarioDir.resolve("job-config.yaml"), generatedSourceRoot);
        assertEquals(1, generationResult.sourceResults().size());
        assertEquals(2, generationResult.targetResults().size());

        compile(generationResult.allGeneratedFiles(), Path.of("target", "test-classes"));

        Class<?> xmlSourceRecordClass = Class.forName(xmlSourceConfig.getPackageName() + "." + xmlSourceConfig.getRecordElement());
        Class<?> csvTargetClass = Class.forName(csvTargetConfig.getPackageName() + "." + csvTargetConfig.getTargetName());
        Class<?> csvSourceRecordClass = Class.forName(csvSourceConfig.getPackageName() + "." + csvSourceConfig.getSourceName());
        Class<?> xmlTargetRecordClass = Class.forName(xmlTargetConfig.getPackageName() + "." + xmlTargetConfig.getRecordElement());
        assertNotNull(xmlSourceRecordClass);
        assertNotNull(csvTargetClass);
        assertNotNull(csvSourceRecordClass);
        assertNotNull(xmlTargetRecordClass);

        DynamicReaderFactory readerFactory = new DynamicReaderFactory(List.of(new CsvDynamicReader<>(), new XmlDynamicReader<>()));
        DynamicProcessorFactory processorFactory = new DynamicProcessorFactory(Map.of("default", new DefaultDynamicProcessor()));
        DynamicWriterFactory writerFactory = new DynamicWriterFactory(List.of(new CsvDynamicWriter(), new XmlDynamicWriter()));

        ResolvedModelMetadata firstStepMetadata = GeneratedModelClassResolver.resolveMetadata(xmlSourceConfig, csvTargetConfig);
        ItemReader<Object> firstStepReader = readerFactory.createReader(xmlSourceConfig, (Class<Object>) xmlSourceRecordClass);
        ItemProcessor<Object, Object> firstStepProcessor = processorFactory.getProcessor(processorConfig, xmlSourceConfig, csvTargetConfig, firstStepMetadata);
        ItemWriter<Object> firstStepWriter = writerFactory.createWriter(csvTargetConfig, csvTargetClass);

        List<Object> firstStepProcessedItems = new ArrayList<>();
        Object xmlItem;
        while ((xmlItem = firstStepReader.read()) != null) {
            Object processed = firstStepProcessor.process(xmlItem);
            if (processed != null) {
                firstStepProcessedItems.add(processed);
            }
        }
        assertEquals(1, firstStepProcessedItems.size());

        FlatFileItemWriter<Object> csvWriter = (FlatFileItemWriter<Object>) firstStepWriter;
        csvWriter.open(new ExecutionContext());
        try {
            csvWriter.write(new Chunk<>(firstStepProcessedItems));
        } finally {
            csvWriter.close();
        }

        String intermediateCsv = Files.readString(Path.of(csvTargetConfig.getFilePath()));
        assertTrue(intermediateCsv.contains("homeAgencyId,tagAgencyId,tagSerialNumber,plateNumber,plateCountry,plateState,accountNumber"));
        assertTrue(intermediateCsv.contains("0056,1300,0003518358,7064AFP,US,KS,4773316"));

        ResolvedModelMetadata secondStepMetadata = GeneratedModelClassResolver.resolveMetadata(csvSourceConfig, xmlTargetConfig);
        ItemReader<Object> secondStepReader = readerFactory.createReader(csvSourceConfig, (Class<Object>) csvSourceRecordClass);
        ItemProcessor<Object, Object> secondStepProcessor = processorFactory.getProcessor(processorConfig, csvSourceConfig, xmlTargetConfig, secondStepMetadata);
        ItemWriter<Object> secondStepWriter = writerFactory.createWriter(xmlTargetConfig, xmlTargetRecordClass);

        List<Object> secondStepProcessedItems = new ArrayList<>();
        ExecutionContext readerContext = new ExecutionContext();
        if (secondStepReader instanceof org.springframework.batch.item.ItemStream itemStreamReader) {
            itemStreamReader.open(readerContext);
        }
        try {
            Object csvItem;
            while ((csvItem = secondStepReader.read()) != null) {
                Object processed = secondStepProcessor.process(csvItem);
                if (processed != null) {
                    secondStepProcessedItems.add(processed);
                }
            }
        } finally {
            if (secondStepReader instanceof org.springframework.batch.item.ItemStream itemStreamReader) {
                itemStreamReader.close();
            }
        }

        assertEquals(1, secondStepProcessedItems.size());
        Object roundTripRecord = secondStepProcessedItems.get(0);
        assertEquals("0056", roundTripRecord.getClass().getMethod("getHomeAgencyId").invoke(roundTripRecord));
        assertEquals("1300", roundTripRecord.getClass().getMethod("getTagAgencyId").invoke(roundTripRecord));
        assertEquals("0003518358", roundTripRecord.getClass().getMethod("getTagSerialNumber").invoke(roundTripRecord));
        Object plateDetails = roundTripRecord.getClass().getMethod("getPlateDetails").invoke(roundTripRecord);
        assertNotNull(plateDetails);
        assertEquals("7064AFP", plateDetails.getClass().getMethod("getPlateNumber").invoke(plateDetails));
        assertEquals("US", plateDetails.getClass().getMethod("getPlateCountry").invoke(plateDetails));
        assertEquals("KS", plateDetails.getClass().getMethod("getPlateState").invoke(plateDetails));
        Object accountDetails = roundTripRecord.getClass().getMethod("getAccountDetails").invoke(roundTripRecord);
        assertNotNull(accountDetails);
        assertEquals("4773316", accountDetails.getClass().getMethod("getAccountNumber").invoke(accountDetails));

        StaxEventItemWriter<Object> xmlWriter = (StaxEventItemWriter<Object>) secondStepWriter;
        xmlWriter.open(new ExecutionContext());
        try {
            xmlWriter.write(new Chunk<>(secondStepProcessedItems));
        } finally {
            xmlWriter.close();
        }

        String finalXml = Files.readString(Path.of(xmlTargetConfig.getFilePath()));
        assertTrue(finalXml.contains("<TagValidationExports>"));
        assertTrue(finalXml.contains("<TagValidationExport>"));
        assertTrue(finalXml.contains("<homeAgencyId>0056</homeAgencyId>"));
        assertTrue(finalXml.contains("<tagAgencyId>1300</tagAgencyId>"));
        assertTrue(finalXml.contains("<tagSerialNumber>0003518358</tagSerialNumber>"));
        assertTrue(finalXml.contains("<plateDetails>"));
        assertTrue(finalXml.contains("<plateNumber>7064AFP</plateNumber>"));
        assertTrue(finalXml.contains("<plateCountry>US</plateCountry>"));
        assertTrue(finalXml.contains("<plateState>KS</plateState>"));
        assertTrue(finalXml.contains("<accountDetails>"));
        assertTrue(finalXml.contains("<accountNumber>4773316</accountNumber>"));
    }

    private Path prepareScenarioBundle() throws Exception {
        Path sourceScenarioDir = Path.of("src", "main", "resources", "config-scenarios", "xml-nested-to-csv-to-nested-xml");
        Path scenarioDir = tempDir.resolve("xml-nested-to-csv-to-nested-xml");
        copyDirectory(sourceScenarioDir, scenarioDir);

        Path inputFile = scenarioDir.resolve("input/nested-sample.xml").toAbsolutePath().normalize();
        Path intermediateCsv = scenarioDir.resolve("target/intermediate/tag-validation-intermediate.csv").toAbsolutePath().normalize();
        Path outputFile = scenarioDir.resolve("target/tag-validation-roundtrip.xml").toAbsolutePath().normalize();
        Files.createDirectories(intermediateCsv.getParent());
        Files.createDirectories(outputFile.getParent());

        Files.writeString(
                scenarioDir.resolve("source-config.yaml"),
                Files.readString(scenarioDir.resolve("source-config.yaml"))
                        .replace("filePath: input/nested-sample.xml", "filePath: " + toYamlPath(inputFile))
                        .replace("filePath: target/intermediate/tag-validation-intermediate.csv", "filePath: " + toYamlPath(intermediateCsv))
        );
        Files.writeString(
                scenarioDir.resolve("target-config.yaml"),
                Files.readString(scenarioDir.resolve("target-config.yaml"))
                        .replace("filePath: target/intermediate/tag-validation-intermediate.csv", "filePath: " + toYamlPath(intermediateCsv))
                        .replace("filePath: target/tag-validation-roundtrip.xml", "filePath: " + toYamlPath(outputFile))
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
            assertEquals(Boolean.TRUE, success, "Generated multi-step roundtrip flow proof sources must compile successfully.");
        }
    }

    private String toYamlPath(Path path) {
        return path.toAbsolutePath().normalize().toString().replace("\\", "/");
    }
}

