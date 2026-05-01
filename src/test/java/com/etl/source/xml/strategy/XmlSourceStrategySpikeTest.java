package com.etl.source.xml.strategy;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.source.XmlSourceConfig;
import com.etl.generation.xml.XmlModelDefinitionLoader;
import com.etl.generation.xml.XmlModelGenerationResult;
import com.etl.generation.xml.XmlStructureClassGenerator;
import com.etl.source.xml.runtime.XmlFlatteningResult;
import com.etl.source.xml.runtime.XmlSourceRuntimeContext;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ItemReader;
import org.springframework.context.support.StaticApplicationContext;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class XmlSourceStrategySpikeTest {

    @TempDir
    Path tempDir;

    @Test
    void directStrategyFlattensSimpleGeneratedXml() throws Exception {
        ScenarioArtifacts artifacts = generateAndLoad("simple-source-model.yaml");
        Object wrapper = unmarshal(artifacts.classLoader(), artifacts.rootClass(), scenarioPath("simple-sample.xml"));

        XmlSourceConfig config = xmlConfig(
                artifacts.result().packageName(),
                scenarioPath("simple-sample.xml"),
                artifacts.result().rootClassName(),
                artifacts.result().recordClassName(),
                XmlFlatteningStrategyNames.DIRECT_XML
        );

        DirectXmlSourceStrategy strategy = new DirectXmlSourceStrategy();
        XmlFlatteningResult result = strategy.flatten(context(config, artifacts.rootClass(), artifacts.recordClass(), Map.of()), wrapper);

        assertEquals(1, result.getRecordCount());
        Map<String, Object> row = result.getRows().get(0);
        assertEquals(1001, row.get("id"));
        assertEquals("Alice", row.get("name"));
        assertEquals("alice@example.com", row.get("email"));
    }

    @Test
    void nestedStrategyFlattensNestedGeneratedXmlWithAutoAndMappedModes() throws Exception {
        ScenarioArtifacts artifacts = generateAndLoad("nested-source-model.yaml");
        Object wrapper = unmarshal(artifacts.classLoader(), artifacts.rootClass(), scenarioPath("nested-sample.xml"));

        XmlSourceConfig config = xmlConfig(
                artifacts.result().packageName(),
                scenarioPath("nested-sample.xml"),
                artifacts.result().rootClassName(),
                artifacts.result().recordClassName(),
                XmlFlatteningStrategyNames.NESTED_XML
        );

        NestedXmlSourceStrategy strategy = new NestedXmlSourceStrategy();

        XmlFlatteningResult autoResult = strategy.flatten(context(config, artifacts.rootClass(), artifacts.recordClass(), Map.of()), wrapper);
        assertEquals(1, autoResult.getRecordCount());
        Map<String, Object> autoRow = autoResult.getRows().get(0);
        assertEquals("0056", autoRow.get("HomeAgencyID"));
        assertEquals("1300", autoRow.get("TagAgencyID"));
        assertEquals("US", autoRow.get("TVLPlateDetails.PlateCountry"));
        assertEquals("KS", autoRow.get("TVLPlateDetails.PlateState"));
        assertEquals("4773316", autoRow.get("TVLAccountDetails.AccountNumber"));

        Map<String, String> fieldMappings = new LinkedHashMap<>();
        fieldMappings.put("homeAgencyId", "HomeAgencyID");
        fieldMappings.put("plateCountry", "TVLPlateDetails.PlateCountry");
        fieldMappings.put("accountNumber", "TVLAccountDetails.AccountNumber");

        XmlFlatteningResult mappedResult = strategy.flatten(context(config, artifacts.rootClass(), artifacts.recordClass(), fieldMappings), wrapper);
        assertEquals(1, mappedResult.getRecordCount());
        Map<String, Object> mappedRow = mappedResult.getRows().get(0);
        assertEquals("0056", mappedRow.get("homeAgencyId"));
        assertEquals("US", mappedRow.get("plateCountry"));
        assertEquals("4773316", mappedRow.get("accountNumber"));
    }

    @Test
    void nestedStrategyExtractsAllNestedRecordInstancesBelowWrapperNodes() {
        XmlSourceConfig config = xmlConfig(
                "ignored.package",
                Path.of("ignored.xml"),
                "TagValidationList",
                "TVLTagDetails",
                XmlFlatteningStrategyNames.NESTED_XML
        );

        TVLTagDetailsRecord firstRecord = new TVLTagDetailsRecord(
                "0056",
                "1300",
                new TVLPlateDetailsRecord("US", "KS"),
                new TVLAccountDetailsRecord("4773316")
        );
        TVLTagDetailsRecord secondRecord = new TVLTagDetailsRecord(
                "0041",
                "1112",
                new TVLPlateDetailsRecord("US", "TX"),
                new TVLAccountDetailsRecord("2025753547")
        );
        TagValidationListWrapper wrapper = new TagValidationListWrapper(
                List.of(new TVLDetailWrapper(List.of(firstRecord, secondRecord)))
        );

        Map<String, String> fieldMappings = new LinkedHashMap<>();
        fieldMappings.put("homeAgencyId", "HomeAgencyID");
        fieldMappings.put("tagAgencyId", "TagAgencyID");
        fieldMappings.put("plateState", "TVLPlateDetails.PlateState");
        fieldMappings.put("accountNumber", "TVLAccountDetails.AccountNumber");

        NestedXmlSourceStrategy strategy = new NestedXmlSourceStrategy();
        XmlFlatteningResult result = strategy.flatten(
                context(config, TagValidationListWrapper.class, TVLTagDetailsRecord.class, fieldMappings),
                wrapper
        );

        assertEquals(2, result.getRecordCount());
        assertEquals("0056", result.getRows().get(0).get("homeAgencyId"));
        assertEquals("1300", result.getRows().get(0).get("tagAgencyId"));
        assertEquals("KS", result.getRows().get(0).get("plateState"));
        assertEquals("4773316", result.getRows().get(0).get("accountNumber"));
        assertEquals("0041", result.getRows().get(1).get("homeAgencyId"));
        assertEquals("1112", result.getRows().get(1).get("tagAgencyId"));
        assertEquals("TX", result.getRows().get(1).get("plateState"));
        assertEquals("2025753547", result.getRows().get(1).get("accountNumber"));
    }

    @Test
    void selectorResolvesRegisteredAndJobSpecificStrategies() {
        DirectXmlSourceStrategy directStrategy = new DirectXmlSourceStrategy();
        NestedXmlSourceStrategy nestedStrategy = new NestedXmlSourceStrategy();
        TestJobSpecificXmlSourceStrategy customStrategy = new TestJobSpecificXmlSourceStrategy();

        StaticApplicationContext applicationContext = new StaticApplicationContext();
        applicationContext.getBeanFactory().registerSingleton("jobSpecificNestedStrategy", customStrategy);

        XmlSourceStrategyRegistry registry = new XmlSourceStrategyRegistry(List.of(directStrategy, nestedStrategy));
        XmlSourceStrategySelector selector = new XmlSourceStrategySelector(
                registry,
                new JobSpecificXmlStrategyResolver(applicationContext)
        );

        XmlSourceRuntimeContext nestedContext = XmlSourceRuntimeContext.builder()
                .sourceName("tagValidationXmlSource")
                .flatteningStrategy(XmlFlatteningStrategyNames.NESTED_XML)
                .build();
        assertSame(nestedStrategy, selector.select(nestedContext));

        XmlSourceRuntimeContext jobSpecificContext = XmlSourceRuntimeContext.builder()
                .jobName("invoice-reconciliation")
                .sourceName("invoiceXmlSource")
                .flatteningStrategy(XmlFlatteningStrategyNames.JOB_SPECIFIC_XML)
                .jobSpecificStrategyBean("jobSpecificNestedStrategy")
                .build();
        assertSame(customStrategy, selector.select(jobSpecificContext));

        XmlFlatteningResult customResult = customStrategy.flatten(jobSpecificContext, new Object());
        assertEquals("invoice-reconciliation", customResult.getRows().get(0).get("job"));
        assertEquals("invoiceXmlSource", customResult.getRows().get(0).get("source"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void xmlDynamicReaderUsesNestedStrategyToEmitFlattenedRows() throws Exception {
        ScenarioArtifacts artifacts = generateAndLoad("nested-source-model.yaml");

        XmlSourceConfig config = xmlConfig(
                artifacts.result().packageName(),
                scenarioPath("nested-sample.xml"),
                artifacts.result().rootClassName(),
                artifacts.result().recordClassName(),
                XmlFlatteningStrategyNames.NESTED_XML
        );

        XmlSourceStrategySelector selector = new XmlSourceStrategySelector(
                new XmlSourceStrategyRegistry(List.of(new DirectXmlSourceStrategy(), new NestedXmlSourceStrategy())),
                new JobSpecificXmlStrategyResolver(new StaticApplicationContext())
        );

        com.etl.reader.impl.XmlDynamicReader<Object> reader = new com.etl.reader.impl.XmlDynamicReader<>(selector);
        ItemReader<Map<String, Object>> flatteningReader = (ItemReader<Map<String, Object>>) (ItemReader)
                reader.getReader(config, (Class<Object>) artifacts.recordClass());

        Map<String, Object> row = flatteningReader.read();
        assertNotNull(row);
        assertEquals("0056", row.get("HomeAgencyID"));
        assertEquals("US", row.get("TVLPlateDetails.PlateCountry"));
        assertEquals("4773316", row.get("TVLAccountDetails.AccountNumber"));
        assertEquals("US", ReflectionUtils.getFieldValue(row, "TVLPlateDetails.PlateCountry"));
        assertNull(flatteningReader.read());
    }

    private XmlSourceRuntimeContext context(XmlSourceConfig config,
                                            Class<?> rootClass,
                                            Class<?> recordClass,
                                            Map<String, String> fieldMappings) {
        return XmlSourceRuntimeContext.builder()
                .jobName("xml-model-spike-job")
                .sourceName(config.getSourceName())
                .xmlSourceConfig(config)
                .rootClass(rootClass)
                .recordClass(recordClass)
                .fieldMappings(fieldMappings)
                .build();
    }

    private XmlSourceConfig xmlConfig(String packageName,
                                      Path filePath,
                                      String rootElement,
                                      String recordElement,
                                      String flatteningStrategy) {
        XmlSourceConfig config = new XmlSourceConfig();
        config.setSourceName("xmlModelSpikeSource");
        config.setPackageName(packageName);
        config.setFilePath(filePath.toString());
        config.setRootElement(rootElement);
        config.setRecordElement(recordElement);
        config.setFlatteningStrategy(flatteningStrategy);
        return config;
    }

    private ScenarioArtifacts generateAndLoad(String modelFile) throws Exception {
        Path sourceRoot = tempDir.resolve(modelFile.replace('.', '-')).resolve("generated-sources");
        Path classRoot = tempDir.resolve(modelFile.replace('.', '-')).resolve("generated-classes");

        XmlModelDefinitionLoader loader = new XmlModelDefinitionLoader();
        XmlStructureClassGenerator generator = new XmlStructureClassGenerator();
        XmlModelGenerationResult result = generator.generate(loader.load(scenarioPath(modelFile)), sourceRoot);

        compile(result.generatedFiles(), classRoot);
        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{classRoot.toUri().toURL()},
                Thread.currentThread().getContextClassLoader()
        );
        Class<?> rootClass = classLoader.loadClass(result.packageName() + "." + result.rootClassName());
        Class<?> recordClass = classLoader.loadClass(result.packageName() + "." + result.recordClassName());
        return new ScenarioArtifacts(result, classLoader, rootClass, recordClass);
    }

    private Object unmarshal(ClassLoader classLoader, Class<?> wrapperClass, Path xmlPath) throws Exception {
        JAXBContext context = JAXBContext.newInstance(wrapperClass);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        Thread currentThread = Thread.currentThread();
        ClassLoader previous = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            return unmarshaller.unmarshal(xmlPath.toFile());
        } finally {
            currentThread.setContextClassLoader(previous);
        }
    }

    private void compile(List<Path> javaFiles, Path classRoot) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A JDK is required to compile generated sources during the spike test.");
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
            assertEquals(Boolean.TRUE, success, "Generated XML strategy test sources must compile successfully.");
        }
    }

    private Path scenarioPath(String fileName) {
        return Path.of("src", "main", "resources", "config-scenarios", "xml-model-spike", fileName);
    }

    private record ScenarioArtifacts(
            XmlModelGenerationResult result,
            URLClassLoader classLoader,
            Class<?> rootClass,
            Class<?> recordClass
    ) {
    }

    private static final class TestJobSpecificXmlSourceStrategy extends JobSpecificXmlSourceStrategy {

        @Override
        public String getStrategyName() {
            return "TestJobSpecificXml";
        }

        @Override
        public XmlFlatteningResult flatten(XmlSourceRuntimeContext context, Object xmlRoot) {
            return XmlFlatteningResult.ofRows(List.of(Map.of(
                    "job", context.getJobName(),
                    "source", context.getSourceName()
            )));
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    private static final class TagValidationListWrapper {

        @XmlElement(name = "TVLDetail")
        private List<TVLDetailWrapper> details;

        private TagValidationListWrapper(List<TVLDetailWrapper> details) {
            this.details = details;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    private static final class TVLDetailWrapper {

        @XmlElement(name = "TVLTagDetails")
        private List<TVLTagDetailsRecord> tagDetails;

        private TVLDetailWrapper(List<TVLTagDetailsRecord> tagDetails) {
            this.tagDetails = tagDetails;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    private static final class TVLTagDetailsRecord {

        @XmlElement(name = "HomeAgencyID")
        private String homeAgencyID;

        @XmlElement(name = "TagAgencyID")
        private String tagAgencyID;

        @XmlElement(name = "TVLPlateDetails")
        private TVLPlateDetailsRecord plateDetails;

        @XmlElement(name = "TVLAccountDetails")
        private TVLAccountDetailsRecord accountDetails;

        private TVLTagDetailsRecord(String homeAgencyID,
                                    String tagAgencyID,
                                    TVLPlateDetailsRecord plateDetails,
                                    TVLAccountDetailsRecord accountDetails) {
            this.homeAgencyID = homeAgencyID;
            this.tagAgencyID = tagAgencyID;
            this.plateDetails = plateDetails;
            this.accountDetails = accountDetails;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    private static final class TVLPlateDetailsRecord {

        @XmlElement(name = "PlateCountry")
        private String plateCountry;

        @XmlElement(name = "PlateState")
        private String plateState;

        private TVLPlateDetailsRecord(String plateCountry, String plateState) {
            this.plateCountry = plateCountry;
            this.plateState = plateState;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    private static final class TVLAccountDetailsRecord {

        @XmlElement(name = "AccountNumber")
        private String accountNumber;

        private TVLAccountDetailsRecord(String accountNumber) {
            this.accountNumber = accountNumber;
        }
    }
}








