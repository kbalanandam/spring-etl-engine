package com.etl.validation.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.File;

public class ValidationConfigLoader {
    public static ValidationConfig load(String yamlPath) throws Exception {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        return mapper.readValue(new File(yamlPath), ValidationConfig.class);
    }
}

