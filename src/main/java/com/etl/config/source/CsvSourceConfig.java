package com.etl.config.source;

import com.etl.config.ColumnConfig;
import com.etl.config.FieldDefinition;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

/**
 * Configuration class for CSV data sources.
 * <p>
 * Holds CSV-specific properties such as file path and delimiter.
 * The format for this config is always "csv".
 */
@Getter
public class CsvSourceConfig extends SourceConfig {

    /** Path to the CSV file. */
    private final String filePath;

    /** Delimiter used in the CSV file. */
    private final String delimiter;

    /**
     * Constructs a new {@code CsvSourceConfig} instance.
     *
     * @param sourceName   the name of the source
     * @param packageName  the package name for generated code
     * @param fields       the list of column definitions for the CSV
     * @param filePath     the path to the CSV file
     * @param delimiter    the delimiter used in the CSV file
     */
    @JsonCreator
    public CsvSourceConfig(
            @JsonProperty("sourceName") String sourceName,
            @JsonProperty("packageName") String packageName,
            @JsonProperty("fields") List<ColumnConfig> fields,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("delimiter") String delimiter
    ) {
        super(sourceName, packageName, fields);
        this.filePath = filePath;
        this.delimiter = delimiter;
    }


    /**
     * Returns the format of the source configuration.
     *
     * @return the string "csv"
     */
    @Override
    public ModelFormat getFormat() {
        return ModelFormat.CSV;
    }

    /**
     * Returns the list of column definitions for the CSV source.
     *
     * @return the list of {@link ColumnConfig}
     */
    @Override
    public List<? extends FieldDefinition> getFields() {
        return super.getFields();
    }
}