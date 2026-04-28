package com.etl.validation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;

/**
 * @deprecated Legacy loader for validation-config.yaml. Active validation config is loaded
 * through scenario source/processor YAML files by {@code com.etl.config.ConfigLoader}.
 */
@Deprecated(since = "1.4.0")
public class ValidationConfigLoader {
    public static ValidationConfig load(String yamlPath) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(new File(yamlPath), ValidationConfig.class);
    }
}

