package com.etl.config.source;

import com.etl.config.ColumnConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvSourceConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void recordCountSubtractsHeaderWhenSkipHeaderIsEnabled() throws IOException {
        Path csvFile = tempDir.resolve("customers-with-header.csv");
        Files.writeString(csvFile, "id,name\n1,Alice\n2,Bob\n");

        CsvSourceConfig config = csvSource(csvFile);

        assertTrue(config.isSkipHeader());
        assertEquals(2, config.getRecordCount());
    }

    @Test
    void recordCountIncludesFirstLineWhenSkipHeaderIsDisabled() throws IOException {
        Path csvFile = tempDir.resolve("customers-no-header.csv");
        Files.writeString(csvFile, "1,Alice\n2,Bob\n");

        CsvSourceConfig config = csvSource(csvFile);
        config.setSkipHeader(false);

        assertEquals(2, config.getRecordCount());
    }

  @Test
  void resolveQuoteCharacterReturnsNullWhenParserIsNotConfigured() {
    CsvSourceConfig config = csvSource(tempDir.resolve("customers.csv"));

    assertNull(config.resolveQuoteCharacter());
  }

  @Test
  void validateParserConfigurationRejectsMultiCharacterQuoteSetting() {
    CsvSourceConfig config = csvSource(tempDir.resolve("customers.csv"));
    CsvSourceConfig.ParserConfig parser = new CsvSourceConfig.ParserConfig();
    parser.setQuoteCharacter("''");
    config.setParser(parser);

    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, config::validateParserConfiguration);
    assertEquals("parser.quoteCharacter must be exactly one character when configured.", failure.getMessage());
  }

  @Test
  void validateParserConfigurationRejectsQuoteCharacterThatMatchesDelimiter() {
    CsvSourceConfig config = csvSource(tempDir.resolve("customers.csv"));
    config.setDelimiter("|");
    CsvSourceConfig.ParserConfig parser = new CsvSourceConfig.ParserConfig();
    parser.setQuoteCharacter("|");
    config.setParser(parser);

    IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, config::validateParserConfiguration);
    assertEquals("parser.quoteCharacter must differ from delimiter.", failure.getMessage());
  }

    private CsvSourceConfig csvSource(Path filePath) {
        CsvSourceConfig config = new CsvSourceConfig();
        config.setSourceName("Customers");
        config.setPackageName("com.etl.model.source");
        config.setFilePath(filePath.toString());
        config.setDelimiter(",");
        config.setFields(List.of(column("id"), column("name")));
        return config;
    }

    private ColumnConfig column(String name) {
        ColumnConfig column = new ColumnConfig();
        column.setName(name);
        column.setType("String");
        return column;
    }
}

