package com.etl.config.source;

import com.etl.config.ColumnConfig;
import com.etl.config.FieldDefinition;
import com.etl.enums.ModelFormat;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

/**
 * Configuration class for CSV data sources.
 * <p>
 * Holds CSV-specific properties such as file path and delimiter.
 * The format for this config is always "csv".
 */
public class CsvSourceConfig extends SourceConfig {

    /** Path to the CSV file. */
    private String filePath;

    /** Delimiter used in the CSV file. */
    private String delimiter;

	private ArchiveConfig archive;

  public String getFilePath() {
    return filePath;
  }

  public void setFilePath(String filePath) {
    this.filePath = filePath;
  }

  public String getDelimiter() {
    return delimiter;
  }

  public void setDelimiter(String delimiter) {
    this.delimiter = delimiter;
  }

  public ArchiveConfig getArchive() {
    return archive;
  }

  public void setArchive(ArchiveConfig archive) {
    this.archive = archive;
  }

    // No-args constructor for YAML/object mapping
    public CsvSourceConfig() {
        super();
    }

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
        this(sourceName, packageName, fields, filePath, delimiter, null);
    }

    public CsvSourceConfig(
            String sourceName,
            String packageName,
            List<ColumnConfig> fields,
            String filePath,
            String delimiter,
            ArchiveConfig archive
    ) {
        super(sourceName, packageName, fields);
        this.filePath = filePath;
        this.delimiter = delimiter;
		this.archive = archive;
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

    /**
     * Returns the number of records in the CSV file (excluding header if present).
     *
     * @return the record count
     * @throws IOException if file reading fails
     */
    @Override
    public int getRecordCount() throws IOException {
        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            while (br.readLine() != null) {
                count++;
            }
        }
        // Optionally subtract 1 if header is present
        return count > 0 ? count - 1 : 0;
    }

  public static class ArchiveConfig {

    private boolean enabled;
    private String successPath;
    private String namePattern;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getSuccessPath() {
      return successPath;
    }

    public void setSuccessPath(String successPath) {
      this.successPath = successPath;
    }

    public String getNamePattern() {
      return namePattern;
    }

    public void setNamePattern(String namePattern) {
      this.namePattern = namePattern;
    }
  }
}
