package com.etl.testsupport;

import com.etl.generation.xml.build.XmlJobScopedGenerationResult;
import com.etl.generation.xml.build.XmlJobScopedGenerationService;
import org.junit.jupiter.api.Assertions;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class GeneratedScenarioModelSupport {

    private GeneratedScenarioModelSupport() {
    }

    public static CompiledGeneratedModels compileJobScopedModels(Path jobConfigPath, Path tempDir) throws Exception {
        Path generationRoot = tempDir.resolve("generated-job-sources");
        Path classRoot = tempDir.resolve("generated-job-classes");
        XmlJobScopedGenerationResult generationResult = new XmlJobScopedGenerationService()
                .generate(jobConfigPath, generationRoot);
        compile(generationResult.allGeneratedFiles(), classRoot);
        URLClassLoader classLoader = new URLClassLoader(
                new URL[]{classRoot.toUri().toURL()},
                Thread.currentThread().getContextClassLoader()
        );
        return new CompiledGeneratedModels(classLoader);
    }

    private static void compile(List<Path> javaFiles, Path classRoot) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assertions.assertNotNull(compiler, "A JDK is required to compile generated sources during tests.");
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
            Assertions.assertEquals(Boolean.TRUE, success, "Generated scenario sources must compile successfully.");
        }
    }

    public record CompiledGeneratedModels(URLClassLoader classLoader) implements AutoCloseable {
        public Class<?> loadClass(String fullyQualifiedClassName) throws ClassNotFoundException {
            return classLoader.loadClass(fullyQualifiedClassName);
        }

        @Override
        public void close() throws Exception {
            classLoader.close();
        }
    }
}

