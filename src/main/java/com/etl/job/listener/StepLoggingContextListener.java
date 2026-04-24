package com.etl.job.listener;

import com.etl.logging.RunLoggingContext;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

@Component
public class StepLoggingContextListener implements StepExecutionListener {

    @Override
    public void beforeStep(StepExecution stepExecution) {
        RunLoggingContext.put(RunLoggingContext.STEP_NAME, stepExecution.getStepName());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        RunLoggingContext.clearStepScope();
        return stepExecution.getExitStatus();
    }
}

