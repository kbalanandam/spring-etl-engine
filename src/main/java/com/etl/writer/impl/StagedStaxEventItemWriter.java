package com.etl.writer.impl;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.xml.StaxEventItemWriter;
import org.springframework.core.io.FileSystemResource;

/**
 * XML writer that stages output until the step completes successfully.
 */
public class StagedStaxEventItemWriter<T> extends StaxEventItemWriter<T> implements StepExecutionListener {

    private final StagedFileLifecycle stagedFileLifecycle;

    public StagedStaxEventItemWriter(String finalPath) {
        this.stagedFileLifecycle = new StagedFileLifecycle(finalPath);
        setResource(new FileSystemResource(stagedFileLifecycle.stagingPath()));
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        // no-op
    }

    @Override
    public void open(ExecutionContext executionContext) {
        stagedFileLifecycle.prepareForWrite();
        super.open(executionContext);
    }

    @Override
    public void close() {
        super.close();
        stagedFileLifecycle.streamClosed();
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        return stagedFileLifecycle.completeStep(stepExecution.getExitStatus());
    }
}

