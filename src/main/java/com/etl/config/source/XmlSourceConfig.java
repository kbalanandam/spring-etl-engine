package com.etl.config.source;

import com.etl.config.ColumnConfig;
import com.etl.config.FieldDefinition;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Configuration class for XML source definitions.
 * Holds XML-specific properties such as file path, root element, and record element.
 */
@Setter
public class XmlSourceConfig extends SourceConfig {

    /**
     * -- SETTER --
     *  Sets the file path of the XML source.
     * <p>
     * -- GETTER --
     *  Gets the file path of the XML source.
     *

     */
    @Getter
    private String filePath;
    /**
     * -- SETTER --
     *  Sets the root element name of the XML.
     * <p>
     * -- GETTER --
     *  Gets the root element name of the XML.
     *

     */
    @Getter
    private String rootElement;
    /**
     * -- SETTER --
     *  Sets the record element name of the XML.
     * <p>
     * -- GETTER --
     *  Gets the record element name of the XML.
     *

     */
    @Getter
    private String recordElement;

    /**
     * -- SETTER --
     *  Sets the list of field definitions for the XML source.
     *
     */
    @JsonDeserialize(contentAs = ColumnConfig.class)
    private List<ColumnConfig> fields;

    /**
     * Constructs an XmlSourceConfig with the specified parameters.
     *
     * @param sourceName  the name of the source
     * @param packageName the package name for generated classes
     * @param fields      the list of field definitions
     */
    protected XmlSourceConfig(String sourceName, String packageName, List<? extends FieldDefinition> fields) {
        super(sourceName, packageName, fields);
    }

    /**
     * Returns the format type for this source config.
     *
     * @return ModelFormat.XML
     */
    @Override
    public ModelFormat getFormat() {
        return ModelFormat.XML;
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

}