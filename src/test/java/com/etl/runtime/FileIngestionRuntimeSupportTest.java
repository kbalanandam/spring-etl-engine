package com.etl.runtime;

import com.etl.config.ColumnConfig;
import com.etl.config.source.FileArchiveConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.exception.RuntimeEtlException;
import com.etl.exception.ZipPackagingException;
import com.etl.processor.validation.ValidationIssue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        stepExecution.setStatus(BatchStatus.COMPLETED);
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
  void packagesRejectArtifactAsZipWhenConfigured() throws Exception {
    Path sourceFile = tempDir.resolve("events.csv");
    Files.writeString(sourceFile, "id,eventTime,description\n,25:99:00,bad row\n");

    Path rejectDir = tempDir.resolve("rejects");
    CsvSourceConfig sourceConfig = new CsvSourceConfig(
        "Events",
        "com.etl.model.source",
        List.of(column("id"), column("eventTime"), column("description")),
        sourceFile.toString(),
        ",",
        null
    );

    ProcessorConfig processorConfig = new ProcessorConfig();
    processorConfig.setType("default");
    ProcessorConfig.RejectHandling rejectHandling = new ProcessorConfig.RejectHandling();
    rejectHandling.setEnabled(true);
    rejectHandling.setOutputPath(rejectDir + "\\");
    rejectHandling.setIncludeReasonColumns(true);
    rejectHandling.setPackageAsZip(true);
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
      assertTrue(runtimeSupport.recordRejected(new EventRecord("", "25:99:00", "bad row"), List.of(
          new ValidationIssue("id", "notNull", "id must not be null")
      )));
    } finally {
      StepSynchronizationManager.close();
    }

    stepExecution.setStatus(BatchStatus.COMPLETED);
    stepExecution.setExitStatus(ExitStatus.COMPLETED);
    runtimeSupport.completeStep(stepExecution, sourceConfig);

    Path rejectZip = Path.of(stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.REJECT_OUTPUT_PATH_KEY));
    assertTrue(rejectZip.getFileName().toString().endsWith(".zip"));
    assertTrue(Files.exists(rejectZip));
    assertEquals(1, zipEntryNames(rejectZip).size());
    String entryName = zipEntryNames(rejectZip).get(0);
    assertTrue(entryName.endsWith(".csv"));
    String zippedRejectCsv = readZipEntryContents(rejectZip, entryName);
    assertTrue(zippedRejectCsv.contains("id,eventTime,description,_rejectField,_rejectRule,_rejectMessage"));
    assertTrue(zippedRejectCsv.contains("id must not be null"));
    assertFalse(Files.exists(rejectZip.resolveSibling(entryName)));
  }

  @Test
  void concurrentRejectWritesProduceSingleHeaderAndExpectedRowCount() throws Exception {
    Path sourceFile = tempDir.resolve("events.csv");
    Files.writeString(sourceFile, "id,eventTime,description\n", StandardCharsets.UTF_8);

    Path rejectDir = tempDir.resolve("rejects");

    CsvSourceConfig sourceConfig = new CsvSourceConfig(
        "Events",
        "com.etl.model.source",
        List.of(column("id"), column("eventTime"), column("description")),
        sourceFile.toString(),
        ",",
        null
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

    int workerCount = 8;
    int rejectsPerWorker = 25;
    int expectedRows = workerCount * rejectsPerWorker;
    CountDownLatch readyGate = new CountDownLatch(workerCount);
    CountDownLatch startGate = new CountDownLatch(1);
    Queue<Throwable> failures = new ConcurrentLinkedQueue<>();
    ExecutorService executor = Executors.newFixedThreadPool(workerCount);

    for (int worker = 0; worker < workerCount; worker++) {
      int workerIndex = worker;
      executor.submit(() -> {
        readyGate.countDown();
        try {
          startGate.await(5, TimeUnit.SECONDS);
          StepSynchronizationManager.register(stepExecution);
          for (int i = 0; i < rejectsPerWorker; i++) {
            boolean rejected = runtimeSupport.recordRejected(
                new EventRecord("", "25:99:00", "bad row " + workerIndex + "-" + i),
                List.of(new ValidationIssue("id", "notNull", "id must not be null"))
            );
            if (!rejected) {
              failures.add(new AssertionError("Expected record to be rejected."));
              break;
            }
          }
        } catch (Throwable t) {
          failures.add(t);
        } finally {
          StepSynchronizationManager.close();
        }
      });
    }

    assertTrue(readyGate.await(10, TimeUnit.SECONDS));
    startGate.countDown();
    executor.shutdown();
    assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
    assertTrue(failures.isEmpty(), () -> "Concurrency failures: " + failures);

    stepExecution.setStatus(BatchStatus.COMPLETED);
    stepExecution.setExitStatus(ExitStatus.COMPLETED);
    runtimeSupport.completeStep(stepExecution, sourceConfig);

    Path rejectFile = Path.of(stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.REJECT_OUTPUT_PATH_KEY));
    assertTrue(Files.exists(rejectFile));
    List<String> lines = Files.readAllLines(rejectFile, StandardCharsets.UTF_8);

    String header = "id,eventTime,description,_rejectField,_rejectRule,_rejectMessage";
    assertEquals(header, lines.get(0));
    assertEquals(1, lines.stream().filter(header::equals).count());
    assertEquals(expectedRows + 1, lines.size());
    assertEquals(expectedRows, stepExecution.getExecutionContext().getInt(FileIngestionRuntimeSupport.REJECTED_COUNT_KEY));
  }

  @Test
  void treatsExtensionlessRejectOutputPathAsDirectoryWhenPackagingZip() throws Exception {
    Path sourceFile = tempDir.resolve("events.csv");
    Files.writeString(sourceFile, "id,eventTime,description\n,25:99:00,bad row\n");

    Path rejectDir = tempDir.resolve("rejects");
    CsvSourceConfig sourceConfig = new CsvSourceConfig(
        "Events",
        "com.etl.model.source",
        List.of(column("id"), column("eventTime"), column("description")),
        sourceFile.toString(),
        ",",
        null
    );

    ProcessorConfig processorConfig = new ProcessorConfig();
    processorConfig.setType("default");
    ProcessorConfig.RejectHandling rejectHandling = new ProcessorConfig.RejectHandling();
    rejectHandling.setEnabled(true);
    rejectHandling.setOutputPath(rejectDir.toString());
    rejectHandling.setIncludeReasonColumns(true);
    rejectHandling.setPackageAsZip(true);
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
      assertTrue(runtimeSupport.recordRejected(new EventRecord("", "25:99:00", "bad row"), List.of(
          new ValidationIssue("id", "notNull", "id must not be null")
      )));
    } finally {
      StepSynchronizationManager.close();
    }

    stepExecution.setStatus(BatchStatus.COMPLETED);
    stepExecution.setExitStatus(ExitStatus.COMPLETED);
    runtimeSupport.completeStep(stepExecution, sourceConfig);

    Path rejectZip = Path.of(stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.REJECT_OUTPUT_PATH_KEY));
    String expectedFileName = stepExecution.getStepName() + "-rejects.csv.zip";
    assertEquals(expectedFileName, rejectZip.getFileName().toString());
    assertTrue(Files.exists(rejectZip));
    assertEquals(List.of(stepExecution.getStepName() + "-rejects.csv"), zipEntryNames(rejectZip));
  }

  @Test
  void treatsExtensionlessRejectOutputPathAsDirectoryWhenNotPackagingZip() throws Exception {
    Path sourceFile = tempDir.resolve("events.csv");
    Files.writeString(sourceFile, "id,eventTime,description\n,25:99:00,bad row\n");

    Path rejectDir = tempDir.resolve("rejects");
    CsvSourceConfig sourceConfig = new CsvSourceConfig(
        "Events",
        "com.etl.model.source",
        List.of(column("id"), column("eventTime"), column("description")),
        sourceFile.toString(),
        ",",
        null
    );

    ProcessorConfig processorConfig = new ProcessorConfig();
    processorConfig.setType("default");
    ProcessorConfig.RejectHandling rejectHandling = new ProcessorConfig.RejectHandling();
    rejectHandling.setEnabled(true);
    rejectHandling.setOutputPath(rejectDir.toString());
    rejectHandling.setIncludeReasonColumns(true);
    rejectHandling.setPackageAsZip(false);
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
      assertTrue(runtimeSupport.recordRejected(new EventRecord("", "25:99:00", "bad row"), List.of(
          new ValidationIssue("id", "notNull", "id must not be null")
      )));
    } finally {
      StepSynchronizationManager.close();
    }

    stepExecution.setStatus(BatchStatus.COMPLETED);
    stepExecution.setExitStatus(ExitStatus.COMPLETED);
    runtimeSupport.completeStep(stepExecution, sourceConfig);

    Path rejectFile = Path.of(stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.REJECT_OUTPUT_PATH_KEY));
    String expectedFileName = stepExecution.getStepName() + "-rejects.csv";
    assertEquals(expectedFileName, rejectFile.getFileName().toString());
    assertTrue(Files.exists(rejectFile));
    assertTrue(Files.readString(rejectFile, StandardCharsets.UTF_8).contains("id,eventTime,description,_rejectField,_rejectRule,_rejectMessage"));
  }

  @Test
  void keepsPublishedZipPathButWritesCsvWhenConfiguredRejectPathEndsWithZipAndPackagingDisabled() throws Exception {
    Path sourceFile = tempDir.resolve("events.csv");
    Files.writeString(sourceFile, "id,eventTime,description\n,25:99:00,bad row\n");

    Path configuredZipPath = tempDir.resolve("rejects").resolve("custom-output.zip");
    Path expectedWritableCsvPath = configuredZipPath.resolveSibling("custom-output.csv");

    CsvSourceConfig sourceConfig = new CsvSourceConfig(
        "Events",
        "com.etl.model.source",
        List.of(column("id"), column("eventTime"), column("description")),
        sourceFile.toString(),
        ",",
        null
    );

    ProcessorConfig processorConfig = new ProcessorConfig();
    processorConfig.setType("default");
    ProcessorConfig.RejectHandling rejectHandling = new ProcessorConfig.RejectHandling();
    rejectHandling.setEnabled(true);
    rejectHandling.setOutputPath(configuredZipPath.toString());
    rejectHandling.setIncludeReasonColumns(true);
    rejectHandling.setPackageAsZip(false);
    processorConfig.setRejectHandling(rejectHandling);

    ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
    mapping.setSource("Events");
    mapping.setTarget("EventsCsv");
    mapping.setFields(List.of(field("id", "id"), field("eventTime", "eventTime"), field("description", "description")));
    processorConfig.setMappings(List.of(mapping));

    FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    runtimeSupport.initializeStep(stepExecution, sourceConfig, processorConfig, mapping);

    assertEquals(configuredZipPath.normalize().toString(), stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.REJECT_OUTPUT_PATH_KEY));

    StepSynchronizationManager.register(stepExecution);
    try {
      assertTrue(runtimeSupport.recordRejected(new EventRecord("", "25:99:00", "bad row"), List.of(
          new ValidationIssue("id", "notNull", "id must not be null")
      )));
    } finally {
      StepSynchronizationManager.close();
    }

    stepExecution.setExitStatus(ExitStatus.COMPLETED);
    runtimeSupport.completeStep(stepExecution, sourceConfig);

    assertFalse(Files.exists(configuredZipPath));
    assertTrue(Files.exists(expectedWritableCsvPath));
    assertTrue(Files.readString(expectedWritableCsvPath, StandardCharsets.UTF_8).contains("id must not be null"));
  }

  @Test
  void packagesArchivedCsvSourceAsZipAfterSuccessfulCompletion() throws Exception {
    Path sourceFile = tempDir.resolve("events.csv");
    Files.writeString(sourceFile, "id,eventTime\nEVT-1,08:30:00\n", StandardCharsets.UTF_8);

    Path archiveDir = tempDir.resolve("archive/zipped-output");
    CsvSourceConfig.ArchiveConfig archive = new CsvSourceConfig.ArchiveConfig();
    archive.setEnabled(true);
    archive.setSuccessPath(archiveDir.toString());
    archive.setNamePattern("{originalName}-{timestamp}");
    archive.setPackageAsZip(true);

    CsvSourceConfig sourceConfig = new CsvSourceConfig(
        "Events",
        "com.etl.model.source",
        List.of(column("id"), column("eventTime")),
        sourceFile.toString(),
        ",",
        archive
    );

    ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
    mapping.setSource("Events");
    mapping.setTarget("EventsCsv");
    mapping.setFields(List.of(field("id", "id"), field("eventTime", "eventTime")));

    FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    runtimeSupport.initializeStep(stepExecution, sourceConfig, new ProcessorConfig(), mapping);

    stepExecution.setStatus(BatchStatus.COMPLETED);
    stepExecution.setExitStatus(ExitStatus.COMPLETED);
    runtimeSupport.completeStep(stepExecution, sourceConfig);

    Path archivedPath = Path.of(stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.ARCHIVED_SOURCE_PATH_KEY));
    assertTrue(archivedPath.getFileName().toString().endsWith(".zip"));
    assertTrue(Files.exists(archivedPath));
    assertFalse(Files.exists(sourceFile));
    assertEquals(List.of("events.csv"), zipEntryNames(archivedPath));
    assertTrue(readZipEntryContents(archivedPath, "events.csv").contains("EVT-1,08:30:00"));
  }

  @Test
  void archivesSourceWithDeterministicTimestampWhenClockIsInjected() throws Exception {
    Path sourceFile = tempDir.resolve("events.csv");
    Files.writeString(sourceFile, "id,eventTime\nEVT-1,08:30:00\n", StandardCharsets.UTF_8);

    Path archiveDir = tempDir.resolve("archive/deterministic");
    CsvSourceConfig.ArchiveConfig archive = new CsvSourceConfig.ArchiveConfig();
    archive.setEnabled(true);
    archive.setSuccessPath(archiveDir.toString());
    archive.setNamePattern("{originalName}-{timestamp}");

    CsvSourceConfig sourceConfig = new CsvSourceConfig(
        "Events",
        "com.etl.model.source",
        List.of(column("id"), column("eventTime")),
        sourceFile.toString(),
        ",",
        archive
    );

    ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
    mapping.setSource("Events");
    mapping.setTarget("EventsCsv");
    mapping.setFields(List.of(field("id", "id"), field("eventTime", "eventTime")));

    Clock fixedClock = Clock.fixed(Instant.parse("2026-05-19T01:02:03Z"), ZoneOffset.UTC);
    FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport(new FileSourceArtifactSupport(), fixedClock);
    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    runtimeSupport.initializeStep(stepExecution, sourceConfig, new ProcessorConfig(), mapping);

    stepExecution.setStatus(BatchStatus.COMPLETED);
    stepExecution.setExitStatus(ExitStatus.COMPLETED);
    runtimeSupport.completeStep(stepExecution, sourceConfig);

    Path archivedPath = Path.of(stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.ARCHIVED_SOURCE_PATH_KEY));
    assertEquals("events.csv-20260519-010203", archivedPath.getFileName().toString());
    assertTrue(Files.exists(archivedPath));
    assertFalse(Files.exists(sourceFile));
  }

  @Test
  void archivesWhenBatchStatusCompletedEvenWithCustomExitStatus() throws Exception {
    Path sourceFile = tempDir.resolve("events-custom-exit.csv");
    Files.writeString(sourceFile, "id,eventTime\nEVT-1,08:30:00\n", StandardCharsets.UTF_8);

    Path archiveDir = tempDir.resolve("archive/custom-exit");
    CsvSourceConfig.ArchiveConfig archive = new CsvSourceConfig.ArchiveConfig();
    archive.setEnabled(true);
    archive.setSuccessPath(archiveDir.toString());
    archive.setNamePattern("{originalName}-{timestamp}");

    CsvSourceConfig sourceConfig = new CsvSourceConfig(
        "Events",
        "com.etl.model.source",
        List.of(column("id"), column("eventTime")),
        sourceFile.toString(),
        ",",
        archive
    );

    ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
    mapping.setSource("Events");
    mapping.setTarget("EventsCsv");
    mapping.setFields(List.of(field("id", "id"), field("eventTime", "eventTime")));

    FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    runtimeSupport.initializeStep(stepExecution, sourceConfig, new ProcessorConfig(), mapping);

    stepExecution.setStatus(BatchStatus.COMPLETED);
    stepExecution.setExitStatus(new ExitStatus("COMPLETED_WITH_WARNINGS", "custom success status"));
    runtimeSupport.completeStep(stepExecution, sourceConfig);

    String archivedPath = stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.ARCHIVED_SOURCE_PATH_KEY);
    assertFalse(archivedPath.isBlank());
    assertTrue(Files.exists(Path.of(archivedPath)));
    assertFalse(Files.exists(sourceFile));
  }

  @Test
  void wrapsZipPackagingFailureWithRuntimeExceptionWhenArchivePackagingCannotStart() {
    Path missingSourceFile = tempDir.resolve("missing-events.csv");
    Path archiveDir = tempDir.resolve("archive/zipped-output");

    CsvSourceConfig.ArchiveConfig archive = new CsvSourceConfig.ArchiveConfig();
    archive.setEnabled(true);
    archive.setSuccessPath(archiveDir.toString());
    archive.setNamePattern("{originalName}-{timestamp}");
    archive.setPackageAsZip(true);

    CsvSourceConfig sourceConfig = new CsvSourceConfig(
        "Events",
        "com.etl.model.source",
        List.of(column("id"), column("eventTime")),
        missingSourceFile.toString(),
        ",",
        archive
    );

    ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
    mapping.setSource("Events");
    mapping.setTarget("EventsCsv");
    mapping.setFields(List.of(field("id", "id"), field("eventTime", "eventTime")));

    FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    runtimeSupport.initializeStep(stepExecution, sourceConfig, new ProcessorConfig(), mapping);

    stepExecution.setStatus(BatchStatus.COMPLETED);
    stepExecution.setExitStatus(ExitStatus.COMPLETED);
    RuntimeEtlException exception = assertThrows(
        RuntimeEtlException.class,
        () -> runtimeSupport.completeStep(stepExecution, sourceConfig)
    );

    assertTrue(exception.getMessage().contains("Failed to archive source file"));
    assertTrue(exception.getCause() instanceof ZipPackagingException);
  }

  @Test
  void archivesOriginalZipSourceAndDeletesPreparedExtractedFileAfterSuccessfulCompletion() throws Exception {
    Path sourceZip = tempDir.resolve("events.zip");
    writeZip(sourceZip, "events.csv", "id,eventTime\nEVT-1,08:30:00\n");

    Path archiveDir = tempDir.resolve("archive/zipped-success");
    CsvSourceConfig.ArchiveConfig archive = new CsvSourceConfig.ArchiveConfig();
    archive.setEnabled(true);
    archive.setSuccessPath(archiveDir.toString());
    archive.setNamePattern("{originalName}-{timestamp}");

    CsvSourceConfig sourceConfig = new CsvSourceConfig();
    sourceConfig.setSourceName("EventsZip");
    sourceConfig.setPackageName("com.etl.model.source");
    sourceConfig.setFilePath(sourceZip.toString());
    sourceConfig.setDelimiter(",");
    sourceConfig.setFields(List.of(column("id"), column("eventTime")));
    sourceConfig.setArchive(archive);

    FileSourceArtifactSupport artifactSupport = new FileSourceArtifactSupport();
    Path extractedCsv = artifactSupport.resolveReadablePath(sourceConfig);
    Path preparedDir = extractedCsv.getParent();
    assertTrue(Files.exists(extractedCsv));
    assertTrue(extractedCsv.startsWith(defaultPreparedRoot()));
    assertFalse(extractedCsv.startsWith(sourceZip.getParent()));

    ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
    mapping.setSource("EventsZip");
    mapping.setTarget("EventsCsv");
    mapping.setFields(List.of(field("id", "id"), field("eventTime", "eventTime")));

    FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport(artifactSupport);
    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    runtimeSupport.initializeStep(stepExecution, sourceConfig, new ProcessorConfig(), mapping);

    stepExecution.setStatus(BatchStatus.COMPLETED);
    stepExecution.setExitStatus(ExitStatus.COMPLETED);
    runtimeSupport.completeStep(stepExecution, sourceConfig);

    String archivedPath = stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.ARCHIVED_SOURCE_PATH_KEY);
    assertFalse(archivedPath.isBlank());
    assertTrue(Files.exists(Path.of(archivedPath)));
    assertFalse(Files.exists(sourceZip));
    assertFalse(Files.exists(extractedCsv));
    assertFalse(Files.exists(preparedDir));
    assertEquals(sourceZip.toString(), sourceConfig.getFilePath());
    assertTrue(sourceConfig.getPreparedFilePath() == null || sourceConfig.getPreparedFilePath().isBlank());
  }

          @Test
          void archivesXmlSourceAfterSuccessfulCompletionWhenArchiveIsEnabled() throws Exception {
            Path sourceFile = tempDir.resolve("events.xml");
            Files.writeString(sourceFile, "<Events><Event><id>EVT-1</id></Event></Events>");

            Path archiveDir = tempDir.resolve("archive/xml-success");
            FileArchiveConfig archive = new FileArchiveConfig();
            archive.setEnabled(true);
            archive.setSuccessPath(archiveDir.toString());
            archive.setNamePattern("{originalName}-{timestamp}");

            XmlSourceConfig sourceConfig = new XmlSourceConfig();
            sourceConfig.setSourceName("EventsXml");
            sourceConfig.setPackageName("com.etl.model.source");
            sourceConfig.setFilePath(sourceFile.toString());
            sourceConfig.setRootElement("Events");
            sourceConfig.setRecordElement("Event");
            sourceConfig.setArchive(archive);
            sourceConfig.setFields(List.of(column("id")));

            ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
            mapping.setSource("EventsXml");
            mapping.setTarget("EventsCsv");
            mapping.setFields(List.of(field("id", "id")));

            FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
            StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
            runtimeSupport.initializeStep(stepExecution, sourceConfig, new ProcessorConfig(), mapping);

            stepExecution.setStatus(BatchStatus.COMPLETED);
            stepExecution.setExitStatus(ExitStatus.COMPLETED);
            runtimeSupport.completeStep(stepExecution, sourceConfig);

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
      void duplicateValueNullInputIsIgnoredWithoutThrowing() {
        CsvSourceConfig sourceConfig = new CsvSourceConfig();
        sourceConfig.setSourceName("Events");

        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Events");
        mapping.setTarget("EventsCsv");
        mapping.setFields(List.of(field("id", "id")));

        FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        runtimeSupport.initializeStep(stepExecution, sourceConfig, new ProcessorConfig(), mapping);
        StepSynchronizationManager.register(stepExecution);
        try {
          assertFalse(runtimeSupport.isDuplicateValue("id", null));
          assertFalse(runtimeSupport.isDuplicateValue("id", null));
        } finally {
          StepSynchronizationManager.close();
          runtimeSupport.completeStep(stepExecution, sourceConfig);
        }
      }

      @Test
      void initializeStepFailsFastWhenRejectMappingTargetColumnIsNull() {
        CsvSourceConfig sourceConfig = new CsvSourceConfig();
        sourceConfig.setSourceName("Events");
        sourceConfig.setFilePath(tempDir.resolve("events.csv").toString());

        ProcessorConfig processorConfig = new ProcessorConfig();
        processorConfig.setType("default");
        ProcessorConfig.RejectHandling rejectHandling = new ProcessorConfig.RejectHandling();
        rejectHandling.setEnabled(true);
        rejectHandling.setOutputPath(tempDir.resolve("rejects").toString());
        rejectHandling.setIncludeReasonColumns(true);
        processorConfig.setRejectHandling(rejectHandling);

        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Events");
        mapping.setTarget("EventsCsv");
        mapping.setFields(List.of(field("id", null)));

        FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();

        RuntimeEtlException exception = assertThrows(
            RuntimeEtlException.class,
            () -> runtimeSupport.initializeStep(stepExecution, sourceConfig, processorConfig, mapping)
        );
        assertTrue(exception.getMessage().contains("non-blank target field names"));
      }

      @Test
      void duplicateTrackingFailsFastWhenConfiguredCapIsExceeded() {
        CsvSourceConfig sourceConfig = new CsvSourceConfig();
        sourceConfig.setSourceName("Events");

        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Events");
        mapping.setTarget("EventsCsv");
        mapping.setFields(List.of(field("id", "id")));

        FileIngestionRuntimeSupport runtimeSupport =
            new FileIngestionRuntimeSupport(new FileSourceArtifactSupport(), Clock.systemDefaultZone(), 2);
        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        runtimeSupport.initializeStep(stepExecution, sourceConfig, new ProcessorConfig(), mapping);
        StepSynchronizationManager.register(stepExecution);
        try {
          assertFalse(runtimeSupport.isDuplicateValue("id", "EVT-1"));
          assertFalse(runtimeSupport.isDuplicateValue("id", "EVT-2"));

          RuntimeEtlException exception = assertThrows(
              RuntimeEtlException.class,
              () -> runtimeSupport.isDuplicateValue("id", "EVT-3")
          );
          assertTrue(exception.getMessage().contains("Duplicate tracking limit exceeded"));
        } finally {
          StepSynchronizationManager.close();
          runtimeSupport.completeStep(stepExecution, sourceConfig);
        }
      }

      @Test
      void initializeStepFailsFastWhenStepExecutionIdIsNull() {
        CsvSourceConfig sourceConfig = new CsvSourceConfig();
        sourceConfig.setSourceName("Events");

        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Events");
        mapping.setTarget("EventsCsv");
        mapping.setFields(List.of(field("id", "id")));

        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        stepExecution.setId(null);

        FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();

        RuntimeEtlException exception = assertThrows(
            RuntimeEtlException.class,
            () -> runtimeSupport.initializeStep(stepExecution, sourceConfig, new ProcessorConfig(), mapping)
        );
        assertTrue(exception.getMessage().contains("StepExecution id is required"));
      }

      @Test
      void duplicateTrackingFailsFastWhenCurrentStepExecutionIdIsNull() {
        CsvSourceConfig sourceConfig = new CsvSourceConfig();
        sourceConfig.setSourceName("Events");

        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Events");
        mapping.setTarget("EventsCsv");
        mapping.setFields(List.of(field("id", "id")));

        FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
        StepExecution initializedStep = MetaDataInstanceFactory.createStepExecution();
        runtimeSupport.initializeStep(initializedStep, sourceConfig, new ProcessorConfig(), mapping);

        StepExecution nullIdStep = MetaDataInstanceFactory.createStepExecution();
        nullIdStep.setId(null);

        StepSynchronizationManager.register(nullIdStep);
        try {
          RuntimeEtlException exception = assertThrows(
              RuntimeEtlException.class,
              () -> runtimeSupport.isDuplicateValue("id", "EVT-1")
          );
          assertTrue(exception.getMessage().contains("StepExecution id is required"));
        } finally {
          StepSynchronizationManager.close();
          runtimeSupport.completeStep(initializedStep, sourceConfig);
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

  private Path defaultPreparedRoot() {
    return Path.of(System.getProperty("java.io.tmpdir"))
        .toAbsolutePath()
        .normalize()
        .resolve("spring-etl-engine")
        .resolve("prepared-sources");
  }

  private void writeZip(Path zipFile, String entryName, String contents) throws Exception {
    try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
      zipOutputStream.putNextEntry(new ZipEntry(entryName));
      zipOutputStream.write(contents.getBytes(StandardCharsets.UTF_8));
      zipOutputStream.closeEntry();
    }
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

      private record EventRecord(String id, String eventTime, String description) {
      }
}








