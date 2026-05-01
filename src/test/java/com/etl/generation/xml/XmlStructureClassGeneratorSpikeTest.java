package com.etl.generation.xml;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
class XmlStructureClassGeneratorSpikeTest {
    @TempDir
    Path tempDir;
    @Test
    void generatesCompilableSimpleAndNestedXmlModelsFromConfig() throws Exception {
        Path sourceRoot = tempDir.resolve("generated-sources");
        Path classRoot = tempDir.resolve("generated-classes");
        XmlModelDefinitionLoader loader = new XmlModelDefinitionLoader();
        XmlStructureClassGenerator generator = new XmlStructureClassGenerator();
        List<XmlModelGenerationResult> results = List.of(
                generator.generate(loader.load(scenarioPath("simple-source-model.yaml")), sourceRoot),
                generator.generate(loader.load(scenarioPath("simple-target-model.yaml")), sourceRoot),
                generator.generate(loader.load(scenarioPath("nested-source-model.yaml")), sourceRoot),
                generator.generate(loader.load(scenarioPath("nested-target-model.yaml")), sourceRoot)
        );
        List<Path> generatedFiles = new ArrayList<>();
        for (XmlModelGenerationResult result : results) {
            generatedFiles.addAll(result.generatedFiles());
            assertTrue(result.generatedFiles().stream().allMatch(java.nio.file.Files::exists));
        }
        compile(generatedFiles, classRoot);
        try (URLClassLoader classLoader = new URLClassLoader(new URL[]{classRoot.toUri().toURL()}, Thread.currentThread().getContextClassLoader())) {
            assertSimpleUnmarshalWorks(classLoader);
            assertNestedUnmarshalWorks(classLoader);
            assertNotNull(classLoader.loadClass("com.etl.generated.job.xmlmodelspike.simple.target.Customer"));
            assertNotNull(classLoader.loadClass("com.etl.generated.job.xmlmodelspike.simple.target.Customers"));
            assertNotNull(classLoader.loadClass("com.etl.generated.job.xmlmodelspike.nested.target.TVLTagDetails"));
            assertNotNull(classLoader.loadClass("com.etl.generated.job.xmlmodelspike.nested.target.TagValidationList"));
        }
    }
    private void assertSimpleUnmarshalWorks(ClassLoader classLoader) throws Exception {
        Class<?> wrapperClass = classLoader.loadClass("com.etl.generated.job.xmlmodelspike.simple.source.Customers");
        JAXBContext context = JAXBContext.newInstance(wrapperClass);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        Object wrapper = unmarshaller.unmarshal(scenarioPath("simple-sample.xml").toFile());
        Object records = wrapperClass.getMethod("getCustomer").invoke(wrapper);
        assertNotNull(records);
        Object record = ((List<?>) records).get(0);
        assertEquals(1001, wrapperClass.getClassLoader().loadClass("com.etl.generated.job.xmlmodelspike.simple.source.Customer")
                .getMethod("getId").invoke(record));
        assertEquals("Alice", record.getClass().getMethod("getName").invoke(record));
        assertEquals("alice@example.com", record.getClass().getMethod("getEmail").invoke(record));
    }
    private void assertNestedUnmarshalWorks(ClassLoader classLoader) throws Exception {
        Class<?> wrapperClass = classLoader.loadClass("com.etl.generated.job.xmlmodelspike.nested.source.TagValidationList");
        JAXBContext context = JAXBContext.newInstance(wrapperClass);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        Object wrapper = unmarshaller.unmarshal(scenarioPath("nested-sample.xml").toFile());
        Object records = wrapperClass.getMethod("getTVLTagDetails").invoke(wrapper);
        assertNotNull(records);
        Object record = ((List<?>) records).get(0);
        assertEquals("0056", record.getClass().getMethod("getHomeAgencyID").invoke(record));
        Object plateDetails = record.getClass().getMethod("getTVLPlateDetails").invoke(record);
        assertNotNull(plateDetails);
        assertEquals("US", plateDetails.getClass().getMethod("getPlateCountry").invoke(plateDetails));
        assertEquals("KS", plateDetails.getClass().getMethod("getPlateState").invoke(plateDetails));
        Object accountDetails = record.getClass().getMethod("getTVLAccountDetails").invoke(record);
        assertNotNull(accountDetails);
        assertEquals("4773316", accountDetails.getClass().getMethod("getAccountNumber").invoke(accountDetails));
        assertEquals("N", accountDetails.getClass().getMethod("getFleetIndicator").invoke(accountDetails));
    }
    private void compile(List<Path> javaFiles, Path classRoot) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "A JDK is required to compile generated sources during the spike test.");
        java.nio.file.Files.createDirectories(classRoot);
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
            assertTrue(Boolean.TRUE.equals(success), "Generated XML spike sources must compile successfully.");
        }
    }
    private Path scenarioPath(String fileName) {
        return Path.of("src", "main", "resources", "config-scenarios", "xml-model-spike", fileName);
    }
}
