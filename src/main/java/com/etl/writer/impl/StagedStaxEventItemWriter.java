package com.etl.writer.impl;

import com.etl.exception.TargetWriteException;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.xml.StaxEventItemWriter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Chunk-oriented XML writer that stages output until the step completes successfully.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This writer extends Spring Batch's {@link StaxEventItemWriter} for flows that
 * emit individual XML record objects one item at a time. Records are streamed to a
 * staging file first, then promoted to the final output only after the surrounding
 * step reports a successful completion status.</p>
 *
 * <p>The class also tracks writer failure state explicitly. Once a streaming write,
 * update, or close operation fails, subsequent lifecycle handling switches to cleanup
 * behavior so partial XML output is removed instead of being exposed as a valid file.</p>
 *
 * <p>This class owns XML streaming and writer-failure categorization. The actual staged-file
 * promotion handshake remains delegated to {@link StagedFileLifecycle} so chunk-mode and
 * wrapper-mode XML writers publish through the same final-path contract.</p>
 */
public class StagedStaxEventItemWriter<T> extends StaxEventItemWriter<T> implements StepExecutionListener {

    private final StagedFileLifecycle stagedFileLifecycle;
    private boolean failed;

    public StagedStaxEventItemWriter(String finalPath) {
        this(finalPath, false);
    }

    public StagedStaxEventItemWriter(String finalPath, boolean packageAsZip) {
        this.stagedFileLifecycle = new StagedFileLifecycle(finalPath, packageAsZip, ".xml");
        setResource(new FileSystemResource(stagedFileLifecycle.stagingPath()));
    }

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        stagedFileLifecycle.cleanupOrphanedArtifactsAtStepStart();
    }

    @Override
    public void open(@NonNull ExecutionContext executionContext) {
        // Always prepare a fresh staging path before the parent XML writer opens its stream so the
        // inherited StAX writer never targets the published file directly.
        stagedFileLifecycle.prepareForWrite();
        failed = false;
        try {
            super.open(executionContext);
        } catch (Exception e) {
            failed = true;
            String message = "Failed to open staged XML writer for '" + stagedFileLifecycle.finalPath() + "'.";
            ItemStreamException failure = new ItemStreamException(message, runtimeFailure(message, e));
            attachCleanupFailure(failure);
            throw failure;
        }
    }

  @Override
  public void write(@NonNull Chunk<? extends T> chunk) {
    if (failed) {
      throw new TargetWriteException("Staged XML writer for '" + stagedFileLifecycle.finalPath() + "' is already in a failed state.");
    }
    try {
      // Delegate record-by-record XML streaming to the parent writer while this wrapper preserves
      // staged publication boundaries and XML-specific runtime failure messaging.
      super.write(chunk);
    } catch (Exception e) {
      throw writeFailure(e);
    }
  }

  @Override
  public void update(@NonNull ExecutionContext executionContext) throws ItemStreamException {
    if (failed) {
      return;
    }
    try {
      super.update(executionContext);
    } catch (ItemStreamException e) {
      failed = true;
      String message = "Failed to update staged XML writer for '" + stagedFileLifecycle.finalPath() + "'.";
      throw new ItemStreamException(message, runtimeFailure(message, e));
    }
    }

    @Override
    public void close() {
    if (failed) {
      // When the writer has already failed, do not attempt a normal staged-to-final lifecycle.
      // Clean up the partial stream state instead.
      cleanupFailedStreamState();
      return;
    }

    try {
      super.close();
      stagedFileLifecycle.streamClosed();
    } catch (Exception e) {
      failed = true;
      String message = "Failed to close staged XML writer for '" + stagedFileLifecycle.finalPath() + "'.";
      ItemStreamException failure = new ItemStreamException(message, runtimeFailure(message, e));
      attachCleanupFailure(failure);
      throw failure;
    }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        // Step completion is the second signal in the staged publish handshake. A successfully
        // closed staged XML file becomes publishable only here when the step also completed.
        return stagedFileLifecycle.completeStep(stepExecution.getExitStatus());
    }

  private TargetWriteException runtimeFailure(String message, Throwable cause) {
    return new TargetWriteException(message, cause);
  }

  private TargetWriteException writeFailure(Throwable cause) {
    failed = true;
    String message = "Failed to write staged XML output for '" + stagedFileLifecycle.finalPath() + "'.";
    return runtimeFailure(message, cause);
  }

  private void cleanupFailedStreamState() {
    // Best-effort cleanup for failed streaming writes: close any underlying XML resources and
    // remove the staging file so partial output does not survive the failed writer path.
    Exception cleanupFailure = null;
    try {
      super.close();
    } catch (Exception e) {
      cleanupFailure = e;
    } finally {
      try {
        Files.deleteIfExists(stagedFileLifecycle.stagingPath());
      } catch (IOException e) {
        if (cleanupFailure == null) {
          cleanupFailure = e;
        }
      }
    }
    if (cleanupFailure != null) {
      String message = "Failed to clean staged XML writer state for '" + stagedFileLifecycle.finalPath() + "'.";
      throw new ItemStreamException(message, runtimeFailure(message, cleanupFailure));
    }
  }

  private void attachCleanupFailure(ItemStreamException failure) {
    try {
      cleanupFailedStreamState();
    } catch (ItemStreamException cleanupFailure) {
      failure.addSuppressed(cleanupFailure);
    }
  }
}

