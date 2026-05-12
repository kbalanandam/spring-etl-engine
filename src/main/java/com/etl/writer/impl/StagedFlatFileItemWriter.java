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
 * Flat file writer that stages output until the step completes successfully.
 */
public class StagedFlatFileItemWriter<T> extends FlatFileItemWriter<T> implements StepExecutionListener {

    private final StagedFileLifecycle stagedFileLifecycle;
    private boolean failed;

    public StagedFlatFileItemWriter(String finalPath) {
        this.stagedFileLifecycle = new StagedFileLifecycle(finalPath);
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
        return stagedFileLifecycle.completeStep(stepExecution.getExitStatus());
    }

  private RuntimeEtlException runtimeFailure(String message, Throwable cause) {
    return new RuntimeEtlException(message, cause);
  }

  private void cleanupFailedStreamState() {
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

