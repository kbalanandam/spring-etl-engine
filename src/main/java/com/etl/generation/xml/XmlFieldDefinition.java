package com.etl.generation.xml;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * Structural XML field definition used by the XML model generation spike.
 *
 * <p>This definition is intentionally structural only. Nested child fields describe
 * nested XML object graphs, not flattening or business mapping rules.</p>
 */
public class XmlFieldDefinition {

    private String name;
    private String type;
    private String className;
    private boolean collection;
    private List<XmlFieldDefinition> fields;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public boolean isCollection() {
        return collection;
    }

    public void setCollection(boolean collection) {
        this.collection = collection;
    }

    public List<XmlFieldDefinition> getFields() {
        return fields;
    }

    public void setFields(List<XmlFieldDefinition> fields) {
        this.fields = fields;
    }

    @JsonIgnore
    public boolean isNested() {
        return fields != null && !fields.isEmpty();
    }
}

