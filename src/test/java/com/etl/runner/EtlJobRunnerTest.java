package com.etl.runner;

import com.etl.config.RunConfigurationMetadata;
import com.etl.logging.RunLoggingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

import java.net.URISyntaxException;
import java.time.LocalDate;
import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EtlJobRunnerTest {

    private static final String CUSTOMER_LOAD_JOB_CONFIG = customerLoadJobConfigPath();

    @AfterEach
    void tearDown() {
        RunLoggingContext.clearAll();
    }

    @Test
    void runUsesSanitizedScenarioNameInDailyLogKey() throws Exception {
        JobLauncher jobLauncher = mock(JobLauncher.class);
        Job etlJob = mock(Job.class);
        JobExecution jobExecution = mock(JobExecution.class);
        org.mockito.ArgumentCaptor<JobParameters> parametersCaptor = org.mockito.ArgumentCaptor.forClass(JobParameters.class);
        when(jobLauncher.run(eq(etlJob), any(JobParameters.class))).thenReturn(jobExecution);

        RunConfigurationMetadata metadata = new RunConfigurationMetadata(
                "Customer Load",
                CUSTOMER_LOAD_JOB_CONFIG,
                false
        );

        EtlJobRunner runner = new EtlJobRunner(jobLauncher, etlJob, metadata);
        runner.run();

        verify(jobLauncher).run(eq(etlJob), parametersCaptor.capture());
        JobParameters jobParameters = parametersCaptor.getValue();

        assertEquals("Customer Load", jobParameters.getString("scenario"));
        assertEquals(LocalDate.now() + "/Customer_Load", jobParameters.getString("scenarioLogKey"));
        assertEquals("explicit-job", jobParameters.getString("runMode"));
        assertNull(MDC.get(RunLoggingContext.SCENARIO));
        assertNull(MDC.get(RunLoggingContext.SCENARIO_LOG_KEY));
        assertNull(MDC.get(RunLoggingContext.RUN_CORRELATION_ID));
    }

    private static String customerLoadJobConfigPath() {
        String resourceName = "config-scenarios/customer-load/job-config.yaml";
        try {
            return Path.of(Objects.requireNonNull(
                    EtlJobRunnerTest.class.getClassLoader().getResource(resourceName),
                    () -> "Missing test resource: " + resourceName
            ).toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Failed to resolve test resource: " + resourceName, e);
        }
    }
}



