package com.etl.step.impl;

import com.etl.config.job.JobConfig;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditNoopCustomStepProviderTest {

    private final AuditNoopCustomStepProvider provider = new AuditNoopCustomStepProvider();

    @Test
    void createHandlerSetsExitStatusWhenResultCodeConfigured() throws Exception {
        JobConfig.CustomStepConfig customConfig = new JobConfig.CustomStepConfig();
        customConfig.setConfig(Map.of("resultCode", "verify_failed"));

        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        StepContribution contribution = new StepContribution(stepExecution);

        RepeatStatus status = provider.createHandler(customConfig).execute(contribution, null);

        assertEquals(RepeatStatus.FINISHED, status);
        assertEquals("verify_failed", contribution.getExitStatus().getExitCode());
    }

    @Test
    void createHandlerLeavesExistingExitStatusWhenResultCodeMissing() throws Exception {
        JobConfig.CustomStepConfig customConfig = new JobConfig.CustomStepConfig();

        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        StepContribution contribution = new StepContribution(stepExecution);
        contribution.setExitStatus(ExitStatus.COMPLETED);

        RepeatStatus status = provider.createHandler(customConfig).execute(contribution, null);

        assertEquals(RepeatStatus.FINISHED, status);
        assertEquals(ExitStatus.COMPLETED.getExitCode(), contribution.getExitStatus().getExitCode());
    }
}

