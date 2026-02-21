package com.etl.config.source;

import com.etl.config.ColumnConfig;
import com.etl.config.FieldDefinition;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * Configuration class for XML source definitions.
 * Holds XML-specific properties such as file path, root element, and record element.
 */
@Getter
public class XmlSourceConfig extends SourceConfig {

    /** Path to the XML file. */
    private String filePath;

    /** Root element name of the XML. */
    private String rootElement;

    /** Record element name of the XML. */
    private String recordElement;

    // No-args constructor for YAML/object mapping
    public XmlSourceConfig() {
        super();
    }

    /**
     * Constructs an XmlSourceConfig with the specified parameters.
     *
     * @param sourceName   the name of the source
     * @param packageName  the package name for generated classes
     * @param fields       the list of field definitions
     * @param filePath     the XML file path
     * @param rootElement  the root element name
     * @param recordElement the record element name
     */
    @JsonCreator
    public XmlSourceConfig(
            @JsonProperty("sourceName") String sourceName,
            @JsonProperty("packageName") String packageName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("rootElement") String rootElement,
            @JsonProperty("recordElement") String recordElement
    ) {
        super(sourceName, packageName, fields);
        this.filePath = filePath;
        this.rootElement = rootElement;
        this.recordElement = recordElement;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public void setRootElement(String rootElement) {
        this.rootElement = rootElement;
    }

    public void setRecordElement(String recordElement) {
        this.recordElement = recordElement;
    }

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.XML;
    }
}
