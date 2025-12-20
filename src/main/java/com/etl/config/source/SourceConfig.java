package com.etl.config.source;

import com.etl.config.FieldDefinition;
import com.etl.config.ModelConfig;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
     //   include = JsonTypeInfo.As.PROPERTY,
        property = "type"          // <-- uses YAML "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CsvSourceConfig.class, name = "csv"),
        @JsonSubTypes.Type(value = XmlSourceConfig.class, name = "xml"),
        @JsonSubTypes.Type(value = DbSourceConfig.class,  name = "db")
})
public abstract class SourceConfig implements ModelConfig {

    protected String type;
    protected String sourceName;
    protected String packageName;
    public abstract String getType();
    protected List<? extends FieldDefinition> fields;

    public String getSourceName() {
        return sourceName;
    }

    public String getPackageName() {
        return packageName;
    }

  public List<? extends FieldDefinition> getFields() {
        return fields;
    }
}
