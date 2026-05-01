package com.etl.generation.xml;
import java.util.List;
/**
 * Standalone XML model definition used by the XML model generation spike.
 *
 * <p>The same structural definition can be used to generate source-side or target-side
 * XML classes by changing the configured package name.</p>
 */
public class XmlModelDefinition {
    private String packageName;
    private String rootElement;
    private String recordElement;
    private List<XmlFieldDefinition> fields;
    public String getPackageName() {
        return packageName;
    }
    public void setPackageName(String packageName) {
        this.packageName = packageName;
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
    public List<XmlFieldDefinition> getFields() {
        return fields;
    }
    public void setFields(List<XmlFieldDefinition> fields) {
        this.fields = fields;
    }
}
