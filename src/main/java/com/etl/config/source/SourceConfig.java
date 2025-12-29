package com.etl.config.source;

import com.etl.config.FieldDefinition;
import com.etl.config.ModelConfig;
import com.etl.enums.ModelType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for source configuration.
 * Supports polymorphic deserialization for different source types.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "format"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CsvSourceConfig.class, name = "csv"),
        @JsonSubTypes.Type(value = XmlSourceConfig.class, name = "xml")
     //   @JsonSubTypes.Type(value = DbSourceConfig.class,  name = "db")
})
public abstract class SourceConfig implements ModelConfig {

    /**
     * The type of the source (e.g., csv, xml, db).
     */
    private final String format;

 //   private final String type="source";

    /**
     * The name of the source.
     */
    private final String sourceName;

    /**
     * The package name associated with the source.
     */
    private final String packageName;

    /**
     * The list of field definitions for the source.
     */
    private final List<? extends FieldDefinition> fields;

    /**
     * Constructs a new SourceConfig.
     *
     * @param format the type of the source
     * @param sourceName the name of the source
     * @param packageName the package name for the source
     * @param fields the list of field definitions
     */
    protected SourceConfig(String format, String sourceName, String packageName, List<? extends FieldDefinition> fields) {
        this.format = format;
        this.sourceName = sourceName;
        this.packageName = packageName;
        this.fields = fields != null ? Collections.unmodifiableList(fields) : Collections.emptyList();
    }

    /**
     * Returns the type of the source.
     *
     * @return the source type
     */
    public abstract String getFormat();

    /**
     * Returns the name of the source.
     *
     * @return the source name
     */
    public String getSourceName() {
        return sourceName;
    }

    /**
     * Returns the package name associated with the source.
     *
     * @return the package name
     */
    public String getPackageName() {
        return packageName;
    }



    /**
     * Returns an unmodifiable list of field definitions.
     *
     * @return the list of field definitions
     */
    public List<? extends FieldDefinition> getFields() {
        return fields;
    }

    /**
     * Returns the model name, which is the source name.
     *
     * @return the model name
     */
    @Override
    public String getModelName() {
        return this.sourceName;
    }

    /**
     * Returns the model type, which is the source type.
     *
     * @return the model type
     */
    @Override
    public ModelType getModelType() {
        return ModelType.SOURCE;
    }

}
