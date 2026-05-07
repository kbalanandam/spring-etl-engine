package com.etl.writer.impl;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemWriter;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

public class SingleObjectXmlWriter implements ItemWriter<Object>, ItemStream, StepExecutionListener {
    private final Jaxb2Marshaller marshaller;
    private final StagedFileLifecycle stagedFileLifecycle;
    private boolean prepared;
    private boolean writeInvoked;

    public SingleObjectXmlWriter(Jaxb2Marshaller marshaller, String filePath) {
        this.marshaller = marshaller;
        this.stagedFileLifecycle = new StagedFileLifecycle(filePath);
    }

    @Override
    public void write(Chunk<?> chunk) throws Exception {
        if (chunk == null || chunk.isEmpty()) return;
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

    @Override
    public void open(ExecutionContext executionContext) {
        prepareIfNeeded();
    }

    @Override
    public void update(ExecutionContext executionContext) {
        // no-op
    }

    @Override
    public void close() {
        stagedFileLifecycle.streamClosed();
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // no-op
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        ExitStatus exitStatus = stepExecution.getExitStatus();
        if (ExitStatus.COMPLETED.getExitCode().equals(exitStatus.getExitCode()) && !writeInvoked) {
            stagedFileLifecycle.deletePublishedOutputIfPresent();
            return exitStatus;
        }
        return stagedFileLifecycle.completeStep(exitStatus);
    }

    private void prepareIfNeeded() {
        if (prepared) {
            return;
        }
        stagedFileLifecycle.prepareForWrite();
        prepared = true;
    }
}
