package com.etl.validation.config;

import java.util.Map;
import java.util.List;

/**
 * @deprecated Legacy CSV validation-config.yaml model that is no longer used by the
 * active runtime. Use CSV source validation in {@code com.etl.config.source} and
 * processor rules in {@code com.etl.config.processor.ProcessorConfig} instead.
 */
@Deprecated(since = "1.4.0")
public class CsvValidationConfig {
    // Map of file name to list of field rules
    private Map<String, List<FieldRuleConfig>> files;

    public Map<String, List<FieldRuleConfig>> getFiles() { return files; }
    public void setFiles(Map<String, List<FieldRuleConfig>> files) { this.files = files; }
}

