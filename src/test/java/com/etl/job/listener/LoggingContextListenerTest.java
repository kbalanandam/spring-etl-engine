package com.etl.job.listener;

import com.etl.logging.RunLoggingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoggingContextListenerTest {

    private static final String CUSTOMER_LOAD_JOB_CONFIG = resourcePath("config-scenarios/customer-load/job-config.yaml");

    private final JobCompletionNotificationListener jobCompletionNotificationListener = new JobCompletionNotificationListener();
    private final StepLoggingContextListener stepLoggingContextListener = new StepLoggingContextListener();

    @AfterEach
    void tearDown() {
        RunLoggingContext.clearAll();
    }

    @Test
    void jobListenerPopulatesAndClearsJobScopeMdc() {
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("scenario", "customer-load")
                .addString("scenarioLogKey", "2026-04-23/customer-load")
                .addString("runCorrelationId", "20260423-123000-000")
                .addString("runMode", "explicit-job")
                .addString("jobConfigPath", CUSTOMER_LOAD_JOB_CONFIG)
                .toJobParameters();

        JobExecution jobExecution = mock(JobExecution.class);
        JobInstance jobInstance = mock(JobInstance.class);
        when(jobExecution.getJobParameters()).thenReturn(jobParameters);
        when(jobExecution.getJobInstance()).thenReturn(jobInstance);
        when(jobInstance.getJobName()).thenReturn("etlJob");
        when(jobExecution.getId()).thenReturn(42L);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.COMPLETED);
        when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(5));
        when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());

        jobCompletionNotificationListener.beforeJob(jobExecution);

        assertEquals("customer-load", MDC.get(RunLoggingContext.SCENARIO));
        assertEquals("2026-04-23/customer-load", MDC.get(RunLoggingContext.SCENARIO_LOG_KEY));
        assertEquals("20260423-123000-000", MDC.get(RunLoggingContext.RUN_CORRELATION_ID));
        assertEquals("42", MDC.get(RunLoggingContext.JOB_EXECUTION_ID));
        assertEquals("etlJob", MDC.get(RunLoggingContext.JOB_NAME));

        jobCompletionNotificationListener.afterJob(jobExecution);

        assertEquals("customer-load", MDC.get(RunLoggingContext.SCENARIO));
        assertEquals("2026-04-23/customer-load", MDC.get(RunLoggingContext.SCENARIO_LOG_KEY));
        assertEquals("20260423-123000-000", MDC.get(RunLoggingContext.RUN_CORRELATION_ID));
        assertNull(MDC.get(RunLoggingContext.JOB_EXECUTION_ID));
        assertNull(MDC.get(RunLoggingContext.JOB_NAME));
    }

    @Test
    void stepListenerAddsAndClearsStepNameFromMdc() {
        StepExecution stepExecution = mock(StepExecution.class);
        when(stepExecution.getStepName()).thenReturn("etlStep_0_Customers");
        when(stepExecution.getExitStatus()).thenReturn(org.springframework.batch.core.ExitStatus.COMPLETED);

        stepLoggingContextListener.beforeStep(stepExecution);
        assertEquals("etlStep_0_Customers", MDC.get(RunLoggingContext.STEP_NAME));

        stepLoggingContextListener.afterStep(stepExecution);
        assertNull(MDC.get(RunLoggingContext.STEP_NAME));
    }

    @Test
    void failedJobKeepsScenarioContextWhileLoggingFailureDetails() {
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("scenario", "department-load")
                .addString("scenarioLogKey", "2026-04-23/department-load")
                .addString("runCorrelationId", "20260423-124500-000")
                .toJobParameters();

        JobExecution jobExecution = mock(JobExecution.class);
        JobInstance jobInstance = mock(JobInstance.class);
        when(jobExecution.getJobParameters()).thenReturn(jobParameters);
        when(jobExecution.getJobInstance()).thenReturn(jobInstance);
        when(jobInstance.getJobName()).thenReturn("etlJob");
        when(jobExecution.getId()).thenReturn(77L);
        when(jobExecution.getStatus()).thenReturn(BatchStatus.FAILED);
        when(jobExecution.getStartTime()).thenReturn(LocalDateTime.now().minusSeconds(3));
        when(jobExecution.getEndTime()).thenReturn(LocalDateTime.now());
        when(jobExecution.getAllFailureExceptions()).thenReturn(List.of(new IllegalStateException("boom")));

        jobCompletionNotificationListener.beforeJob(jobExecution);
        jobCompletionNotificationListener.afterJob(jobExecution);

        assertEquals("department-load", MDC.get(RunLoggingContext.SCENARIO));
        assertEquals("2026-04-23/department-load", MDC.get(RunLoggingContext.SCENARIO_LOG_KEY));
        assertNull(MDC.get(RunLoggingContext.JOB_EXECUTION_ID));
    }

    private static String resourcePath(String resourceName) {
        try {
            return Path.of(Objects.requireNonNull(
                    LoggingContextListenerTest.class.getClassLoader().getResource(resourceName),
                    () -> "Missing test resource: " + resourceName
            ).toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to resolve test resource: " + resourceName, e);
        }
    }
}



