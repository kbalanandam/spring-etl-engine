package com.etl.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.etl.config.ColumnConfig;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.JsonTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.enums.ModelFormat;
import com.etl.exception.FactoryException;
import com.etl.model.source.Customers;
import com.etl.writer.exception.NoWriterFoundException;
import com.etl.writer.impl.JsonDynamicWriter;
import com.etl.writer.impl.StagedJsonArrayItemWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;

import com.etl.writer.impl.CsvDynamicWriter;
import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings({"rawtypes", "unchecked"})
class DynamicWriterFactoryTest {

    private final DynamicWriterFactory factory = new DynamicWriterFactory(List.of(new CsvDynamicWriter(), new JsonDynamicWriter()));

    @Test
    void createsCsvWriterAndPersistsOutput(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_test.csv");
    CsvTargetConfig config = getCsvTargetConfig(outputFile);

    ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
    assertNotNull(writer);

    FlatFileItemWriter<?> flatWriter = assertInstanceOf(FlatFileItemWriter.class, writer);
    flatWriter.afterPropertiesSet();
    flatWriter.open(new ExecutionContext());

    Customers customer = new Customers();
    customer.setId(1);
    customer.setName("John Doe");
    customer.setEmail("john@example.com");

    flatWriter.write(new Chunk(List.of(customer)));
    flatWriter.close();

    assertEquals("1,John Doe,john@example.com", Files.readString(outputFile).trim());
    }

    @Test
    void createsJsonWriterAndPersistsOutput(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("customers_test.json");
        JsonTargetConfig config = getJsonTargetConfig(outputFile);

        ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
        assertNotNull(writer);

        StagedJsonArrayItemWriter<Object> jsonWriter = assertInstanceOf(StagedJsonArrayItemWriter.class, writer);
        jsonWriter.open(new ExecutionContext());
        try {
            Customers customer = new Customers();
            customer.setId(1);
            customer.setName("John Doe");
            customer.setEmail("john@example.com");

            jsonWriter.write(new Chunk(List.of(customer)));
        } finally {
            jsonWriter.close();
        }

        var json = new ObjectMapper().readTree(outputFile.toFile());
        assertEquals(1, json.size());
        assertEquals(1, json.get(0).get("id").asInt());
        assertEquals("John Doe", json.get(0).get("name").asText());
        assertEquals("john@example.com", json.get(0).get("email").asText());
    }

  @Test
  void failsFastWhenMultipleWritersRegisterSameFormat() {
    FactoryException failure = assertThrows(
        FactoryException.class,
        () -> new DynamicWriterFactory(List.of(new CsvDynamicWriter(), new DuplicateCsvWriter()))
    );

    assertTrue(failure.getMessage().startsWith("Multiple writers registered for format: CSV"));
  }

	@Test
	void prefersOverrideWriterWhenFormatConflicts() throws Exception {
		DynamicWriterFactory overrideFactory = new DynamicWriterFactory(List.of(new CsvDynamicWriter(), new OverrideCsvWriter()));

		CsvTargetConfig config = getCsvTargetConfig(Path.of("target", "override-test.csv"));
		assertInstanceOf(OverrideCsvItemWriter.class, overrideFactory.createWriter(config, Customers.class));
	}

  @Test
  void failsWhenTwoOverrideWritersRegisterSameFormat() {
    FactoryException failure = assertThrows(FactoryException.class,
        () -> new DynamicWriterFactory(List.of(new OverrideCsvWriter(), new SecondOverrideCsvWriter())));

    assertTrue(failure.getMessage().contains("Multiple writers registered for format: CSV"));
    assertTrue(failure.getMessage().contains("override-csv-writer"));
    assertTrue(failure.getMessage().contains("second-override-csv-writer"));
  }

  @Test
  void throwsWriterSpecificExceptionWhenFormatHasNoRegisteredWriter() {
    TargetConfig missingTarget = new TargetConfig("RelationalOut", "com.etl.model.target", List.of()) {
      @Override
      public ModelFormat getFormat() {
        return ModelFormat.RELATIONAL;
      }
    };

    NoWriterFoundException failure = assertThrows(
        NoWriterFoundException.class,
        () -> factory.createWriter(missingTarget, Customers.class)
    );

    assertEquals("No writer found for format: RELATIONAL", failure.getMessage());
  }

  private static CsvTargetConfig getCsvTargetConfig(Path outputFile) {
        ColumnConfig col1 = new ColumnConfig();
        col1.setName("id");
        col1.setType("integer");

        ColumnConfig col2 = new ColumnConfig();
        col2.setName("name");
        col2.setType("string");

        ColumnConfig col3 = new ColumnConfig();
        col3.setName("email");
        col3.setType("string");

        List<ColumnConfig> columnConfig = List.of(col1, col2, col3);
        return new CsvTargetConfig(
                "customers",
                "com.etl.model.target",
                columnConfig,
                outputFile.toString(),
                ","
        );
    }

    private static JsonTargetConfig getJsonTargetConfig(Path outputFile) {
        ColumnConfig col1 = new ColumnConfig();
        col1.setName("id");
        col1.setType("integer");

        ColumnConfig col2 = new ColumnConfig();
        col2.setName("name");
        col2.setType("string");

        ColumnConfig col3 = new ColumnConfig();
        col3.setName("email");
        col3.setType("string");

        List<ColumnConfig> columnConfig = List.of(col1, col2, col3);
        return new JsonTargetConfig(
                "CustomersOut",
                "com.etl.model.target",
                columnConfig,
                outputFile.toString()
        );
    }

  private static final class DuplicateCsvWriter implements DynamicWriter {
    @Override
    public ModelFormat getFormat() {
      return ModelFormat.CSV;
    }

    @Override
    public ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) {
      return chunk -> {
      };
    }
  }

  private static final class OverrideCsvWriter implements DynamicWriter {
    @Override
    public String extensionId() {
      return "override-csv-writer";
    }

    @Override
    public boolean isOverride() {
      return true;
    }

    @Override
    public ModelFormat getFormat() {
      return ModelFormat.CSV;
    }

    @Override
    public ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) {
      return new OverrideCsvItemWriter();
    }
  }

  private static final class OverrideCsvItemWriter implements ItemWriter<Object> {
    @Override
    public void write(Chunk<? extends Object> chunk) {
    }
  }

  private static final class SecondOverrideCsvWriter implements DynamicWriter {
    @Override
    public String extensionId() {
      return "second-override-csv-writer";
    }

    @Override
    public boolean isOverride() {
      return true;
    }

    @Override
    public ModelFormat getFormat() {
      return ModelFormat.CSV;
    }

    @Override
    public ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) {
      return chunk -> {
      };
    }
  }
}
