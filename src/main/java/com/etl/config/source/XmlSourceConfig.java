package com.etl.config.source;

import com.etl.config.ColumnConfig;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;

/**
 * Configuration class for XML source definitions.
 * Holds XML-specific properties such as file path, root element, and record element.
 */
public class XmlSourceConfig extends SourceConfig {

    /** Path to the XML file.
     * -- SETTER --
     *  Sets the file path for the XML source.
     *
     */
    private String filePath;

    /** Root element name of the XML.
     * -- SETTER --
     *  Sets the root element name for the XML source.
     *
     */
    private String rootElement;

    /** Record element name of the XML.
     * -- SETTER --
     *  Sets the record element name for the XML source.
     *
     */
    private String recordElement;

    /**
     * Optional flattening strategy for XML source processing.
     * Defaults to DirectXml to preserve current simple-reader behavior until overridden.
     */
    private String flatteningStrategy;

    /**
     * Optional Spring bean name for a job-specific XML flattening strategy.
     */
    private String jobSpecificStrategyBean;

    /**
     * Optional external structural XML model definition used for build-time generation,
     * especially for nested XML contracts.
     */
    private String modelDefinitionPath;

	private ValidationConfig validation;

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getRootElement() {
    return rootElement;
  }

  public void setRootElement(String rootElement) {
    this.rootElement = rootElement;
  }

  public String getRecordElement() {
    return recordElement;
  }

  public void setRecordElement(String recordElement) {
    this.recordElement = recordElement;
  }

  public String getFlatteningStrategy() {
    return flatteningStrategy == null || flatteningStrategy.isBlank() ? "DirectXml" : flatteningStrategy;
  }

  public void setFlatteningStrategy(String flatteningStrategy) {
    this.flatteningStrategy = flatteningStrategy;
  }

  public String getJobSpecificStrategyBean() {
    return jobSpecificStrategyBean;
  }

  public void setJobSpecificStrategyBean(String jobSpecificStrategyBean) {
    this.jobSpecificStrategyBean = jobSpecificStrategyBean;
  }

  public String getModelDefinitionPath() {
    return modelDefinitionPath;
  }

  public void setModelDefinitionPath(String modelDefinitionPath) {
    this.modelDefinitionPath = modelDefinitionPath;
  }

  public ValidationConfig getValidation() {
    return validation;
  }

  public void setValidation(ValidationConfig validation) {
    this.validation = validation;
  }

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

    @Override
    public ModelFormat getFormat() {
        return ModelFormat.XML;
    }

    /**
     * Returns the number of records in the XML file by counting the record elements.
     *
     * @return the record count
     * @throws IOException if file reading fails
     */
    @Override
    public int getRecordCount() throws IOException {
        int count = 0;
        XMLInputFactory factory = XMLInputFactory.newInstance();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            XMLStreamReader reader = factory.createXMLStreamReader(fis);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT &&
                        recordElement != null &&
                        recordElement.equals(reader.getLocalName())) {
                    count++;
                }
            }
            reader.close();
        } catch (Exception e) {
            throw new IOException("Failed to count records in XML file: " + filePath, e);
        }
        return count;
    }

  public static class ValidationConfig {

    private String fileNamePattern;
    private String onFailure;
    private String rejectPath;

    public String getFileNamePattern() {
      return fileNamePattern;
    }

    public void setFileNamePattern(String fileNamePattern) {
      this.fileNamePattern = fileNamePattern;
    }

    public String getOnFailure() {
      return onFailure;
    }

    public void setOnFailure(String onFailure) {
      this.onFailure = onFailure;
    }

    public String getRejectPath() {
      return rejectPath;
    }

    public void setRejectPath(String rejectPath) {
      this.rejectPath = rejectPath;
    }
  }
}
