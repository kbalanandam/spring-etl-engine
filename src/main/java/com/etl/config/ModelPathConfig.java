package com.etl.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "model.paths")
public class ModelPathConfig {
    private String sourceDir;
    private String targetDir;
    private String sourceClassDir;
    private String targetClassDir;

    public String getSourceDir() {
        return sourceDir;
    }
    public void setSourceDir(String sourceDir) {
        this.sourceDir = sourceDir;
    }
    public String getTargetDir() {
        return targetDir;
    }
    public void setTargetDir(String targetDir) {
        this.targetDir = targetDir;
    }
    public String getSourceClassDir() {
        return sourceClassDir;
    }
    public void setSourceClassDir(String sourceClassDir) {
        this.sourceClassDir = sourceClassDir;
    }
    public String getTargetClassDir() {
        return targetClassDir;
    }
    public void setTargetClassDir(String targetClassDir) {
        this.targetClassDir = targetClassDir;
    }
}

