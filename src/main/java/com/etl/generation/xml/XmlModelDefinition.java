package com.etl.generation.xml;
import java.util.List;
/**
 * Standalone XML model definition used by the XML model generation spike.
 *
 * <p>The same structural definition can be used to generate source-side or target-side
 * XML classes by changing the configured package name. When {@code packageName} is omitted,
 * the standalone loader may derive a deterministic fallback from the definition file path.</p>
 */
public class XmlModelDefinition {
    private String packageName;
    private String rootClassName;
    private String recordClassName;
    private String rootElement;
    private String recordElement;
    private List<XmlFieldDefinition> fields;
    public String getPackageName() {
        return packageName;
    }
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
    public String getRootClassName() {
        return rootClassName;
    }
    public void setRootClassName(String rootClassName) {
        this.rootClassName = rootClassName;
    }
    public String getRecordClassName() {
        return recordClassName;
    }
    public void setRecordClassName(String recordClassName) {
        this.recordClassName = recordClassName;
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
