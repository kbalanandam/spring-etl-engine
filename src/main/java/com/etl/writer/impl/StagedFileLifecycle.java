package com.etl.writer.impl;

import com.etl.exception.RuntimeEtlException;
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
 * Shared staged-file publication contract for file-based writers.
 *
 * <p><strong>Transition status:</strong> REUSE.</p>
 *
 * <p>This helper keeps one invariant for the active CSV, XML, and JSON file target path:
 * writer implementations should write into a sibling {@code .part} file first and only publish
 * the configured final output path once the write stream has closed cleanly and the surrounding
 * step has completed successfully.</p>
 *
 * <p>The lifecycle therefore works with two distinct signals:</p>
 * <ul>
 *   <li><strong>stream closed</strong> — the writer finished its own open/write/close cycle</li>
 *   <li><strong>step completion signaled</strong> — Spring Batch reported the enclosing step exit status</li>
 * </ul>
 *
 * <p>Inside an active step, both signals must be present before the staged file is promoted to the
 * final path. Outside a step context, such as standalone tests or direct tasklet-style usage, the
 * lifecycle can promote immediately after the stream closes because there is no surrounding step
 * outcome to wait for.</p>
 *
 * <p>Failed steps never publish staged output. Instead, the {@code .part} file is deleted so a
 * partial rerun artifact cannot be mistaken for a completed target publication.</p>
 *
 * <p>This class owns staging, promotion, and cleanup timing only. Concrete writers still own their
 * own stream open/write/close behavior, format-specific error categorization, and any extra policy
 * such as deleting stale published output when a step completes without producing new content.</p>
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

    /**
     * Reset the handshake state for a new write attempt and ensure the staging location starts
     * empty.
     *
     * <p>This is the first lifecycle step for any staged writer. It clears any leftover
     * {@code .part} file from an earlier run before the delegate stream opens. Writers should call
     * this exactly once for each new output attempt before any bytes are written.</p>
     */
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
            throw new RuntimeEtlException("Failed to prepare staged output path '" + stagingPath + "'.", e);
        }
    }

    /**
     * Promote staged output immediately when no Spring Batch step context is active.
     *
     * <p>This path exists for standalone usage patterns where the writer still needs the same
     * staged-write safety but there is no later {@link #completeStep(ExitStatus)} callback to act
     * as the second publish signal. Step-scoped writers must still wait for the explicit step
     * completion handshake instead of publishing here.</p>
     */
    void promoteIfNoActiveStep() {
        if (hasNoActiveStepContext()) {
            promoteIfPresent();
        }
    }

    /**
     * Record that the writer stream has closed cleanly.
     *
     * <p>If the step outcome has already been signaled, or if no step context exists at all, that
     * means the publish handshake is complete and the staged file can move into its final path.</p>
     *
     * <p>This method intentionally does not try to infer whether a step succeeded; it only records
     * the writer-side signal that streaming has completed without an immediate failure.</p>
     */
    void streamClosed() {
        streamClosed = true;
        if (hasNoActiveStepContext() || stepCompletionSignaled) {
            promoteIfPresent();
        }
    }

    /**
     * Record the enclosing step outcome and either complete or cancel publication.
     *
     * <p>A successful step sets the second publish signal. If the stream has already closed, the
     * staged file is promoted immediately. A failed step never publishes; instead the staged
     * artifact is deleted quietly because it should not survive as an operator-visible final
     * output.</p>
     *
     * <p>Concrete writers may still layer additional step-end behavior around this, but promotion
     * and failed-step staging cleanup should continue to flow through this shared helper.</p>
     */
    ExitStatus completeStep(ExitStatus exitStatus) {
        if (ExitStatus.COMPLETED.getExitCode().equals(exitStatus.getExitCode())) {
            stepCompletionSignaled = true;
            if (streamClosed) {
                promoteIfPresent();
            }
        } else {
            stepCompletionSignaled = false;
            cleanupStagingQuietly();
        }
        return exitStatus;
    }

    /**
     * Delete a previously published final output.
     *
     * <p>This is used by tasklet/single-object flows that may prepare staged output but ultimately
     * decide that nothing should be published for the completed step.</p>
     */
    void deletePublishedOutputIfPresent() {
        try {
            Files.deleteIfExists(finalPath);
        } catch (IOException e) {
            throw new RuntimeEtlException("Failed to delete published output '" + finalPath + "'.", e);
        }
    }

    /**
     * Promote the staged file into the configured final path when a staged artifact is present.
     *
     * <p>Promotion prefers an atomic move when the filesystem supports it and falls back to a
     * normal replace-existing move otherwise.</p>
     */
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
            throw new RuntimeEtlException("Failed to promote staged output '" + stagingPath + "' to final output '" + finalPath + "'.", e);
        }
    }

    /**
     * Best-effort cleanup for staged artifacts that must not be published.
     *
     * <p>This method is intentionally quiet because it is used on already-failed paths where the
     * primary step outcome should remain the main signal. Cleanup problems are still logged for
     * diagnostics.</p>
     */
    private void cleanupStagingQuietly() {
        try {
            Files.deleteIfExists(stagingPath);
        } catch (IOException e) {
            logger.warn("Failed to clean staged output '{}' after step did not complete successfully.", stagingPath, e);
        }
    }

    private static boolean hasNoActiveStepContext() {
        return StepSynchronizationManager.getContext() == null;
    }

    /**
     * Derive the sibling staging path used during the unpublished write phase.
     */
    private static Path stagingPath(Path finalPath) {
        String fileName = finalPath.getFileName().toString();
        return finalPath.resolveSibling(fileName + ".part");
    }
}

