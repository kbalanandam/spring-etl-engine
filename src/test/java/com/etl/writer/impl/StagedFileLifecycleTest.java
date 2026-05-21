package com.etl.writer.impl;

import com.etl.exception.RuntimeEtlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StagedFileLifecycleTest {

    @Test
    void retriesStepStartCleanupForTransientLockAndSucceeds(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("events.csv");
        Path stagingFile = outputFile.resolveSibling("events.csv.part");
        Files.writeString(stagingFile, "partial");

        AtomicInteger deleteAttempts = new AtomicInteger();
        List<Long> waits = new ArrayList<>();

        StagedFileLifecycle lifecycle = new StagedFileLifecycle(
                outputFile,
                false,
                null,
                path -> {
                    if (deleteAttempts.getAndIncrement() == 0) {
                        throw new FileSystemException(path.toString(), null,
                                "The process cannot access the file because it is being used by another process");
                    }
                    Files.delete(path);
                },
                waits::add);

        lifecycle.cleanupOrphanedArtifactsAtStepStart();

        assertEquals(2, deleteAttempts.get());
        assertEquals(List.of(100L), waits);
        assertFalse(Files.exists(stagingFile));
    }

    @Test
    void failsStepStartCleanupAfterRetryBudgetExhausted(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("events.csv");
        Path stagingFile = outputFile.resolveSibling("events.csv.part");
        Files.writeString(stagingFile, "partial");

        AtomicInteger deleteAttempts = new AtomicInteger();
        List<Long> waits = new ArrayList<>();

        StagedFileLifecycle lifecycle = new StagedFileLifecycle(
                outputFile,
                false,
                null,
                path -> {
                    deleteAttempts.incrementAndGet();
                    throw new FileSystemException(path.toString(), null,
                            "The process cannot access the file because it is being used by another process");
                },
                waits::add);

        RuntimeEtlException failure = assertThrows(RuntimeEtlException.class, lifecycle::cleanupOrphanedArtifactsAtStepStart);

        assertEquals(4, deleteAttempts.get());
        assertEquals(List.of(100L, 250L, 500L), waits);
        assertInstanceOf(FileSystemException.class, failure.getCause());
    }

    @Test
    void prepareForWriteDoesNotRetryTransientLockDeleteFailures(@TempDir Path tempDir) throws Exception {
        Path outputFile = tempDir.resolve("events.csv");
        Path stagingFile = outputFile.resolveSibling("events.csv.part");
        Files.writeString(stagingFile, "partial");

        AtomicInteger deleteAttempts = new AtomicInteger();
        List<Long> waits = new ArrayList<>();

        StagedFileLifecycle lifecycle = new StagedFileLifecycle(
                outputFile,
                false,
                null,
                path -> {
                    deleteAttempts.incrementAndGet();
                    throw new FileSystemException(path.toString(), null,
                            "The process cannot access the file because it is being used by another process");
                },
                waits::add);

        RuntimeEtlException failure = assertThrows(RuntimeEtlException.class, lifecycle::prepareForWrite);

        assertEquals(1, deleteAttempts.get());
        assertEquals(List.of(), waits);
        assertInstanceOf(FileSystemException.class, failure.getCause());
    }
}

