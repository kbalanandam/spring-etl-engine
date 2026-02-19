package com.etl.config.source;

import com.etl.config.FieldDefinition;
import com.etl.config.ModelConfig;
import com.etl.enums.ModelFormat;
import com.etl.enums.ModelType;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

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
})
@Getter
public abstract class SourceConfig implements ModelConfig {

    //   private final String type="source";

    /**
     * The name of the source.
     * -- GETTER --
     *  Returns the name of the source.
     *

     */

    private final String sourceName;

    /**
     * The package name associated with the source.
     * -- GETTER --
     *  Returns the package name associated with the source.
     *

     */

    private final String packageName;

    /**
     * The list of field definitions for the source.
     * -- GETTER --
     *  Returns an unmodifiable list of field definitions.
     *

     */

    private final List<? extends FieldDefinition> fields;

    /**
     * Constructs a new SourceConfig.
     *
     * @param sourceName the name of the source
     * @param packageName the package name for the source
     * @param fields the list of field definitions
     */
    protected SourceConfig(String sourceName, String packageName, List<? extends FieldDefinition> fields) {
        this.sourceName = sourceName;
        this.packageName = packageName;
        this.fields = fields != null ? Collections.unmodifiableList(fields) : Collections.emptyList();
    }

    /**
     * Returns the type of the source.
     *
     * @return the source type
     */
    public abstract ModelFormat getFormat();


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
