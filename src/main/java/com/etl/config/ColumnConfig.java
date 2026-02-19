package com.etl.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ColumnConfig implements FieldDefinition {
    private String name;
    private String type;
}
