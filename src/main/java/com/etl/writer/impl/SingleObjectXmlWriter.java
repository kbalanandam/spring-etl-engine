package com.etl.writer.impl;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemWriter;
import org.springframework.lang.NonNull;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * XML writer for wrapper/root-object output.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This writer is used when the runtime already has a single fully assembled
 * object graph representing the final XML document. Typical examples are tasklet
 * flows or wrapper-based XML writes where one root object contains the collection
 * of generated record objects.</p>
 *
 * <p>The writer publishes through {@link StagedFileLifecycle} so output is first
 * written to a staging file and only promoted to the final file when the runtime
 * can safely consider the write complete. That keeps failed or incomplete XML
 * writes from appearing as valid published output.</p>
 *
 * <p>Unlike chunk-oriented XML record writers, this writer treats the chunk as a transport wrapper
 * around one logical XML document object. The first item is marshalled as the full output document;
 * callers are therefore expected to supply exactly one wrapper/root object for each completed
 * write.</p>
 */
public class SingleObjectXmlWriter implements ItemWriter<Object>, ItemStream, StepExecutionListener {
    private final Jaxb2Marshaller marshaller;
    private final StagedFileLifecycle stagedFileLifecycle;
    private boolean prepared;
    private boolean writeInvoked;

    public SingleObjectXmlWriter(Jaxb2Marshaller marshaller, String filePath) {
        this(marshaller, filePath, false);
    }

    public SingleObjectXmlWriter(Jaxb2Marshaller marshaller, String filePath, boolean packageAsZip) {
        this.marshaller = marshaller;
        this.stagedFileLifecycle = new StagedFileLifecycle(filePath, packageAsZip, ".xml");
    }

    /**
     * Marshals the first wrapper object in the chunk to the staged output file.
     *
     * <p>This writer expects a single wrapper/root object per invocation. After a
     * successful marshal, standalone usage may promote the file immediately, while
     * step-scoped usage waits for the step lifecycle to decide whether the staged
     * file should be published or cleaned up. Empty chunks are ignored because they
     * do not represent a new XML document to publish.</p>
     */
    @Override
    public void write(@NonNull Chunk<?> chunk) throws Exception {
        if (chunk.isEmpty()) {
            return;
        }
        prepareIfNeeded();
        writeInvoked = true;
        Object wrapper = chunk.getItems().get(0); // Only one wrapper object expected
        try (OutputStream os = Files.newOutputStream(
                stagedFileLifecycle.stagingPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            marshaller.marshal(wrapper, new StreamResult(os));
        }
        stagedFileLifecycle.promoteIfNoActiveStep();
    }

    /**
     * Prepares the staged output handshake before step-scoped streaming or writing begins.
     *
     * <p>This keeps wrapper-mode XML writes aligned with the shared staged-file contract even when
     * the first actual marshal happens later in the step lifecycle.</p>
     */
    @Override
    public void open(@NonNull ExecutionContext executionContext) {
        prepareIfNeeded();
    }

    @Override
    public void update(@NonNull ExecutionContext executionContext) {
        // no-op
    }

    @Override
    public void close() {
        // Closing the stream is one half of the staged publish handshake. The final promotion may
        // still wait for afterStep when this writer is running inside an active step context.
        stagedFileLifecycle.streamClosed();
    }

    @Override
    public void beforeStep(@NonNull StepExecution stepExecution) {
        // no-op
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        ExitStatus exitStatus = stepExecution.getExitStatus();
        if (ExitStatus.COMPLETED.getExitCode().equals(exitStatus.getExitCode()) && !writeInvoked) {
            // If the step completed without emitting any wrapper object, remove any
            // previously published file rather than leaving stale XML behind as if
            // fresh output had been produced.
            stagedFileLifecycle.deletePublishedOutputIfPresent();
            return exitStatus;
        }
        // For successful writes, publication happens only after the staged lifecycle has both the
        // stream-closed signal and the final step outcome. Failed steps clean staging instead.
        return stagedFileLifecycle.completeStep(exitStatus);
    }

    /**
     * Lazily initializes staged output once for the writer lifecycle.
     *
     * <p>Both {@link #open(ExecutionContext)} and {@link #write(Chunk)} may reach
     * this path, so initialization is guarded to keep the staging handshake
     * idempotent. Reaching this method multiple times for the same writer instance must not reset
     * publication state mid-write or recreate staging unexpectedly.</p>
     */
    private void prepareIfNeeded() {
        if (prepared) {
            return;
        }
        stagedFileLifecycle.prepareForWrite();
        prepared = true;
    }
}
