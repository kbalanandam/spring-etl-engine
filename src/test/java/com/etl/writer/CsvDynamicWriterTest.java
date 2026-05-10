package com.etl.writer;

import com.etl.config.ColumnConfig;
import com.etl.config.target.CsvTargetConfig;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    public static final class CustomerCsvRow {
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
}

