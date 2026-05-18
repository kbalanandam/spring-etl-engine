package com.etl.writer.impl;

import com.etl.exception.RuntimeEtlException;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.nio.file.Files;

/**
 * Chunk-oriented CSV writer that stages output until the step completes successfully.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This writer extends Spring Batch's {@link FlatFileItemWriter} for CSV targets that
 * publish row-oriented output one item at a time. Rows are written to a sibling staging
 * file first and only promoted to the final output once the surrounding step completes
 * successfully.</p>
 *
 * <p>When a stream operation fails, the writer enters a failed state and switches to cleanup
 * behavior so partial CSV output is removed instead of being exposed as a published file.</p>
 *
 * <p>This wrapper owns CSV stream lifecycle and failure categorization. The actual publish timing
 * and final-path promotion handshake stay delegated to {@link StagedFileLifecycle} so CSV follows
 * the same staged publication rules as JSON and XML file writers.</p>
 */
public class StagedFlatFileItemWriter<T> extends FlatFileItemWriter<T> implements StepExecutionListener {

    private final StagedFileLifecycle stagedFileLifecycle;
    private boolean failed;

    public StagedFlatFileItemWriter(String finalPath) {
        this(finalPath, false);
    }

    public StagedFlatFileItemWriter(String finalPath, boolean packageAsZip) {
        this.stagedFileLifecycle = new StagedFileLifecycle(finalPath, packageAsZip, ".csv");
        setResource(new FileSystemResource(stagedFileLifecycle.stagingPath()));
        setShouldDeleteIfExists(true);
        setAppendAllowed(false);
    }

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        // no-op
    }

    @Override
    public void open(@NonNull ExecutionContext executionContext) {
        // Prepare a fresh staging path before the underlying flat-file stream opens so the
        // inherited writer never writes directly into the published destination.
        stagedFileLifecycle.prepareForWrite();
        failed = false;
        try {
            super.open(executionContext);
        } catch (Exception e) {
            failed = true;
            String message = "Failed to open staged CSV writer for '" + stagedFileLifecycle.finalPath() + "'.";
            ItemStreamException failure = new ItemStreamException(message, runtimeFailure(message, e));
            attachCleanupFailure(failure);
            throw failure;
        }
    }

    @Override
    public void write(@NonNull Chunk<? extends T> chunk) throws Exception {
        if (failed) {
            throw new RuntimeEtlException("Staged CSV writer for '" + stagedFileLifecycle.finalPath() + "' is already in a failed state.");
        }
        try {
            // Delegate line aggregation and CSV serialization to the parent writer while this
            // wrapper preserves staged-publication boundaries and CSV-specific failure handling.
            super.write(chunk);
        } catch (Exception e) {
            failed = true;
            String message = "Failed to write staged CSV output for '" + stagedFileLifecycle.finalPath() + "'.";
            throw runtimeFailure(message, e);
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
			String message = "Failed to update staged CSV writer for '" + stagedFileLifecycle.finalPath() + "'.";
			throw new ItemStreamException(message, runtimeFailure(message, e));
		}
    }

    @Override
    public void close() {
        if (failed) {
            // Failed streams should never reach normal staged publication. Clean up the partial
            // staging state instead.
            cleanupFailedStreamState();
            return;
        }

        try {
            super.close();
            stagedFileLifecycle.streamClosed();
        } catch (Exception e) {
            failed = true;
            String message = "Failed to close staged CSV writer for '" + stagedFileLifecycle.finalPath() + "'.";
            ItemStreamException failure = new ItemStreamException(message, runtimeFailure(message, e));
            attachCleanupFailure(failure);
            throw failure;
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        // Step completion is the second signal in the staged publish handshake for CSV output.
        return stagedFileLifecycle.completeStep(stepExecution.getExitStatus());
    }

  private RuntimeEtlException runtimeFailure(String message, Throwable cause) {
    return new RuntimeEtlException(message, cause);
  }

  private void cleanupFailedStreamState() {
    // Best-effort cleanup for failed CSV stream state so unfinished staged output does not survive.
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
      String message = "Failed to clean staged CSV writer state for '" + stagedFileLifecycle.finalPath() + "'.";
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

