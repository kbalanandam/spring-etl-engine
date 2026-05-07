package com.etl.writer.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Shared staged-file lifecycle support for file-based writers.
 */
final class StagedFileLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(StagedFileLifecycle.class);

    private final Path finalPath;
    private final Path stagingPath;
    private boolean streamClosed;
    private boolean stepCompletionSignaled;

    StagedFileLifecycle(String finalPath) {
        this(Path.of(finalPath));
    }

    StagedFileLifecycle(Path finalPath) {
        this.finalPath = finalPath.toAbsolutePath().normalize();
        this.stagingPath = stagingPath(this.finalPath);
    }

    Path finalPath() {
        return finalPath;
    }

    Path stagingPath() {
        return stagingPath;
    }

    void prepareForWrite() {
        try {
            streamClosed = false;
            stepCompletionSignaled = false;
            Path parent = stagingPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(stagingPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to prepare staged output path '" + stagingPath + "'.", e);
        }
    }

    void promoteIfNoActiveStep() {
        if (!hasActiveStepContext()) {
            promoteIfPresent();
        }
    }

    void streamClosed() {
        streamClosed = true;
        if (!hasActiveStepContext() || stepCompletionSignaled) {
            promoteIfPresent();
        }
    }

    ExitStatus completeStep(ExitStatus exitStatus) {
        if (ExitStatus.COMPLETED.getExitCode().equals(exitStatus.getExitCode())) {
            stepCompletionSignaled = true;
            if (streamClosed) {
                promoteIfPresent();
            }
        } else {
            stepCompletionSignaled = false;
            cleanupStagingQuietly("step did not complete successfully");
        }
        return exitStatus;
    }

    void deletePublishedOutputIfPresent() {
        try {
            Files.deleteIfExists(finalPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete published output '" + finalPath + "'.", e);
        }
    }

    private void promoteIfPresent() {
        if (!Files.exists(stagingPath)) {
            return;
        }
        try {
            Path parent = finalPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try {
                Files.move(stagingPath, finalPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(stagingPath, finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to promote staged output '" + stagingPath + "' to final output '" + finalPath + "'.", e);
        }
    }

    private void cleanupStagingQuietly(String reason) {
        try {
            Files.deleteIfExists(stagingPath);
        } catch (IOException e) {
            logger.warn("Failed to clean staged output '{}' after {}.", stagingPath, reason, e);
        }
    }

    private static boolean hasActiveStepContext() {
        return StepSynchronizationManager.getContext() != null;
    }

    private static Path stagingPath(Path finalPath) {
        String fileName = finalPath.getFileName().toString();
        return finalPath.resolveSibling(fileName + ".part");
    }
}

