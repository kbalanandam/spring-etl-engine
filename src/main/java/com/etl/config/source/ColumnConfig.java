package com.etl.config.source;

import com.etl.config.FieldDefinition;

public class ColumnConfig implements FieldDefinition {
    private String name;
    private String type;

    // Getters and Setters
    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}