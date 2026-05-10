package com.etl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@ConfigurationProperties(prefix = "model.paths")
public class ModelPathConfig {
    private String sourceDir;
    private String targetDir;
    private String sourceClassDir;
    private String targetClassDir;

    public String getSourceDir() {
        return resolveConfiguredPath(sourceDir, detectProjectRoot(Path.of(""))).toString();
    }
    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }
    public String getTargetDir() {
        return resolveConfiguredPath(targetDir, detectProjectRoot(Path.of(""))).toString();
    }
    public void setTargetDir(String targetDir) {
        this.targetDir = targetDir;
    }
    public String getSourceClassDir() {
        return resolveConfiguredPath(sourceClassDir, detectProjectRoot(Path.of(""))).toString();
    }
    public void setSourceClassDir(String sourceClassDir) {
        this.sourceClassDir = sourceClassDir;
    }
    public String getTargetClassDir() {
        return resolveConfiguredPath(targetClassDir, detectProjectRoot(Path.of(""))).toString();
    }
    public void setTargetClassDir(String targetClassDir) {
        this.targetClassDir = targetClassDir;
    }

    static Path resolveConfiguredPath(String configuredPath, Path projectRoot) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return projectRoot.toAbsolutePath().normalize();
        }
        Path configured = Path.of(configuredPath.trim()).normalize();
        return configured.isAbsolute() ? configured : projectRoot.toAbsolutePath().normalize().resolve(configured).normalize();
    }

    static Path detectProjectRoot(Path startDirectory) {
        Path current = startDirectory.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        return startDirectory.toAbsolutePath().normalize();
    }
}

