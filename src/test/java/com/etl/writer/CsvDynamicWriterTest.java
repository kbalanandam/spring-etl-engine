package com.etl.writer;

import com.etl.config.ColumnConfig;
import com.etl.config.target.CsvTargetConfig;
import com.etl.exception.EtlErrorCategory;
import com.etl.exception.EtlExceptionDetails;
import com.etl.writer.impl.CsvDynamicWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvDynamicWriterTest {

    private final DynamicWriterFactory factory = new DynamicWriterFactory(List.of(new CsvDynamicWriter()));

    @Test
    void writesHeaderRowWhenConfigured(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("customers-with-header.csv");
        CsvTargetConfig config = csvTargetConfig(outputFile, true);
        CustomerCsvRow row = new CustomerCsvRow("1001", "alice@example.com", "Chennai", "IN");

        ItemWriter<Object> writer = factory.createWriter(config, CustomerCsvRow.class);
        FlatFileItemWriter<Object> csvWriter = (FlatFileItemWriter<Object>) writer;
        csvWriter.open(new ExecutionContext());
        try {
            csvWriter.write(new Chunk<>(List.of(row)));
        } finally {
            csvWriter.close();
        }

        assertTrue(row.getId().startsWith("1001"));
        assertTrue(row.getEmail().contains("@"));
        assertEquals("Chennai", row.getCity());
        assertEquals("IN", row.getCountry());

        String csv = Files.readString(outputFile);
        assertTrue(csv.contains("id,email,city,country"));
        assertTrue(csv.contains("1001,alice@example.com,Chennai,IN"));
    }

    @Test
    void doesNotWriteHeaderRowByDefault(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("customers-no-header.csv");
        CsvTargetConfig config = csvTargetConfig(outputFile, false);
        CustomerCsvRow row = new CustomerCsvRow("1001", "alice@example.com", "Chennai", "IN");

        ItemWriter<Object> writer = factory.createWriter(config, CustomerCsvRow.class);
        FlatFileItemWriter<Object> csvWriter = (FlatFileItemWriter<Object>) writer;
        csvWriter.open(new ExecutionContext());
        try {
            csvWriter.write(new Chunk<>(List.of(row)));
        } finally {
            csvWriter.close();
        }

        assertTrue(row.getId().startsWith("1001"));
        assertTrue(row.getEmail().contains("@"));
        assertEquals("Chennai", row.getCity());
        assertEquals("IN", row.getCountry());

        String csv = Files.readString(outputFile);
        assertFalse(csv.startsWith("id,email,city,country"));
        assertTrue(csv.contains("1001,alice@example.com,Chennai,IN"));
    }

    @Test
    void defaultsDelimiterToCommaWhenOmitted(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("customers-default-delimiter.csv");
        CsvTargetConfig config = new CsvTargetConfig(
                "TagValidationCsvIntermediate",
                "com.etl.generated.job.xmlnestedcsvroundtrip.target",
                List.of(column("id"), column("email"), column("city"), column("country")),
                outputFile.toString(),
                null,
                true
        );

        ItemWriter<Object> writer = factory.createWriter(config, CustomerCsvRow.class);
        FlatFileItemWriter<Object> csvWriter = (FlatFileItemWriter<Object>) writer;
        csvWriter.open(new ExecutionContext());
        try {
            csvWriter.write(new Chunk<>(List.of(new CustomerCsvRow("1001", "alice@example.com", "Chennai", "IN"))));
        } finally {
            csvWriter.close();
        }

        String csv = Files.readString(outputFile);
        assertTrue(csv.contains("id,email,city,country"));
        assertTrue(csv.contains("1001,alice@example.com,Chennai,IN"));
    }

    @Test
    void writesConfiguredAlternateDelimiter(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("customers-pipe.csv");
        CsvTargetConfig config = new CsvTargetConfig(
                "TagValidationCsvIntermediate",
                "com.etl.generated.job.xmlnestedcsvroundtrip.target",
                List.of(column("id"), column("email"), column("city"), column("country")),
                outputFile.toString(),
                "|",
                true
        );

        ItemWriter<Object> writer = factory.createWriter(config, CustomerCsvRow.class);
        FlatFileItemWriter<Object> csvWriter = (FlatFileItemWriter<Object>) writer;
        csvWriter.open(new ExecutionContext());
        try {
            csvWriter.write(new Chunk<>(List.of(new CustomerCsvRow("1001", "alice@example.com", "Chennai", "IN"))));
        } finally {
            csvWriter.close();
        }

        String csv = Files.readString(outputFile);
        assertTrue(csv.contains("id|email|city|country"));
        assertTrue(csv.contains("1001|alice@example.com|Chennai|IN"));
    }

    @Test
    void replacesExistingFinalFileOnSuccessfulStandaloneWrite(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("customers-replace.csv");
        Files.writeString(outputFile, "stale,data\nold,row\n");
        CsvTargetConfig config = csvTargetConfig(outputFile, true);

        ItemWriter<Object> writer = factory.createWriter(config, CustomerCsvRow.class);
        FlatFileItemWriter<Object> csvWriter = (FlatFileItemWriter<Object>) writer;
        csvWriter.open(new ExecutionContext());
        try {
            csvWriter.write(new Chunk<>(List.of(new CustomerCsvRow("2002", "bob@example.com", "Pune", "IN"))));
        } finally {
            csvWriter.close();
        }

        String csv = Files.readString(outputFile);
        assertFalse(csv.contains("stale,data"));
        assertTrue(csv.contains("2002,bob@example.com,Pune,IN"));
    }

  @Test
  void packagesSuccessfulCsvOutputAsZipWhenConfigured(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers-zipped.csv");
    CsvTargetConfig config = new CsvTargetConfig(
        "TagValidationCsvIntermediate",
        "com.etl.generated.job.xmlnestedcsvroundtrip.target",
        List.of(column("id"), column("email"), column("city"), column("country")),
        outputFile.toString(),
        ",",
        true,
        true
    );

    ItemWriter<Object> writer = factory.createWriter(config, CustomerCsvRow.class);
    FlatFileItemWriter<Object> csvWriter = (FlatFileItemWriter<Object>) writer;
    csvWriter.open(new ExecutionContext());
    try {
      csvWriter.write(new Chunk<>(List.of(new CustomerCsvRow("5005", "zip@example.com", "Bengaluru", "IN"))));
    } finally {
      csvWriter.close();
    }

    Path publishedZip = outputFile.resolveSibling(outputFile.getFileName() + ".zip");
    assertTrue(Files.exists(publishedZip));
    assertFalse(Files.exists(outputFile));
    assertEquals(List.of("customers-zipped.csv"), zipEntryNames(publishedZip));
    String csv = readZipEntryContents(publishedZip, "customers-zipped.csv");
    assertTrue(csv.contains("id,email,city,country"));
    assertTrue(csv.contains("5005,zip@example.com,Bengaluru,IN"));
  }

  @Test
  void resolvesDirectoryStyleOutputPathWithoutMalformedConcatenation(@TempDir Path tempDir) throws Exception {
    Path outputDirectory = tempDir.resolve("csv-output");
    Files.createDirectories(outputDirectory);
    CsvTargetConfig config = new CsvTargetConfig(
        "TagValidationCsvIntermediate",
        "com.etl.generated.job.xmlnestedcsvroundtrip.target",
        List.of(column("id"), column("email"), column("city"), column("country")),
        outputDirectory.toString(),
        ",",
        true,
        true
    );

    ItemWriter<Object> writer = factory.createWriter(config, CustomerCsvRow.class);
    FlatFileItemWriter<Object> csvWriter = (FlatFileItemWriter<Object>) writer;
    csvWriter.open(new ExecutionContext());
    try {
      csvWriter.write(new Chunk<>(List.of(new CustomerCsvRow("7007", "path@example.com", "Hyderabad", "IN"))));
    } finally {
      csvWriter.close();
    }

    Path expectedZip = outputDirectory.resolve("tagvalidationcsvintermediate.csv.zip");
    assertTrue(Files.exists(expectedZip));
    assertEquals(List.of("tagvalidationcsvintermediate.csv"), zipEntryNames(expectedZip));
    assertFalse(Files.exists(tempDir.resolve("csv-outputtagvalidationcsvintermediate.csv.zip")));
  }

    @Test
    void doesNotPromoteFinalFileWhenStepFails(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("customers-failed.csv");
        Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
        CsvTargetConfig config = csvTargetConfig(outputFile, true);

        ItemWriter<Object> writer = factory.createWriter(config, CustomerCsvRow.class);
        FlatFileItemWriter<Object> csvWriter = (FlatFileItemWriter<Object>) writer;
        StepExecutionListener listener = (StepExecutionListener) writer;
        StepExecution stepExecution = new StepExecution("customers-step", new JobExecution(1L));

        StepSynchronizationManager.register(stepExecution);
        try {
            csvWriter.open(new ExecutionContext());
            csvWriter.write(new Chunk<>(List.of(new CustomerCsvRow("3003", "fail@example.com", "Delhi", "IN"))));
            csvWriter.close();
        } finally {
            StepSynchronizationManager.close();
        }

        stepExecution.setExitStatus(ExitStatus.FAILED);
        listener.afterStep(stepExecution);

        assertFalse(Files.exists(outputFile));
        assertFalse(Files.exists(stagingFile));
    }

    @Test
    void cleansOrphanedCsvPartFileAtStepStart(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("customers-orphaned.csv");
        Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
        Files.writeString(stagingFile, "orphaned-partial-row\n");

        ItemWriter<Object> writer = factory.createWriter(csvTargetConfig(outputFile, true), CustomerCsvRow.class);
        StepExecutionListener listener = (StepExecutionListener) writer;

        listener.beforeStep(new StepExecution("customers-step", new JobExecution(2L)));

        assertFalse(Files.exists(stagingFile));
    }

    @Test
    void keepsUnrelatedPartFilesWhenCleaningCsvOrphans(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("customers-owned.csv");
        Path ownedStagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
        Path unrelatedPartFile = tempDir.resolve("other-step-output.csv.part");
        Files.writeString(ownedStagingFile, "owned-orphan");
        Files.writeString(unrelatedPartFile, "must-stay");

        ItemWriter<Object> writer = factory.createWriter(csvTargetConfig(outputFile, true), CustomerCsvRow.class);
        StepExecutionListener listener = (StepExecutionListener) writer;

        listener.beforeStep(new StepExecution("customers-step", new JobExecution(3L)));

        assertFalse(Files.exists(ownedStagingFile));
        assertTrue(Files.exists(unrelatedPartFile));
    }

  @Test
  void cleansStagingAndCategorizesCsvWriteFailureAsTargetWrite(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers-write-failed.csv");
    Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
    CsvTargetConfig config = csvTargetConfig(outputFile, true);

    ItemWriter<Object> writer = factory.createWriter(config, BrokenCustomerCsvRow.class);
    FlatFileItemWriter<Object> csvWriter = (FlatFileItemWriter<Object>) writer;
    csvWriter.open(new ExecutionContext());

    Exception failure = assertThrows(
        Exception.class,
        () -> csvWriter.write(new Chunk<>(List.of(new BrokenCustomerCsvRow("4004", "broken@example.com", "Mumbai", "IN"))))
    );
    assertEquals(EtlErrorCategory.TARGET_WRITE, EtlExceptionDetails.categoryOf(failure));

    csvWriter.close();

    assertFalse(Files.exists(outputFile));
    assertFalse(Files.exists(stagingFile));
  }

    private CsvTargetConfig csvTargetConfig(Path outputFile, boolean includeHeader) {
        return new CsvTargetConfig(
                "TagValidationCsvIntermediate",
                "com.etl.generated.job.xmlnestedcsvroundtrip.target",
                List.of(column("id"), column("email"), column("city"), column("country")),
                outputFile.toString(),
                ",",
                includeHeader
        );
    }

    private ColumnConfig column(String name) {
        ColumnConfig columnConfig = new ColumnConfig();
        columnConfig.setName(name);
        columnConfig.setType("String");
        return columnConfig;
    }

      private List<String> zipEntryNames(Path zipFile) throws Exception {
        List<String> entryNames = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
          ZipEntry entry;
          while ((entry = zipInputStream.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
              entryNames.add(entry.getName());
            }
          }
        }
        return entryNames;
      }

      private String readZipEntryContents(Path zipFile, String expectedEntryName) throws Exception {
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
          ZipEntry entry;
          while ((entry = zipInputStream.getNextEntry()) != null) {
            if (!entry.isDirectory() && expectedEntryName.equals(entry.getName())) {
              return new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
          }
        }
        return "";
      }

    public static class CustomerCsvRow {
        private final String id;
        private final String email;
        private final String city;
        private final String country;

        public CustomerCsvRow(String id, String email, String city, String country) {
            this.id = id;
            this.email = email;
            this.city = city;
            this.country = country;
        }

        public String getId() {
            return id;
        }

        //noinspection unused
        public String getEmail() {
            return email;
        }

        //noinspection unused
        public String getCity() {
            return city;
        }

        //noinspection unused
        public String getCountry() {
            return country;
        }
    }

  public static final class BrokenCustomerCsvRow extends CustomerCsvRow {
    public BrokenCustomerCsvRow(String id, String email, String city, String country) {
      super(id, email, city, country);
    }

    @Override
    public String getEmail() {
      throw new IllegalStateException("boom");
    }
  }
}

