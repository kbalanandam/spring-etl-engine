package com.etl.job.listener;

import com.etl.logging.RunLoggingContext;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

@Component
public class StepLoggingContextListener implements StepExecutionListener {

  private static final Logger logger = LoggerFactory.getLogger(StepLoggingContextListener.class);

    @Override
    public void beforeStep(StepExecution stepExecution) {
        RunLoggingContext.put(RunLoggingContext.STEP_NAME, stepExecution.getStepName());
    logger.info("STEP_EVENT event=step_started stepName={} stepExecutionId={}", stepExecution.getStepName(), stepExecution.getId());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
	logger.info("STEP_EVENT event=step_finished stepName={} stepExecutionId={} status={} readCount={} writeCount={} filterCount={} skipCount={} rollbackCount={} rejectedCount={} rejectOutputPath={} archivedSourcePath={}",
        stepExecution.getStepName(),
        stepExecution.getId(),
        stepExecution.getExitStatus().getExitCode(),
        stepExecution.getReadCount(),
        stepExecution.getWriteCount(),
        stepExecution.getFilterCount(),
        stepExecution.getSkipCount(),
		stepExecution.getRollbackCount(),
		stepExecution.getExecutionContext().getInt(FileIngestionRuntimeSupport.REJECTED_COUNT_KEY, 0),
		stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.REJECT_OUTPUT_PATH_KEY, ""),
		stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.ARCHIVED_SOURCE_PATH_KEY, ""));
        RunLoggingContext.clearStepScope();
        return stepExecution.getExitStatus();
    }
}

