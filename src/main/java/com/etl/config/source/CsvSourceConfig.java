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
public class CsvSourceConfig extends SourceConfig implements FileSourceConfig {

    /** Path to the CSV file. */
    private String filePath;

    /** Delimiter used in the CSV file. */
    private String delimiter;

  /** Whether the runtime should treat the first CSV line as a header row and skip it. */
  private boolean skipHeader = true;

	private ArchiveConfig archive;

    private ValidationConfig validation;

  private ParserConfig parser;

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

  public boolean isSkipHeader() {
    return skipHeader;
  }

  public void setSkipHeader(boolean skipHeader) {
    this.skipHeader = skipHeader;
  }

  public ArchiveConfig getArchive() {
    return archive;
  }

  public void setArchive(ArchiveConfig archive) {
    this.archive = archive;
  }

  @Override
  public FileArchiveConfig getArchiveConfig() {
    return archive;
  }

	public ValidationConfig getValidation() {
		return validation;
	}

	public void setValidation(ValidationConfig validation) {
		this.validation = validation;
	}

  public ParserConfig getParser() {
    return parser;
  }

  public void setParser(ParserConfig parser) {
    this.parser = parser;
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
    this(sourceName, packageName, fields, filePath, delimiter, null, null, true);
    }

    public CsvSourceConfig(
            String sourceName,
            String packageName,
            List<ColumnConfig> fields,
            String filePath,
            String delimiter,
            ArchiveConfig archive
    ) {
		this(sourceName, packageName, fields, filePath, delimiter, archive, null, true);
  }

  public CsvSourceConfig(
      String sourceName,
      String packageName,
      List<ColumnConfig> fields,
      String filePath,
      String delimiter,
      ArchiveConfig archive,
      ValidationConfig validation
  ) {
    this(sourceName, packageName, fields, filePath, delimiter, archive, validation, true);
  }

  public CsvSourceConfig(
      String sourceName,
      String packageName,
      List<ColumnConfig> fields,
      String filePath,
      String delimiter,
      ArchiveConfig archive,
      ValidationConfig validation,
      boolean skipHeader
  ) {
        super(sourceName, packageName, fields);
        this.filePath = filePath;
        this.delimiter = delimiter;
		this.archive = archive;
    this.validation = validation;
    this.skipHeader = skipHeader;
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
		return skipHeader && count > 0 ? count - 1 : count;
    }

  public Character resolveQuoteCharacter() {
    if (parser == null || parser.getQuoteCharacter() == null || parser.getQuoteCharacter().isBlank()) {
      return null;
    }

    String trimmed = parser.getQuoteCharacter().trim();
    if (trimmed.length() != 1) {
      throw new IllegalArgumentException("parser.quoteCharacter must be exactly one character when configured.");
    }
    return trimmed.charAt(0);
  }

  public void validateParserConfiguration() {
    Character quoteCharacter = resolveQuoteCharacter();
    if (quoteCharacter == null) {
      return;
    }

    if (delimiter == null || delimiter.isBlank()) {
      throw new IllegalArgumentException("parser.quoteCharacter requires a non-blank delimiter.");
    }

    if (delimiter.equals(String.valueOf(quoteCharacter))) {
      throw new IllegalArgumentException("parser.quoteCharacter must differ from delimiter.");
    }
  }

  public static class ArchiveConfig extends FileArchiveConfig {
  }

  public static class ParserConfig {

    private String quoteCharacter;

    public String getQuoteCharacter() {
      return quoteCharacter;
    }

    public void setQuoteCharacter(String quoteCharacter) {
      this.quoteCharacter = quoteCharacter;
    }
  }

  public static class ValidationConfig {

    private boolean allowEmpty = true;
    private boolean requireHeaderMatch;
    private String fileNamePattern;
    private String onFailure;
    private String rejectPath;

    public boolean isAllowEmpty() {
      return allowEmpty;
    }

    public void setAllowEmpty(boolean allowEmpty) {
      this.allowEmpty = allowEmpty;
    }

    public boolean isRequireHeaderMatch() {
      return requireHeaderMatch;
    }

    public void setRequireHeaderMatch(boolean requireHeaderMatch) {
      this.requireHeaderMatch = requireHeaderMatch;
    }

    public String getFileNamePattern() {
      return fileNamePattern;
    }

    public void setFileNamePattern(String fileNamePattern) {
      this.fileNamePattern = fileNamePattern;
    }

    public String getOnFailure() {
      return onFailure;
    }

    public void setOnFailure(String onFailure) {
      this.onFailure = onFailure;
    }

    public String getRejectPath() {
      return rejectPath;
    }

    public void setRejectPath(String rejectPath) {
      this.rejectPath = rejectPath;
    }
  }
}
