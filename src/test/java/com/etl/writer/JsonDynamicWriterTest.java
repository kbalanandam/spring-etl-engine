package com.etl.writer;

import com.etl.config.ColumnConfig;
import com.etl.config.target.JsonTargetConfig;
import com.etl.exception.EtlErrorCategory;
import com.etl.exception.EtlExceptionDetails;
import com.etl.writer.impl.JsonDynamicWriter;
import com.etl.writer.impl.StagedJsonArrayItemWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonDynamicWriterTest {

    private final DynamicWriterFactory factory = new DynamicWriterFactory(List.of(new JsonDynamicWriter()));
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void writesJsonArrayForStandaloneWrite(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("events.json");
        JsonTargetConfig config = jsonTargetConfig(outputFile);

        ItemWriter<Object> writer = factory.createWriter(config, EventJsonRow.class);
        StagedJsonArrayItemWriter<Object> jsonWriter = (StagedJsonArrayItemWriter<Object>) writer;
        jsonWriter.open(new ExecutionContext());
        try {
            jsonWriter.write(new Chunk<>(List.of(
                    new EventJsonRow("EVT-100", "2026-05-10T08:00:00", "created", "billing"),
                    new EventJsonRow("EVT-200", "2026-05-10T08:05:00", "processed", "crm")
            )));
        } finally {
            jsonWriter.close();
        }

        JsonNode json = objectMapper.readTree(outputFile.toFile());
        assertEquals(2, json.size());
        assertEquals("EVT-100", json.get(0).get("eventCode").asText());
        assertEquals("crm", json.get(1).get("sourceSystem").asText());
    }

    @Test
    void accumulatesMultipleChunksIntoSingleJsonArray(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("events-multi-chunk.json");
        JsonTargetConfig config = jsonTargetConfig(outputFile);

        ItemWriter<Object> writer = factory.createWriter(config, EventJsonRow.class);
        StagedJsonArrayItemWriter<Object> jsonWriter = (StagedJsonArrayItemWriter<Object>) writer;
        jsonWriter.open(new ExecutionContext());
        try {
            jsonWriter.write(new Chunk<>(List.of(new EventJsonRow("EVT-100", "2026-05-10T08:00:00", "created", "billing"))));
            jsonWriter.write(new Chunk<>(List.of(new EventJsonRow("EVT-200", "2026-05-10T08:05:00", "processed", "crm"))));
        } finally {
            jsonWriter.close();
        }

        JsonNode json = objectMapper.readTree(outputFile.toFile());
        assertEquals(2, json.size());
        assertEquals("EVT-200", json.get(1).get("eventCode").asText());
    }

    @Test
    void writesDirectoryStyleJsonTargetToTargetNameFile(@TempDir Path tempDir) throws Exception {
        Path outputDirectory = tempDir.resolve("target");
        Files.createDirectories(outputDirectory);
        Path expectedOutputFile = outputDirectory.resolve("eventsjson.json");
        JsonTargetConfig config = jsonTargetConfig(outputDirectory);

        ItemWriter<Object> writer = factory.createWriter(config, EventJsonRow.class);
        StagedJsonArrayItemWriter<Object> jsonWriter = (StagedJsonArrayItemWriter<Object>) writer;
        jsonWriter.open(new ExecutionContext());
        try {
            jsonWriter.write(new Chunk<>(List.of(new EventJsonRow("EVT-100", "2026-05-10T08:00:00", "created", "billing"))));
        } finally {
            jsonWriter.close();
        }

        assertTrue(Files.exists(expectedOutputFile));
        JsonNode json = objectMapper.readTree(expectedOutputFile.toFile());
        assertEquals(1, json.size());
    }

    @Test
    void doesNotPromoteFinalJsonWhenStepFails(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("events-failed.json");
        Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
        JsonTargetConfig config = jsonTargetConfig(outputFile);

        ItemWriter<Object> writer = factory.createWriter(config, EventJsonRow.class);
        StagedJsonArrayItemWriter<Object> jsonWriter = (StagedJsonArrayItemWriter<Object>) writer;
        StepExecutionListener listener = (StepExecutionListener) writer;
        StepExecution stepExecution = new StepExecution("events-json-step", new JobExecution(1L));

        StepSynchronizationManager.register(stepExecution);
        try {
            jsonWriter.open(new ExecutionContext());
            jsonWriter.write(new Chunk<>(List.of(new EventJsonRow("EVT-900", "2026-05-10T09:00:00", "failed", "ops"))));
            jsonWriter.close();
        } finally {
            StepSynchronizationManager.close();
        }

        stepExecution.setExitStatus(ExitStatus.FAILED);
        listener.afterStep(stepExecution);

        assertFalse(Files.exists(outputFile));
        assertFalse(Files.exists(stagingFile));
    }

    @Test
    void promotesJsonOnlyAfterCompletedStepAndReplacesStaleOutput(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("events-completed.json");
        Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
        Files.writeString(outputFile, "[{\"eventCode\":\"STALE\"}]");

        StagedJsonArrayItemWriter<Object> writer = new StagedJsonArrayItemWriter<>(outputFile.toString(), objectMapper);
        StepExecution stepExecution = new StepExecution("events-json-step", new JobExecution(2L));

        StepSynchronizationManager.register(stepExecution);
        try {
            writer.open(new ExecutionContext());
            writer.write(new Chunk<>(List.of(new EventJsonRow("EVT-300", "2026-05-10T10:00:00", "completed", "erp"))));
            writer.close();

            assertTrue(Files.exists(stagingFile));
            assertEquals("[{\"eventCode\":\"STALE\"}]", Files.readString(outputFile));
        } finally {
            StepSynchronizationManager.close();
        }

        stepExecution.setExitStatus(ExitStatus.COMPLETED);
        writer.afterStep(stepExecution);

        assertFalse(Files.exists(stagingFile));
        JsonNode json = objectMapper.readTree(outputFile.toFile());
        assertEquals(1, json.size());
        assertNotEquals("STALE", json.get(0).get("eventCode").asText());
        assertEquals("EVT-300", json.get(0).get("eventCode").asText());
    }

    @Test
    void cleansOrphanedJsonAndZipPartFilesAtStepStart(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("events-orphaned.json");
        Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
        Path publishedZipStagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".zip.part");
        Files.writeString(stagingFile, "[\"partial\"]");
        Files.writeString(publishedZipStagingFile, "zip-fragment");

        StagedJsonArrayItemWriter<Object> writer = new StagedJsonArrayItemWriter<>(outputFile.toString(), objectMapper, true);

        writer.beforeStep(new StepExecution("events-json-step", new JobExecution(3L)));

        assertFalse(Files.exists(stagingFile));
        assertFalse(Files.exists(publishedZipStagingFile));
    }

    @Test
    void keepsUnrelatedPartFilesWhenCleaningJsonOrphans(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("events-owned.json");
        Path ownedStagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
        Path ownedZipStagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".zip.part");
        Path unrelatedPartFile = tempDir.resolve("unrelated.part");
        Files.writeString(ownedStagingFile, "owned-orphan");
        Files.writeString(ownedZipStagingFile, "owned-zip-orphan");
        Files.writeString(unrelatedPartFile, "must-stay");

        StagedJsonArrayItemWriter<Object> writer = new StagedJsonArrayItemWriter<>(outputFile.toString(), objectMapper, true);
        writer.beforeStep(new StepExecution("events-json-step", new JobExecution(4L)));

        assertFalse(Files.exists(ownedStagingFile));
        assertFalse(Files.exists(ownedZipStagingFile));
        assertTrue(Files.exists(unrelatedPartFile));
    }

    @Test
    void doesNotCleanOtherWriterSessionPartFilesInSameDirectory(@TempDir Path tempDir) throws Exception {
        Path outputFileA = tempDir.resolve("events-session-a.json");
        Path outputFileB = tempDir.resolve("events-session-b.json");

        Path sessionAStaging = outputFileA.resolveSibling(outputFileA.getFileName() + ".part");
        Path sessionAZipStaging = outputFileA.resolveSibling(outputFileA.getFileName() + ".zip.part");
        Path sessionBStaging = outputFileB.resolveSibling(outputFileB.getFileName() + ".part");
        Path sessionBZipStaging = outputFileB.resolveSibling(outputFileB.getFileName() + ".zip.part");

        Files.writeString(sessionAStaging, "owned-a");
        Files.writeString(sessionAZipStaging, "owned-a-zip");
        Files.writeString(sessionBStaging, "owned-b");
        Files.writeString(sessionBZipStaging, "owned-b-zip");

        StagedJsonArrayItemWriter<Object> writerA = new StagedJsonArrayItemWriter<>(outputFileA.toString(), objectMapper, true);
        writerA.beforeStep(new StepExecution("events-json-step-a", new JobExecution(5L)));

        assertFalse(Files.exists(sessionAStaging));
        assertFalse(Files.exists(sessionAZipStaging));
        assertTrue(Files.exists(sessionBStaging));
        assertTrue(Files.exists(sessionBZipStaging));
    }

  @Test
  void packagesSuccessfulJsonOutputAsZipWhenConfigured(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("events-zipped.json");
    JsonTargetConfig config = new JsonTargetConfig(
        "EventsJson",
        "com.etl.generated.job.xmltojson.target",
        List.of(column("eventCode"), column("eventTime"), column("description"), column("sourceSystem")),
        outputFile.toString(),
        true
    );

    ItemWriter<Object> writer = factory.createWriter(config, EventJsonRow.class);
    StagedJsonArrayItemWriter<Object> jsonWriter = (StagedJsonArrayItemWriter<Object>) writer;
    jsonWriter.open(new ExecutionContext());
    try {
      jsonWriter.write(new Chunk<>(List.of(new EventJsonRow("EVT-ZIP", "2026-05-10T10:30:00", "zipped", "ops"))));
    } finally {
      jsonWriter.close();
    }

    Path publishedZip = outputFile.resolveSibling(outputFile.getFileName() + ".zip");
    assertTrue(Files.exists(publishedZip));
    assertFalse(Files.exists(outputFile));
    assertEquals(List.of("events-zipped.json"), zipEntryNames(publishedZip));
    JsonNode json = objectMapper.readTree(readZipEntryContents(publishedZip, "events-zipped.json"));
    assertEquals(1, json.size());
    assertEquals("EVT-ZIP", json.get(0).get("eventCode").asText());
  }

    @Test
    void categorizesWriteBeforeOpenFailureAsTargetWrite(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("events-not-opened.json");
        StagedJsonArrayItemWriter<Object> writer = new StagedJsonArrayItemWriter<>(outputFile.toString(), objectMapper);

        Exception failure = assertThrows(Exception.class,
                () -> writer.write(new Chunk<>(List.of(new EventJsonRow("EVT-100", "2026-05-10T08:00:00", "created", "billing")))));

        assertEquals(EtlErrorCategory.TARGET_WRITE, EtlExceptionDetails.categoryOf(failure));
        assertEquals("JSON writer must be opened before write().", EtlExceptionDetails.rootCauseMessage(failure));
    }

    @Test
    void categorizesJsonSerializationFailureAsTargetWrite(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("events-serialization-failed.json");
        Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
        StagedJsonArrayItemWriter<Object> writer = new StagedJsonArrayItemWriter<>(outputFile.toString(), objectMapper);
        writer.open(new ExecutionContext());
        try {
            Exception failure = assertThrows(Exception.class,
                    () -> writer.write(new Chunk<>(List.of(new SelfReferencingJsonRow()))));

            assertEquals(EtlErrorCategory.TARGET_WRITE, EtlExceptionDetails.categoryOf(failure));
            assertTrue(EtlExceptionDetails.rootCauseMessage(failure).contains("Direct self-reference leading to cycle"));
        } finally {
            writer.close();
        }

        assertFalse(Files.exists(outputFile));
        assertFalse(Files.exists(stagingFile));
    }

    private JsonTargetConfig jsonTargetConfig(Path outputFile) {
        return new JsonTargetConfig(
                "EventsJson",
                "com.etl.generated.job.xmltojson.target",
                List.of(column("eventCode"), column("eventTime"), column("description"), column("sourceSystem")),
                outputFile.toString()
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

    public static final class EventJsonRow {
        private final String eventCode;
        private final String eventTime;
        private final String description;
        private final String sourceSystem;

        public EventJsonRow(String eventCode, String eventTime, String description, String sourceSystem) {
            this.eventCode = eventCode;
            this.eventTime = eventTime;
            this.description = description;
            this.sourceSystem = sourceSystem;
        }

        public String getEventCode() {
            return eventCode;
        }

        public String getEventTime() {
            return eventTime;
        }

        public String getDescription() {
            return description;
        }

        public String getSourceSystem() {
            return sourceSystem;
        }
    }

    public static final class SelfReferencingJsonRow {
        private final SelfReferencingJsonRow self = this;

        public SelfReferencingJsonRow getSelf() {
            return self;
        }
    }
}

