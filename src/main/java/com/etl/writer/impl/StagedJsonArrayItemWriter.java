package com.etl.writer.impl;

import com.etl.exception.RuntimeEtlException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.lang.NonNull;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;

/**
 * JSON writer that streams one staged JSON array and only promotes the file after step success.
 */
public class StagedJsonArrayItemWriter<T> implements ItemStreamWriter<T>, StepExecutionListener {

    private final ObjectMapper objectMapper;
    private final StagedFileLifecycle stagedFileLifecycle;
    private Writer outputWriter;
    private JsonGenerator jsonGenerator;
    private boolean failed;

    public StagedJsonArrayItemWriter(String finalPath, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.stagedFileLifecycle = new StagedFileLifecycle(finalPath);
    }

    @Override
    public void open(@NonNull ExecutionContext executionContext) throws ItemStreamException {
        stagedFileLifecycle.prepareForWrite();
        failed = false;
        try {
            outputWriter = Files.newBufferedWriter(stagedFileLifecycle.stagingPath());
            jsonGenerator = objectMapper.getFactory().createGenerator(outputWriter);
            jsonGenerator.writeStartArray();
        } catch (IOException e) {
            failed = true;
            String message = "Failed to open staged JSON writer for '" + stagedFileLifecycle.finalPath() + "'.";
            ItemStreamException failure = new ItemStreamException(message, runtimeFailure(message, e));
            attachCleanupFailure(failure);
            throw failure;
        }
    }

    @Override
    public void write(@NonNull Chunk<? extends T> chunk) throws Exception {
        if (jsonGenerator == null) {
            failed = true;
            throw new RuntimeEtlException("JSON writer must be opened before write().");
        }
        try {
            for (T item : chunk.getItems()) {
                objectMapper.writeValue(jsonGenerator, item);
            }
            jsonGenerator.flush();
        } catch (IOException e) {
            failed = true;
            String message = "Failed to write staged JSON output for '" + stagedFileLifecycle.finalPath() + "'.";
            throw runtimeFailure(message, e);
        }
    }

    @Override
    public void update(@NonNull ExecutionContext executionContext) throws ItemStreamException {
        if (jsonGenerator != null) {
            try {
                jsonGenerator.flush();
            } catch (IOException e) {
                failed = true;
                String message = "Failed to flush staged JSON writer for '" + stagedFileLifecycle.finalPath() + "'.";
                throw new ItemStreamException(message, runtimeFailure(message, e));
            }
        }
    }

    @Override
    public void close() throws ItemStreamException {
        if (failed) {
            cleanupFailedStreamState();
            return;
        }

        try {
            if (jsonGenerator != null) {
                jsonGenerator.writeEndArray();
                jsonGenerator.close();
            } else if (outputWriter != null) {
                outputWriter.close();
            }
            stagedFileLifecycle.streamClosed();
        } catch (IOException e) {
            failed = true;
            String message = "Failed to close staged JSON writer for '" + stagedFileLifecycle.finalPath() + "'.";
            ItemStreamException failure = new ItemStreamException(message, runtimeFailure(message, e));
            attachCleanupFailure(failure);
            throw failure;
        } finally {
            jsonGenerator = null;
            outputWriter = null;
        }
    }

    private RuntimeEtlException runtimeFailure(String message, IOException cause) {
        return new RuntimeEtlException(message, cause);
    }

    private void cleanupFailedStreamState() {
        IOException cleanupFailure = null;
        try {
            if (jsonGenerator != null) {
                jsonGenerator.close();
            } else if (outputWriter != null) {
                outputWriter.close();
            }
        } catch (IOException e) {
            cleanupFailure = e;
        } finally {
            jsonGenerator = null;
            outputWriter = null;
            try {
                Files.deleteIfExists(stagedFileLifecycle.stagingPath());
            } catch (IOException e) {
                if (cleanupFailure == null) {
                    cleanupFailure = e;
                }
            }
        }
        if (cleanupFailure != null) {
            throw new ItemStreamException(
                    "Failed to clean staged JSON writer state for '" + stagedFileLifecycle.finalPath() + "'.",
                    runtimeFailure("Failed to clean staged JSON writer state for '" + stagedFileLifecycle.finalPath() + "'.", cleanupFailure)
            );
        }
    }

    private void attachCleanupFailure(ItemStreamException failure) {
        try {
            cleanupFailedStreamState();
        } catch (ItemStreamException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        // no-op
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stagedFileLifecycle.completeStep(stepExecution.getExitStatus());
    }
}


