package com.etl.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelPathConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesRelativeModelPathsAgainstDetectedProjectRoot() {
        ModelPathConfig config = new ModelPathConfig();
        config.setSourceDir("src/main/java/com/etl/model/source");
        config.setTargetDir("src/main/java/com/etl/model/target");
        config.setSourceClassDir("target/classes/com/etl/model/source");
        config.setTargetClassDir("target/classes/com/etl/model/target");

        Path projectRoot = ModelPathConfig.detectProjectRoot(Path.of(""));

        assertEquals(projectRoot.resolve("src/main/java/com/etl/model/source").normalize().toString(), config.getSourceDir());
        assertEquals(projectRoot.resolve("src/main/java/com/etl/model/target").normalize().toString(), config.getTargetDir());
        assertEquals(projectRoot.resolve("target/classes/com/etl/model/source").normalize().toString(), config.getSourceClassDir());
        assertEquals(projectRoot.resolve("target/classes/com/etl/model/target").normalize().toString(), config.getTargetClassDir());
    }

    @Test
    void preservesAbsoluteModelPaths() {
        Path absoluteSourceDir = tempDir.resolve("source-root").toAbsolutePath().normalize();
        Path absoluteTargetClassDir = tempDir.resolve("compiled-target-root").toAbsolutePath().normalize();

        ModelPathConfig config = new ModelPathConfig();
        config.setSourceDir(absoluteSourceDir.toString());
        config.setTargetClassDir(absoluteTargetClassDir.toString());

        assertEquals(absoluteSourceDir.toString(), config.getSourceDir());
        assertEquals(absoluteTargetClassDir.toString(), config.getTargetClassDir());
    }

    @Test
    void detectsProjectRootWhenStartingFromScenarioFolder() {
        Path scenarioDirectory = Path.of("src", "main", "resources", "config-scenarios", "xml-nested-to-csv-tag-validation");
        Path projectRoot = ModelPathConfig.detectProjectRoot(Path.of(""));

        assertEquals(projectRoot, ModelPathConfig.detectProjectRoot(scenarioDirectory));
    }
}

