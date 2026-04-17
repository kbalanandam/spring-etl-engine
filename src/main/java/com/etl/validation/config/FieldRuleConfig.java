package com.etl.validation.config;

public class FieldRuleConfig {
    private String field;
    private String rule;
    private String pattern; // For regex
    private String xsdPath; // For XSD validation
    private boolean enabled = true;

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }

    public String getRule() { return rule; }
    public void setRule(String rule) { this.rule = rule; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public String getXsdPath() { return xsdPath; }
    public void setXsdPath(String xsdPath) { this.xsdPath = xsdPath; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
