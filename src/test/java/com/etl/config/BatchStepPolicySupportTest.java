package com.etl.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.etl.config.job.JobConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.exception.RuntimeEtlException;
import com.etl.exception.SourceReadException;
import com.etl.exception.TargetWriteException;
import com.etl.exception.ValidationException;
import com.etl.exception.reader.ReaderException;
import com.etl.runtime.job.JobRecoveryPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchStepPolicySupportTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(BatchStepPolicySupport.class);

    @AfterEach
    void tearDown() {
        logger.detachAndStopAllAppenders();
    }

    @Test
    void configuredSkipPolicyStopsSkippingWhenSkipLimitIsReached() {
        BatchStepPolicySupport support = new BatchStepPolicySupport(logger, runMetadata());

        SkipPolicy skipPolicy = support.configuredSkipPolicy(skipPolicy(1, List.of("RUNTIME"), List.of()), "customers-step");

        assertTrue(skipPolicy.shouldSkip(new RuntimeEtlException("first failure"), 0));
        assertFalse(skipPolicy.shouldSkip(new RuntimeEtlException("second failure"), 1));
    }

    @Test
    void configuredSkipPolicyFailsFastWhenConfiguredExceptionClassIsMissing() {
        BatchStepPolicySupport support = new BatchStepPolicySupport(logger, runMetadata());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> support.configuredSkipPolicy(skipPolicy(1, List.of(), List.of("com.example.DoesNotExist")), "customers-step")
        );

        assertTrue(exception.getMessage().contains("skipPolicy exception class 'com.example.DoesNotExist' was not found"));
    }

    @Test
    void configuredRetryPolicyMatchesRuntimeCategoryAndNestedExceptionClass() {
        BatchStepPolicySupport support = new BatchStepPolicySupport(logger, runMetadata());

        RetryPolicy retryPolicy = support.configuredRetryPolicy(
                retryPolicy(3, 25L, List.of("runtime"), List.of(SourceReadException.class.getName())),
                "customers-step"
        );

        RetryContext runtimeContext = retryPolicy.open(null);
        retryPolicy.registerThrowable(runtimeContext, new RuntimeEtlException("runtime failure"));
        assertTrue(retryPolicy.canRetry(runtimeContext));

        RetryContext nestedExceptionContext = retryPolicy.open(null);
        retryPolicy.registerThrowable(nestedExceptionContext,
                new IllegalStateException("wrapper", new SourceReadException("nested source read failure")));
        assertTrue(retryPolicy.canRetry(nestedExceptionContext));
    }

    @Test
    void configuredRetryPolicyDoesNotRetryMismatchedCategory() {
        BatchStepPolicySupport support = new BatchStepPolicySupport(logger, runMetadata());

        RetryPolicy retryPolicy = support.configuredRetryPolicy(
                retryPolicy(3, 25L, List.of("runtime"), List.of()),
                "customers-step"
        );

        RetryContext context = retryPolicy.open(null);
        retryPolicy.registerThrowable(context, new IllegalArgumentException("config mismatch"));

        assertFalse(retryPolicy.canRetry(context));
    }

    @Test
    void configuredRetryBackOffPolicyUsesConfiguredDelay() {
        BatchStepPolicySupport support = new BatchStepPolicySupport(logger, runMetadata());

        FixedBackOffPolicy backOffPolicy = support.configuredRetryBackOffPolicy(retryPolicy(3, 250L, List.of("runtime"), List.of()));

        assertEquals(250L, backOffPolicy.getBackOffPeriod());
    }

    @Test
    void exceptionClassesForCategoriesIncludesExpectedAliases() {
        BatchStepPolicySupport support = new BatchStepPolicySupport(logger, runMetadata());

        List<Class<? extends Throwable>> exceptionClasses = support.exceptionClassesForCategories(
                List.of("validation", "source-read", "target_write")
        );

        assertTrue(exceptionClasses.contains(ValidationException.class));
        assertTrue(exceptionClasses.contains(SourceReadException.class));
        assertTrue(exceptionClasses.contains(ReaderException.class));
        assertTrue(exceptionClasses.contains(TargetWriteException.class));
    }

    @Test
    void configuredRetryListenerLogsRetryAttemptAndSuccessSummary() {
        BatchStepPolicySupport support = new BatchStepPolicySupport(logger, runMetadata());
        ListAppender<ILoggingEvent> appender = attachAppender();

        JobConfig.RetryPolicyConfig retryPolicy = retryPolicy(3, 25L, List.of("runtime"), List.of());
        RetryListener retryListener = support.configuredRetryListener(
                retryPolicy,
                "customers-step",
                source("Customers"),
                target("CustomersOut"),
                null
        );
        assertNotNull(retryListener);

        RetryPolicy runtimeRetryPolicy = support.configuredRetryPolicy(retryPolicy, "customers-step");
        RetryContext context = runtimeRetryPolicy.open(null);
        RetryCallback<Object, Throwable> callback = retryContext -> null;

        assertTrue(retryListener.open(context, callback));
        retryListener.onError(context, callback, new RuntimeEtlException("transient runtime failure"));
        retryListener.close(context, callback, null);

        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("event=retry_attempt")
                && event.getFormattedMessage().contains("stepName=customers-step")
                && event.getFormattedMessage().contains("action=retry_scheduled")));
        assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("event=retry_summary")
                && event.getFormattedMessage().contains("outcome=succeeded_after_retry")
                && event.getFormattedMessage().contains("firstFailureCategory=runtime")));
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        logger.detachAndStopAllAppenders();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private RunConfigurationMetadata runMetadata() {
        return new RunConfigurationMetadata(
                "customers",
                "job-config.yaml",
                false,
                "customers-main-flow",
                "default-subflow",
                JobRecoveryPolicy.RERUN_FROM_START,
                List.of()
        );
    }

    @SuppressWarnings("SameParameterValue")
    private SourceConfig source(String sourceName) {
        SourceConfig source = mock(SourceConfig.class);
        when(source.getSourceName()).thenReturn(sourceName);
        return source;
    }

    @SuppressWarnings("SameParameterValue")
    private TargetConfig target(String targetName) {
        TargetConfig target = mock(TargetConfig.class);
        when(target.getTargetName()).thenReturn(targetName);
        return target;
    }

    @SuppressWarnings("SameParameterValue")
    private JobConfig.SkipPolicyConfig skipPolicy(int skipLimit,
                                                  List<String> categories,
                                                  List<String> exceptions) {
        JobConfig.SkipPolicyConfig skipPolicy = new JobConfig.SkipPolicyConfig();
        skipPolicy.setEnabled(true);
        skipPolicy.setSkipLimit(skipLimit);
        skipPolicy.setSkippableCategories(categories);
        skipPolicy.setSkippableExceptions(exceptions);
        return skipPolicy;
    }

    @SuppressWarnings("SameParameterValue")
    private JobConfig.RetryPolicyConfig retryPolicy(int maxAttempts,
                                                    long backoffMs,
                                                    List<String> categories,
                                                    List<String> exceptions) {
        JobConfig.RetryPolicyConfig retryPolicy = new JobConfig.RetryPolicyConfig();
        retryPolicy.setEnabled(true);
        retryPolicy.setMaxAttempts(maxAttempts);
        retryPolicy.setBackoffMs(backoffMs);
        retryPolicy.setRetryableCategories(categories);
        retryPolicy.setRetryableExceptions(exceptions);
        return retryPolicy;
    }
}


