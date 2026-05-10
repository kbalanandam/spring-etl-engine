package com.etl.writer.impl;

import com.etl.config.target.JsonTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.enums.ModelFormat;
import com.etl.writer.DynamicWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;

@Component("jsonWriter")
public class JsonDynamicWriter implements DynamicWriter {

    private final ObjectMapper objectMapper;

    public JsonDynamicWriter() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    JsonDynamicWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.JSON;
    }

    @Override
    public ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) {
        JsonTargetConfig jsonConfig = (JsonTargetConfig) config;
        return new StagedJsonArrayItemWriter<>(resolveOutputPath(jsonConfig), objectMapper);
    }

    private String resolveOutputPath(JsonTargetConfig jsonConfig) {
        String configuredPath = jsonConfig.getFilePath();
        if (configuredPath == null || configuredPath.isBlank()) {
            return configuredPath;
        }

        String trimmedPath = configuredPath.trim();
        if (trimmedPath.endsWith("/") || trimmedPath.endsWith("\\") || new File(trimmedPath).isDirectory()) {
            return Path.of(trimmedPath, jsonConfig.getTargetName().toLowerCase() + ".json")
                    .toString();
        }
        return trimmedPath;
    }
}

