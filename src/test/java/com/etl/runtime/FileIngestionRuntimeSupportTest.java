package com.etl.runtime;

import com.etl.config.ColumnConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.processor.validation.ValidationIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileIngestionRuntimeSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void writesRejectArtifactAndArchivesSourceAfterSuccessfulCompletion() throws Exception {
        Path sourceFile = tempDir.resolve("events.csv");
        Files.writeString(sourceFile, "id,eventTime,description\n,25:99:00,bad row\n");

        Path rejectDir = tempDir.resolve("rejects");
        Path archiveDir = tempDir.resolve("archive/success");

        CsvSourceConfig.ArchiveConfig archive = new CsvSourceConfig.ArchiveConfig();
        archive.setEnabled(true);
        archive.setSuccessPath(archiveDir.toString());
        archive.setNamePattern("{originalName}-{timestamp}");

        CsvSourceConfig sourceConfig = new CsvSourceConfig(
                "Events",
                "com.etl.model.source",
                List.of(column("id"), column("eventTime"), column("description")),
                sourceFile.toString(),
                ",",
                archive
        );

        ProcessorConfig processorConfig = new ProcessorConfig();
        processorConfig.setType("default");
        ProcessorConfig.RejectHandling rejectHandling = new ProcessorConfig.RejectHandling();
        rejectHandling.setEnabled(true);
            rejectHandling.setOutputPath(rejectDir + "\\");
        rejectHandling.setIncludeReasonColumns(true);
        processorConfig.setRejectHandling(rejectHandling);

        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Events");
        mapping.setTarget("EventsCsv");
        mapping.setFields(List.of(field("id", "id"), field("eventTime", "eventTime"), field("description", "description")));
        processorConfig.setMappings(List.of(mapping));

        FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        runtimeSupport.initializeStep(stepExecution, sourceConfig, processorConfig, mapping);

        StepSynchronizationManager.register(stepExecution);
        try {
            boolean rejected = runtimeSupport.recordRejected(new EventRecord("", "25:99:00", "bad row"), List.of(
                    new ValidationIssue("id", "notNull", "id must not be null"),
                    new ValidationIssue("eventTime", "timeFormat", "eventTime must match HH:mm:ss")
            ));
            assertTrue(rejected);
        } finally {
            StepSynchronizationManager.close();
        }

        stepExecution.setExitStatus(ExitStatus.COMPLETED);
        runtimeSupport.completeStep(stepExecution, sourceConfig);

        Path rejectFile = Path.of(stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.REJECT_OUTPUT_PATH_KEY));
        assertTrue(Files.exists(rejectFile));
        List<String> rejectLines = Files.readAllLines(rejectFile);
        assertEquals("id,eventTime,description,_rejectField,_rejectRule,_rejectMessage", rejectLines.get(0));
        assertTrue(rejectLines.get(1).contains("id|eventTime"));
        assertEquals(1, stepExecution.getExecutionContext().getInt(FileIngestionRuntimeSupport.REJECTED_COUNT_KEY));

        String archivedPath = stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.ARCHIVED_SOURCE_PATH_KEY);
        assertFalse(archivedPath.isBlank());
        assertTrue(Files.exists(Path.of(archivedPath)));
        assertFalse(Files.exists(sourceFile));
    }

      @Test
      void resetsDuplicateTrackingForEachStepExecution() {
        CsvSourceConfig sourceConfig = new CsvSourceConfig();
        sourceConfig.setSourceName("Events");

        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Events");
        mapping.setTarget("EventsCsv");
        mapping.setFields(List.of(field("id", "id")));

        FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();

        StepExecution firstStepExecution = MetaDataInstanceFactory.createStepExecution();
        runtimeSupport.initializeStep(firstStepExecution, sourceConfig, new ProcessorConfig(), mapping);
        StepSynchronizationManager.register(firstStepExecution);
        try {
          assertFalse(runtimeSupport.isDuplicateValue("id", "EVT-1001"));
          assertTrue(runtimeSupport.isDuplicateValue("id", "EVT-1001"));
        } finally {
          StepSynchronizationManager.close();
          runtimeSupport.completeStep(firstStepExecution, sourceConfig);
        }

        StepExecution secondStepExecution = MetaDataInstanceFactory.createStepExecution();
        runtimeSupport.initializeStep(secondStepExecution, sourceConfig, new ProcessorConfig(), mapping);
        StepSynchronizationManager.register(secondStepExecution);
        try {
          assertFalse(runtimeSupport.isDuplicateValue("id", "EVT-1001"));
        } finally {
          StepSynchronizationManager.close();
          runtimeSupport.completeStep(secondStepExecution, sourceConfig);
        }
      }

        @Test
        void supportsCompositeDuplicateTrackingAndIgnoresIncompleteKeys() {
          CsvSourceConfig sourceConfig = new CsvSourceConfig();
          sourceConfig.setSourceName("Events");

          ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
          mapping.setSource("Events");
          mapping.setTarget("EventsCsv");
          mapping.setFields(List.of(field("id", "id"), field("eventTime", "eventTime")));

          FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
          StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
          runtimeSupport.initializeStep(stepExecution, sourceConfig, new ProcessorConfig(), mapping);
          StepSynchronizationManager.register(stepExecution);
          try {
            assertFalse(runtimeSupport.isDuplicateValues("id::id|eventTime", List.of("EVT-1001", "08:30:00")));
            assertFalse(runtimeSupport.isDuplicateValues("id::id|eventTime", List.of("EVT-1001", "09:45:00")));
            assertTrue(runtimeSupport.isDuplicateValues("id::id|eventTime", List.of("EVT-1001", "08:30:00")));
            assertFalse(runtimeSupport.isDuplicateValues("id::id|eventTime", List.of("EVT-1002", "")));
            assertFalse(runtimeSupport.isDuplicateValues("id::id|eventTime", List.of("EVT-1002", "")));
          } finally {
            StepSynchronizationManager.close();
            runtimeSupport.completeStep(stepExecution, sourceConfig);
          }
        }

    private ProcessorConfig.FieldMapping field(String from, String to) {
        ProcessorConfig.FieldMapping field = new ProcessorConfig.FieldMapping();
        field.setFrom(from);
        field.setTo(to);
        return field;
    }

      private ColumnConfig column(String name) {
        ColumnConfig column = new ColumnConfig();
        column.setName(name);
        column.setType("String");
        return column;
    }

      private record EventRecord(String id, String eventTime, String description) {
      }
}


