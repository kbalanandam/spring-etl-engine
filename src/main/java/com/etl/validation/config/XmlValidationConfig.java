package com.etl.validation.config;

import java.util.Map;
import java.util.List;

public class XmlValidationConfig {
    // Map of file name to list of field rules
    private Map<String, List<FieldRuleConfig>> files;

    public Map<String, List<FieldRuleConfig>> getFiles() { return files; }
    public void setFiles(Map<String, List<FieldRuleConfig>> files) { this.files = files; }
}

