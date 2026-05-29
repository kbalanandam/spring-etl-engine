package com.etl.writer.impl;

import com.etl.exception.TargetWriteException;
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
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This writer owns the JSON-array publication contract used by the product's JSON
 * target path. The output file is opened as a single array, each chunk appends array
 * elements, and the staged file is promoted only after the write stream closes cleanly
 * and the enclosing step completes successfully.</p>
 *
 * <p>Like the other staged file writers, any failure moves the writer into cleanup mode
 * so partial JSON is deleted instead of being left behind as a published artifact.</p>
 *
 * <p>This class owns JSON-array stream construction and failure handling. Final-path promotion and
 * publish timing remain delegated to {@link StagedFileLifecycle} so JSON participates in the same
 * two-signal staged publication contract as CSV and XML file targets.</p>
 */
public class StagedJsonArrayItemWriter<T> implements ItemStreamWriter<T>, StepExecutionListener {

    private final ObjectMapper objectMapper;
    private final StagedFileLifecycle stagedFileLifecycle;
    private Writer outputWriter;
    private JsonGenerator jsonGenerator;
    private boolean failed;

    public StagedJsonArrayItemWriter(String finalPath, ObjectMapper objectMapper) {
        this(finalPath, objectMapper, false);
    }

    public StagedJsonArrayItemWriter(String finalPath, ObjectMapper objectMapper, boolean packageAsZip) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.stagedFileLifecycle = new StagedFileLifecycle(finalPath, packageAsZip, ".json");
    }

    @Override
    public void open(@NonNull ExecutionContext executionContext) throws ItemStreamException {
        // Start a fresh staged JSON document for this write attempt before any array bytes are
        // emitted so the published target path is never written directly.
        stagedFileLifecycle.prepareForWrite();
        failed = false;
        try {
            outputWriter = Files.newBufferedWriter(stagedFileLifecycle.stagingPath());
            jsonGenerator = objectMapper.getFactory().createGenerator(outputWriter);
            // The product publishes JSON targets as one array per step run.
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
            throw new TargetWriteException("JSON writer must be opened before write().");
        }
        try {
            // Each item becomes one element in the staged JSON array while the outer array remains
            // open across chunk boundaries for the duration of the step.
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
            // Failed JSON streams should skip normal publication and clean up partial staged output.
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

    private TargetWriteException runtimeFailure(String message, IOException cause) {
        return new TargetWriteException(message, cause);
    }

    private void cleanupFailedStreamState() {
        // Best-effort cleanup for failed JSON array streams: close any open generator/writer and
        // remove the staged file so incomplete JSON never survives as operator-visible output.
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
        stagedFileLifecycle.cleanupOrphanedArtifactsAtStepStart();
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        // Step completion is the second signal in the staged publish handshake for JSON output.
        return stagedFileLifecycle.completeStep(stepExecution.getExitStatus());
    }
}


