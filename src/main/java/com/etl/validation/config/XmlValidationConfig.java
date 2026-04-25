package com.etl.validation.config;

import java.util.Map;
import java.util.List;

/**
 * @deprecated Legacy XML validation-config.yaml model that is no longer used by the
 * active runtime. Future XML source validation should be modeled alongside source config
 * validation instead of this standalone package.
 */
@Deprecated(since = "1.4.0")
public class XmlValidationConfig {
    // Map of file name to list of field rules
    private Map<String, List<FieldRuleConfig>> files;

    public Map<String, List<FieldRuleConfig>> getFiles() { return files; }
    public void setFiles(Map<String, List<FieldRuleConfig>> files) { this.files = files; }
}

