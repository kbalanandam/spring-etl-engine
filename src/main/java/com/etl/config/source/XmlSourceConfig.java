package com.etl.config.source;

import com.etl.config.FieldDefinition;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

/**
 * Configuration class for XML source definitions.
 * Holds XML-specific properties such as file path, root element, and record element.
 */
public class XmlSourceConfig extends SourceConfig {

    private String filePath;
    private String rootElement;
    private String recordElement;

    @JsonDeserialize(contentAs = ColumnConfig.class)
    private List<ColumnConfig> fields;

    /**
     * Constructs an XmlSourceConfig with the specified parameters.
     *
     * @param type        the format type (should be "xml")
     * @param sourceName  the name of the source
     * @param packageName the package name for generated classes
     * @param fields      the list of field definitions
     */
    protected XmlSourceConfig(String type, String sourceName, String packageName, List<? extends FieldDefinition> fields) {
        super(type, sourceName, packageName, fields);
    }

    /**
     * Gets the file path of the XML source.
     *
     * @return the file path
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Sets the file path of the XML source.
     *
     * @param filePath the file path to set
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Gets the root element name of the XML.
     *
     * @return the root element name
     */
    public String getRootElement() {
        return rootElement;
    }

    /**
     * Sets the root element name of the XML.
     *
     * @param rootElement the root element name to set
     */
    public void setRootElement(String rootElement) {
        this.rootElement = rootElement;
    }

    /**
     * Gets the record element name of the XML.
     *
     * @return the record element name
     */
    public String getRecordElement() {
        return recordElement;
    }

    /**
     * Sets the record element name of the XML.
     *
     * @param recordElement the record element name to set
     */
    public void setRecordElement(String recordElement) {
        this.recordElement = recordElement;
    }

    /**
     * Returns the format type for this source config.
     *
     * @return "xml"
     */
    @Override
    public String getFormat() {
        return "xml";
    }

    /**
     * Gets the list of field definitions for the XML source.
     *
     * @return the list of fields
     */
    @Override
    public List<? extends FieldDefinition> getFields() {
        return fields;
    }

    /**
     * Sets the list of field definitions for the XML source.
     *
     * @param fields the list of fields to set
     */
    public void setFields(List<ColumnConfig> fields) {
        this.fields = fields;
    }
}