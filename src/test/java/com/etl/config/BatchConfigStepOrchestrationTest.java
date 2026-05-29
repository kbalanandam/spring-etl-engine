package com.etl.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.etl.config.exception.ConfigException;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.config.target.XmlTargetConfig;
import com.etl.exception.RuntimeEtlException;
import com.etl.job.listener.JobCompletionNotificationListener;
import com.etl.job.listener.StepLoggingContextListener;
import com.etl.processor.DynamicProcessorFactory;
import com.etl.reader.DynamicReaderFactory;
import com.etl.runtime.DuplicateResolverFactory;
import com.etl.runtime.FileIngestionRuntimeSupport;
import com.etl.runtime.job.JobConfigPaths;
import com.etl.runtime.job.JobRecoveryPolicy;
import com.etl.runtime.job.JobRunMode;
import com.etl.runtime.job.JobRuntimeDescriptor;
import com.etl.runtime.job.JobRuntimeDescriptorAssembler;
import com.etl.writer.DynamicWriterFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchConfigStepOrchestrationTest {

  private final Logger batchConfigLogger = (Logger) LoggerFactory.getLogger(BatchConfig.class);

    @TempDir
    Path tempDir;

  @AfterEach
  void tearDown() {
    batchConfigLogger.detachAndStopAllAppenders();
  }

    @Test
    void buildStepsUsesExplicitStepOrderInsteadOfSourceTargetPosition() throws Exception {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(
                csvSource("Customers", tempCsv("customers.csv")),
                csvSource("Department", tempCsv("departments.csv"))
        ));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(
                xmlTarget("Customers", "Customer"),
                xmlTarget("Departments", "Department")
        ));

        ProcessorConfig processorConfig = processorConfig(
                mapping("Customers", "Customers"),
                mapping("Department", "Departments")
        );
      JobRuntimeDescriptor jobRuntimeDescriptor = jobRuntimeDescriptor(
        "cust-dept-load",
        sourceWrapper,
        targetWrapper,
        processorConfig,
        List.of(
            step("departments-step", "Department", "Departments"),
            step("customers-step", "Customers", "Customers")
        )
    );
    ListAppender<ILoggingEvent> appender = attachAppender();

        BatchConfig batchConfig = new BatchConfig(
                sourceWrapper,
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig,
                targetWrapper,
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "cust-dept-load",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "cust-dept-load-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of(
                                step("departments-step", "Department", "Departments"),
                                step("customers-step", "Customers", "Customers")
                        )
                        ),
            jobRuntimeDescriptor,
                        new FileIngestionRuntimeSupport(),
                        new DuplicateResolverFactory()
        );

        List<Step> steps = batchConfig.buildSteps();

        assertEquals(List.of("departments-step", "customers-step"), steps.stream().map(Step::getName).toList());
    assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_PLAN event=step_plan")
        && event.getFormattedMessage().contains("stepName=customers-step")
        && event.getFormattedMessage().contains("subFlow=customers-step-subflow")
        && event.getFormattedMessage().contains("dependsOnSubFlows=departments-step-subflow")
        && event.getFormattedMessage().contains("upstreamSteps=departments-step")));
    }

    @Test
    void buildStepsFailsFastWhenProcessorMappingIsMissingForConfiguredStep() throws Exception {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(csvSource("Customers", tempCsv("customers.csv"))));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(xmlTarget("Customers", "Customer")));

        ProcessorConfig processorConfig = processorConfig(mapping("OtherSource", "Customers"));

        BatchConfig batchConfig = new BatchConfig(
                sourceWrapper,
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig,
                targetWrapper,
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customer-load",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "customer-load-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of(step("customers-step", "Customers", "Customers"))
                        ),
                        new FileIngestionRuntimeSupport(),
                        new DuplicateResolverFactory()
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, batchConfig::buildSteps);
        assertEquals("Step 'customers-step' does not have a matching processor mapping for source 'Customers' and target 'Customers'.", exception.getMessage());
    }

    @Test
    void buildStepsEmitsDuplicateResolverPlanEvidenceForOrderedDuplicateSelection() throws Exception {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(csvSource("Customers", tempCsv("customers.csv"))));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(xmlTarget("Customers", "Customer")));

        ProcessorConfig processorConfig = processorConfig(mappingWithOrderedDuplicateRule("Customers", "Customers"));
        ListAppender<ILoggingEvent> appender = attachAppender();

        BatchConfig batchConfig = new BatchConfig(
                sourceWrapper,
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig,
                targetWrapper,
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customers-ordered-duplicate",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "customers-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of(step("customers-step", "Customers", "Customers"))
                ),
                new FileIngestionRuntimeSupport(),
                new DuplicateResolverFactory()
        );
        ReflectionTestUtils.setField(batchConfig, "chunkThreshold", 10000);

        List<Step> steps = batchConfig.buildSteps();

        assertEquals(List.of("customers-step"), steps.stream().map(Step::getName).toList());
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_READY event=duplicate_resolver_plan")
                && event.getFormattedMessage().contains("stepName=customers-step")
                && event.getFormattedMessage().contains("duplicateIdentityMode=flatMapped")
                && event.getFormattedMessage().contains("resolverMode=inMemory")
                && event.getFormattedMessage().contains("resolverReason=record_count_within_chunk_threshold")));
    }

    @Test
    void buildStepsOverridesTaskletToChunkWhenSkipPolicyIsEnabled() throws Exception {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(csvSource("Customers", tempCsv("customers-small.csv"))));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(xmlTarget("Customers", "Customer")));

        ProcessorConfig processorConfig = processorConfig(mapping("Customers", "Customers"));
        ListAppender<ILoggingEvent> appender = attachAppender();

        BatchConfig batchConfig = new BatchConfig(
                sourceWrapper,
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig,
                targetWrapper,
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customers-skip-policy-tasklet",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "customers-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of(stepWithSkipPolicy("customers-step", "Customers", "Customers", 3,
                                List.of("runtime"),
                                List.of()))
                ),
                new FileIngestionRuntimeSupport(),
                new DuplicateResolverFactory()
        );
        ReflectionTestUtils.setField(batchConfig, "chunkThreshold", 10000);

        List<Step> steps = batchConfig.buildSteps();

        assertEquals(List.of("customers-step"), steps.stream().map(Step::getName).toList());
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_READY event=step_mode_override")
                && event.getFormattedMessage().contains("originalMode=tasklet")
                && event.getFormattedMessage().contains("overriddenMode=chunk")
                && event.getFormattedMessage().contains("reason=skip-policy-requires-fault-tolerant-chunk")));
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_READY event=skip_policy_enabled")
                && event.getFormattedMessage().contains("stepName=customers-step")
                && event.getFormattedMessage().contains("skippableCategories=[runtime]")
                && event.getFormattedMessage().contains("skipLimit=3")));
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_READY event=step_ready")
                && event.getFormattedMessage().contains("mode=chunk")
                && event.getFormattedMessage().contains("stepName=customers-step")));
    }

    @Test
    void buildStepsFailsFastWhenSkipPolicyIsCombinedWithOrderedDuplicateWinnerSelection() throws Exception {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(csvSource("Customers", tempCsv("customers-small.csv"))));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(xmlTarget("Customers", "Customer")));

        ProcessorConfig processorConfig = processorConfig(mappingWithOrderedDuplicateRule("Customers", "Customers"));

        BatchConfig batchConfig = new BatchConfig(
                sourceWrapper,
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig,
                targetWrapper,
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customers-skip-policy-duplicate-conflict",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "customers-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of(stepWithSkipPolicy("customers-step", "Customers", "Customers", 3,
                                List.of("runtime"),
                                List.of()))
                ),
                new FileIngestionRuntimeSupport(),
                new DuplicateResolverFactory()
        );
        ReflectionTestUtils.setField(batchConfig, "chunkThreshold", 10000);

        IllegalStateException exception = assertThrows(IllegalStateException.class, batchConfig::buildSteps);
        assertTrue(exception.getMessage().contains("both ordered duplicate winner selection and skipPolicy"));
    }

    @Test
    void buildStepsOverridesTaskletToChunkWhenRetryPolicyIsEnabled() throws Exception {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(csvSource("Customers", tempCsv("customers-small.csv"))));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(xmlTarget("Customers", "Customer")));

        ProcessorConfig processorConfig = processorConfig(mapping("Customers", "Customers"));
        ListAppender<ILoggingEvent> appender = attachAppender();

        BatchConfig batchConfig = new BatchConfig(
                sourceWrapper,
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig,
                targetWrapper,
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customers-retry-policy-tasklet",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "customers-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of(stepWithRetryPolicy("customers-step", "Customers", "Customers", 3, 250L,
                                List.of("runtime"),
                                List.of()))
                ),
                new FileIngestionRuntimeSupport(),
                new DuplicateResolverFactory()
        );
        ReflectionTestUtils.setField(batchConfig, "chunkThreshold", 10000);

        List<Step> steps = batchConfig.buildSteps();

        assertEquals(List.of("customers-step"), steps.stream().map(Step::getName).toList());
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_READY event=step_mode_override")
                && event.getFormattedMessage().contains("originalMode=tasklet")
                && event.getFormattedMessage().contains("overriddenMode=chunk")
                && event.getFormattedMessage().contains("reason=retry-policy-requires-fault-tolerant-chunk")));
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_READY event=retry_policy_enabled")
                && event.getFormattedMessage().contains("stepName=customers-step")
                && event.getFormattedMessage().contains("maxAttempts=3")
                && event.getFormattedMessage().contains("backoffMs=250")
                && event.getFormattedMessage().contains("retryableCategories=[runtime]")));
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_READY event=step_ready")
                && event.getFormattedMessage().contains("mode=chunk")
                && event.getFormattedMessage().contains("stepName=customers-step")));
    }

    @Test
    void buildStepsFailsFastWhenRetryPolicyIsCombinedWithOrderedDuplicateWinnerSelection() throws Exception {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(csvSource("Customers", tempCsv("customers-small.csv"))));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(xmlTarget("Customers", "Customer")));

        ProcessorConfig processorConfig = processorConfig(mappingWithOrderedDuplicateRule("Customers", "Customers"));

        BatchConfig batchConfig = new BatchConfig(
                sourceWrapper,
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig,
                targetWrapper,
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customers-retry-policy-duplicate-conflict",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "customers-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of(stepWithRetryPolicy("customers-step", "Customers", "Customers", 3, 100L,
                                List.of("runtime"),
                                List.of()))
                ),
                new FileIngestionRuntimeSupport(),
                new DuplicateResolverFactory()
        );
        ReflectionTestUtils.setField(batchConfig, "chunkThreshold", 10000);

        IllegalStateException exception = assertThrows(IllegalStateException.class, batchConfig::buildSteps);
        assertTrue(exception.getMessage().contains("both ordered duplicate winner selection and retryPolicy"));
    }

    @Test
    void configuredSkipPolicyStopsSkippingWhenSkipLimitIsReached() throws Exception {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(csvSource("Customers", tempCsv("customers-small.csv"))));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(xmlTarget("Customers", "Customer")));

        ProcessorConfig processorConfig = processorConfig(mapping("Customers", "Customers"));

        BatchConfig batchConfig = new BatchConfig(
                sourceWrapper,
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig,
                targetWrapper,
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customers-skip-policy-limit",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "customers-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of(stepWithSkipPolicy("customers-step", "Customers", "Customers", 1,
                                List.of("runtime"),
                                List.of()))
                ),
                new FileIngestionRuntimeSupport(),
                new DuplicateResolverFactory()
        );

        JobConfig.SkipPolicyConfig skipPolicyConfig = stepWithSkipPolicy("customers-step", "Customers", "Customers", 1,
                List.of("runtime"),
                List.of()).getSkipPolicy();
        SkipPolicy skipPolicy = ReflectionTestUtils.invokeMethod(batchConfig, "configuredSkipPolicy", skipPolicyConfig, "customers-step");
        assertNotNull(skipPolicy);

        assertTrue(skipPolicy.shouldSkip(new RuntimeEtlException("first runtime failure"), 0));
        assertFalse(skipPolicy.shouldSkip(new RuntimeEtlException("second runtime failure"), 1));
    }

    @Test
    void buildStepsHonorsConfiguredEmbeddedDbStorageModeForOrderedDuplicateSelection() throws Exception {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(csvSource("Customers", tempCsv("customers.csv"))));

        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(xmlTarget("Customers", "Customer")));

        ProcessorConfig processorConfig = processorConfig(mappingWithOrderedDuplicateRule("Customers", "Customers", "embeddedDb"));
        ListAppender<ILoggingEvent> appender = attachAppender();

        BatchConfig batchConfig = new BatchConfig(
                sourceWrapper,
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig,
                targetWrapper,
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customers-ordered-duplicate-embedded",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "customers-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of(step("customers-step", "Customers", "Customers"))
                ),
                new FileIngestionRuntimeSupport(),
                new DuplicateResolverFactory()
        );
        ReflectionTestUtils.setField(batchConfig, "chunkThreshold", 10000);

        List<Step> steps = batchConfig.buildSteps();

        assertEquals(List.of("customers-step"), steps.stream().map(Step::getName).toList());
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_READY event=duplicate_resolver_plan")
                && event.getFormattedMessage().contains("stepName=customers-step")
                && event.getFormattedMessage().contains("duplicateIdentityMode=flatMapped")
                && event.getFormattedMessage().contains("resolverMode=embeddedDb")
                && event.getFormattedMessage().contains("resolverReason=configured_storage_mode_embeddedDb")));
    }

    @Test
    void configuredRetryPolicyRetriesMatchingRuntimeFailuresUntilBudgetIsExhausted() throws Exception {
        BatchConfig batchConfig = new BatchConfig(
                new SourceWrapper(),
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig(mapping("Customers", "Customers")),
                new TargetWrapper(),
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customers-retry-policy-runtime-category",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "customers-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of()
                ),
                new FileIngestionRuntimeSupport(),
                new DuplicateResolverFactory()
        );

        JobConfig.RetryPolicyConfig retryPolicyConfig = stepWithRetryPolicy("customers-step", "Customers", "Customers", 3, 25L,
                List.of("runtime"), List.of()).getRetryPolicy();
        RetryPolicy retryPolicy = ReflectionTestUtils.invokeMethod(batchConfig, "configuredRetryPolicy", retryPolicyConfig, "customers-step");
        assertNotNull(retryPolicy);

        RetryContext context = retryPolicy.open(null);
        assertTrue(retryPolicy.canRetry(context));

        retryPolicy.registerThrowable(context, new RuntimeEtlException("first runtime failure"));
        assertTrue(retryPolicy.canRetry(context));

        retryPolicy.registerThrowable(context, new RuntimeEtlException("second runtime failure"));
        assertTrue(retryPolicy.canRetry(context));

        retryPolicy.registerThrowable(context, new RuntimeEtlException("third runtime failure"));
        assertFalse(retryPolicy.canRetry(context));
    }

    @Test
    void configuredRetryPolicyDoesNotRetryMismatchedCategories() throws Exception {
        BatchConfig batchConfig = new BatchConfig(
                new SourceWrapper(),
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig(mapping("Customers", "Customers")),
                new TargetWrapper(),
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customers-retry-policy-mismatch",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "customers-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of()
                ),
                new FileIngestionRuntimeSupport(),
                new DuplicateResolverFactory()
        );

        JobConfig.RetryPolicyConfig retryPolicyConfig = stepWithRetryPolicy("customers-step", "Customers", "Customers", 3, 25L,
                List.of("runtime"), List.of()).getRetryPolicy();
        RetryPolicy retryPolicy = ReflectionTestUtils.invokeMethod(batchConfig, "configuredRetryPolicy", retryPolicyConfig, "customers-step");
        assertNotNull(retryPolicy);

        RetryContext context = retryPolicy.open(null);
        retryPolicy.registerThrowable(context, new ConfigException("deterministic config failure"));

        assertFalse(retryPolicy.canRetry(context));
    }

    @Test
    void configuredRetryListenerLogsSucceededAfterRetrySummary() throws Exception {
        BatchConfig batchConfig = new BatchConfig(
                new SourceWrapper(),
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig(mapping("Customers", "Customers")),
                new TargetWrapper(),
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customers-retry-policy-listener-success",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "customers-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of()
                ),
                new FileIngestionRuntimeSupport(),
                new DuplicateResolverFactory()
        );
        JobConfig.RetryPolicyConfig retryPolicyConfig = stepWithRetryPolicy("customers-step", "Customers", "Customers", 3, 25L,
                List.of("runtime"), List.of()).getRetryPolicy();
        RetryListener retryListener = ReflectionTestUtils.invokeMethod(batchConfig, "configuredRetryListener",
                retryPolicyConfig,
                "customers-step",
                csvSource("Customers", tempCsv("customers-retry.csv")),
                xmlTarget("Customers", "Customer"),
                null);
        assertNotNull(retryListener);

        ListAppender<ILoggingEvent> appender = attachAppender();
        RetryPolicy retryPolicy = ReflectionTestUtils.invokeMethod(batchConfig, "configuredRetryPolicy", retryPolicyConfig, "customers-step");
        assertNotNull(retryPolicy);
        RetryContext context = retryPolicy.open(null);
        RetryCallback<Object, Throwable> callback = retryContext -> null;

        assertTrue(retryListener.open(context, callback));
        retryListener.onError(context, callback, new RuntimeEtlException("transient runtime failure"));
        retryListener.close(context, callback, null);

        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_EVENT event=retry_attempt")
                && event.getFormattedMessage().contains("stepName=customers-step")
                && event.getFormattedMessage().contains("attemptNumber=1")
                && event.getFormattedMessage().contains("action=retry_scheduled")
                && event.getFormattedMessage().contains("failureCategory=runtime")));
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_EVENT event=retry_summary")
                && event.getFormattedMessage().contains("outcome=succeeded_after_retry")
                && event.getFormattedMessage().contains("totalAttempts=2")
                && event.getFormattedMessage().contains("firstFailureCategory=runtime")
                && event.getFormattedMessage().contains("firstExceptionType=RuntimeEtlException")));
    }

    @Test
    void configuredRetryListenerLogsFailedAfterRetriesSummary() throws Exception {
        BatchConfig batchConfig = new BatchConfig(
                new SourceWrapper(),
                mockReaderFactory(),
                mockWriterFactory(),
                mock(JobRepository.class),
                mock(PlatformTransactionManager.class),
                new JobCompletionNotificationListener(),
                mockProcessorFactory(),
                processorConfig(mapping("Customers", "Customers")),
                new TargetWrapper(),
                new StepLoggingContextListener(),
                new RunConfigurationMetadata(
                        "customers-retry-policy-listener-failure",
                        tempDir.resolve("job-config.yaml").toString(),
                        false,
                        "customers-main-flow",
                        "default-subflow",
                        JobRecoveryPolicy.RERUN_FROM_START,
                        List.of()
                ),
                new FileIngestionRuntimeSupport(),
                new DuplicateResolverFactory()
        );
        JobConfig.RetryPolicyConfig retryPolicyConfig = stepWithRetryPolicy("customers-step", "Customers", "Customers", 3, 25L,
                List.of("runtime"), List.of()).getRetryPolicy();
        RetryListener retryListener = ReflectionTestUtils.invokeMethod(batchConfig, "configuredRetryListener",
                retryPolicyConfig,
                "customers-step",
                csvSource("Customers", tempCsv("customers-retry-failure.csv")),
                xmlTarget("Customers", "Customer"),
                null);
        assertNotNull(retryListener);

        ListAppender<ILoggingEvent> appender = attachAppender();
        RetryPolicy retryPolicy = ReflectionTestUtils.invokeMethod(batchConfig, "configuredRetryPolicy", retryPolicyConfig, "customers-step");
        assertNotNull(retryPolicy);
        RetryContext context = retryPolicy.open(null);
        RetryCallback<Object, Throwable> callback = retryContext -> null;

        assertTrue(retryListener.open(context, callback));
        retryListener.onError(context, callback, new RuntimeEtlException("first runtime failure"));
        retryListener.onError(context, callback, new RuntimeEtlException("second runtime failure"));
        retryListener.onError(context, callback, new RuntimeEtlException("third runtime failure"));
        retryListener.close(context, callback, new RuntimeEtlException("terminal runtime failure"));

        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_EVENT event=retry_attempt")
                && event.getFormattedMessage().contains("attemptNumber=3")
                && event.getFormattedMessage().contains("action=retry_exhausted")
                && event.getFormattedMessage().contains("failureCategory=runtime")));
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("STEP_EVENT event=retry_summary")
                && event.getFormattedMessage().contains("outcome=failed_after_retries")
                && event.getFormattedMessage().contains("totalAttempts=3")
                && event.getFormattedMessage().contains("firstFailureCategory=runtime")
                && event.getFormattedMessage().contains("terminalFailureCategory=runtime")
                && event.getFormattedMessage().contains("terminalExceptionType=RuntimeEtlException")));
    }

    private CsvSourceConfig csvSource(String sourceName, Path filePath) {
        CsvSourceConfig config = new CsvSourceConfig();
        config.setSourceName(sourceName);
        config.setPackageName("com.etl.model.source");
        config.setFilePath(filePath.toString());
        config.setDelimiter(",");
        config.setFields(List.of(column("id", "int")));
        return config;
    }

    private TargetConfig xmlTarget(String targetName, String recordElement) {
        return new XmlTargetConfig(targetName, "com.etl.model.target", List.of(column("id", "int")), tempDir.toString(), targetName, recordElement);
    }

    private ProcessorConfig processorConfig(ProcessorConfig.EntityMapping... mappings) {
        ProcessorConfig config = new ProcessorConfig();
        config.setType("default");
        config.setMappings(List.of(mappings));
        return config;
    }

    private ProcessorConfig.EntityMapping mapping(String source, String target) {
        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource(source);
        mapping.setTarget(target);
        ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
        fieldMapping.setFrom("id");
        fieldMapping.setTo("id");
        mapping.setFields(List.of(fieldMapping));
        return mapping;
    }

    @SuppressWarnings("SameParameterValue")
    private ProcessorConfig.EntityMapping mappingWithOrderedDuplicateRule(String source, String target) {
        return mappingWithOrderedDuplicateRule(source, target, null);
    }

    @SuppressWarnings("SameParameterValue")
    private ProcessorConfig.EntityMapping mappingWithOrderedDuplicateRule(String source, String target, String storageMode) {
        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource(source);
        mapping.setTarget(target);

        ProcessorConfig.FieldRule duplicateRule = new ProcessorConfig.FieldRule();
        duplicateRule.setType("duplicate");
        duplicateRule.setOrderBy(List.of(orderBy("eventTime", "DESC")));
        duplicateRule.setStorageMode(storageMode);

        ProcessorConfig.FieldMapping idField = new ProcessorConfig.FieldMapping();
        idField.setFrom("id");
        idField.setTo("id");
        idField.setRules(List.of(duplicateRule));

        ProcessorConfig.FieldMapping eventTimeField = new ProcessorConfig.FieldMapping();
        eventTimeField.setFrom("eventTime");
        eventTimeField.setTo("eventTime");

        mapping.setFields(List.of(idField, eventTimeField));
        return mapping;
    }

    @SuppressWarnings("SameParameterValue")
    private ProcessorConfig.OrderByField orderBy(String field, String direction) {
        ProcessorConfig.OrderByField orderByField = new ProcessorConfig.OrderByField();
        orderByField.setField(field);
        orderByField.setDirection(direction);
        return orderByField;
    }

    @SuppressWarnings("SameParameterValue")
    private ColumnConfig column(String name, String type) {
        ColumnConfig column = new ColumnConfig();
        column.setName(name);
        column.setType(type);
        return column;
    }

    private JobConfig.JobStepConfig step(String name, String source, String target) {
        JobConfig.JobStepConfig step = new JobConfig.JobStepConfig();
        step.setName(name);
        step.setSource(source);
        step.setTarget(target);
        return step;
    }

    @SuppressWarnings("SameParameterValue")
    private JobConfig.JobStepConfig stepWithSkipPolicy(String name,
                                                       String source,
                                                       String target,
                                                       int skipLimit,
                                                       List<String> skippableCategories,
                                                       List<String> skippableExceptions) {
        JobConfig.JobStepConfig step = step(name, source, target);
        JobConfig.SkipPolicyConfig skipPolicy = new JobConfig.SkipPolicyConfig();
        skipPolicy.setEnabled(true);
        skipPolicy.setSkipLimit(skipLimit);
        skipPolicy.setSkippableCategories(skippableCategories);
        skipPolicy.setSkippableExceptions(skippableExceptions);
        step.setSkipPolicy(skipPolicy);
        return step;
    }

    @SuppressWarnings("SameParameterValue")
    private JobConfig.JobStepConfig stepWithRetryPolicy(String name,
                                                        String source,
                                                        String target,
                                                        int maxAttempts,
                                                        long backoffMs,
                                                        List<String> retryableCategories,
                                                        List<String> retryableExceptions) {
        JobConfig.JobStepConfig step = step(name, source, target);
        JobConfig.RetryPolicyConfig retryPolicy = new JobConfig.RetryPolicyConfig();
        retryPolicy.setEnabled(true);
        retryPolicy.setMaxAttempts(maxAttempts);
        retryPolicy.setBackoffMs(backoffMs);
        retryPolicy.setRetryableCategories(retryableCategories);
        retryPolicy.setRetryableExceptions(retryableExceptions);
        step.setRetryPolicy(retryPolicy);
        return step;
    }

    @SuppressWarnings("unchecked")
    private DynamicReaderFactory mockReaderFactory() throws Exception {
        DynamicReaderFactory readerFactory = mock(DynamicReaderFactory.class);
        ItemReader<Object> reader = () -> null;
        when(readerFactory.createReader(any(SourceConfig.class), any(Class.class))).thenReturn(reader);
        return readerFactory;
    }

    private DynamicWriterFactory mockWriterFactory() throws Exception {
        DynamicWriterFactory writerFactory = mock(DynamicWriterFactory.class);
        ItemWriter<Object> writer = chunk -> { };
        when(writerFactory.createWriter(any(TargetConfig.class), any(Class.class))).thenReturn(writer);
        return writerFactory;
    }

    private DynamicProcessorFactory mockProcessorFactory() throws Exception {
        DynamicProcessorFactory processorFactory = mock(DynamicProcessorFactory.class);
        ItemProcessor<Object, Object> processor = item -> item;
        when(processorFactory.getProcessor(any(ProcessorConfig.class), any(SourceConfig.class), any(TargetConfig.class), any())).thenReturn(processor);
        return processorFactory;
    }

  private ListAppender<ILoggingEvent> attachAppender() {
    batchConfigLogger.detachAndStopAllAppenders();
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    batchConfigLogger.addAppender(appender);
    return appender;
  }

  @SuppressWarnings("SameParameterValue")
  private JobRuntimeDescriptor jobRuntimeDescriptor(String scenarioName,
                                                          SourceWrapper sourceWrapper,
                                                          TargetWrapper targetWrapper,
                                                          ProcessorConfig processorConfig,
                                                          List<JobConfig.JobStepConfig> steps) {
    JobRuntimeDescriptorAssembler assembler = new JobRuntimeDescriptorAssembler();
    return assembler.assemble(
        scenarioName,
        tempDir.resolve("job-config.yaml").toString(),
        JobRunMode.EXPLICIT_JOB,
        new JobConfigPaths("source-config.yaml", "target-config.yaml", "processor-config.yaml"),
        steps,
        sourceWrapper,
        targetWrapper,
        processorConfig
    );
  }

    private Path tempCsv(String fileName) throws Exception {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, "id\n1\n");
        return file;
    }
}

