package com.etl.validation.config;

/**
 * @deprecated Legacy validation-config.yaml binding model that is no longer part of the
 * active ETL runtime path. Use source config validation in {@code com.etl.config} and
 * processor field rules in {@code com.etl.config.processor.ProcessorConfig} instead.
 */
@Deprecated(since = "1.4.0")
public class ValidationConfig {
    private CsvValidationConfig csv;
    private XmlValidationConfig xml;

    public CsvValidationConfig getCsv() { return csv; }
    public void setCsv(CsvValidationConfig csv) { this.csv = csv; }

    public XmlValidationConfig getXml() { return xml; }
    public void setXml(XmlValidationConfig xml) { this.xml = xml; }
}

