package com.etl.validation.config;

public class ValidationConfig {
    private CsvValidationConfig csv;
    private XmlValidationConfig xml;

    public CsvValidationConfig getCsv() { return csv; }
    public void setCsv(CsvValidationConfig csv) { this.csv = csv; }

    public XmlValidationConfig getXml() { return xml; }
    public void setXml(XmlValidationConfig xml) { this.xml = xml; }
}

