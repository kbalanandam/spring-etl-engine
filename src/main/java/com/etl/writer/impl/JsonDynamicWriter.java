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

/**
 * Runtime JSON writer builder for staged JSON-array output.
 *
 * <p>The shipped JSON target path writes one array document per step. This class owns
 * output-path resolution and delegates staged publication plus incremental array writing
 * to {@link StagedJsonArrayItemWriter}.</p>
 */
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
        return new StagedJsonArrayItemWriter<>(resolveOutputPath(jsonConfig), objectMapper, jsonConfig.isPackageAsZip());
    }

    /**
     * Resolves the final JSON output file path from the selected target config.
     *
     * <p>If the configured path points at a directory-like location, the writer derives a
     * deterministic file name from {@code targetName}. Otherwise the configured path is
     * treated as the final JSON file.</p>
     */
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

