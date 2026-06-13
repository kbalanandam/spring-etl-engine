package com.etl.config;

import com.etl.config.job.JobConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.exception.EtlErrorCategory;
import com.etl.exception.EtlExceptionDetails;
import com.etl.exception.FactoryException;
import com.etl.exception.ListenerException;
import com.etl.exception.RelationalException;
import com.etl.exception.RuntimeEtlException;
import com.etl.exception.SourceReadException;
import com.etl.exception.TargetWriteException;
import com.etl.exception.TransformationException;
import com.etl.exception.ValidationException;
import com.etl.exception.config.ConfigException;
import com.etl.exception.processor.ProcessorException;
import com.etl.exception.reader.ReaderException;
import com.etl.exception.writer.WriterException;
import com.etl.runtime.job.JobSubFlowDescriptor;
import org.slf4j.Logger;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.lang.NonNull;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.ExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bridge seam for skip/retry policy construction extracted from BatchConfig.
 *
 * <p>This helper keeps behavior stable while reducing orchestration weight in BatchConfig.</p>
 */
final class BatchStepPolicySupport {

    private static final String RETRY_FAILURE_COUNT_KEY = "configuredRetryFailureCount";
    private static final String RETRY_FIRST_FAILURE_CATEGORY_KEY = "configuredRetryFirstFailureCategory";
    private static final String RETRY_FIRST_EXCEPTION_TYPE_KEY = "configuredRetryFirstExceptionType";
    private static final String RETRY_FIRST_ROOT_CAUSE_KEY = "configuredRetryFirstRootCause";

    private final Logger logger;
    private final RunConfigurationMetadata runConfigurationMetadata;

    BatchStepPolicySupport(Logger logger, RunConfigurationMetadata runConfigurationMetadata) {
        this.logger = logger;
        this.runConfigurationMetadata = runConfigurationMetadata;
    }

    SkipPolicy configuredSkipPolicy(JobConfig.SkipPolicyConfig skipPolicy, String stepName) {
        return new ConfiguredSkipPolicy(
                skipPolicy.getSkipLimit(),
                resolveSkippableCategories(skipPolicy),
                resolveSkippableExceptionClasses(skipPolicy, stepName)
        );
    }

    RetryPolicy configuredRetryPolicy(JobConfig.RetryPolicyConfig retryPolicy,
                                      String stepName) {
        List<String> retryableCategories = resolveRetryableCategories(retryPolicy);
        List<Class<? extends Throwable>> retryableExceptions = resolveRetryableExceptionClasses(retryPolicy, stepName);
        SimpleRetryPolicy matchingRetryPolicy = new SimpleRetryPolicy(retryPolicy.getMaxAttempts());
        NeverRetryPolicy neverRetryPolicy = new NeverRetryPolicy();
        ExceptionClassifierRetryPolicy retryClassifier = new ExceptionClassifierRetryPolicy();
        retryClassifier.setExceptionClassifier(throwable -> throwable == null
                || matchesConfiguredRetryCategory(throwable, retryableCategories)
                || matchesConfiguredRetryException(throwable, retryableExceptions)
                ? matchingRetryPolicy
                : neverRetryPolicy);
        return retryClassifier;
    }

    FixedBackOffPolicy configuredRetryBackOffPolicy(JobConfig.RetryPolicyConfig retryPolicy) {
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(retryPolicy.getBackoffMs());
        return backOffPolicy;
    }

    RetryListener configuredRetryListener(JobConfig.RetryPolicyConfig retryPolicy,
                                          String stepName,
                                          SourceConfig sourceConfig,
                                          TargetConfig targetConfig,
                                          JobSubFlowDescriptor stepSubFlow) {
        return new RetryListener() {
            @Override
            public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
                context.setAttribute(RETRY_FAILURE_COUNT_KEY, 0);
                return true;
            }

            @Override
            public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                int failureCount = retryFailureCount(context) + 1;
                context.setAttribute(RETRY_FAILURE_COUNT_KEY, failureCount);
                if (context.getAttribute(RETRY_FIRST_FAILURE_CATEGORY_KEY) == null) {
                    context.setAttribute(RETRY_FIRST_FAILURE_CATEGORY_KEY, EtlExceptionDetails.categoryValueOf(throwable));
                    context.setAttribute(RETRY_FIRST_EXCEPTION_TYPE_KEY, EtlExceptionDetails.exceptionType(throwable));
                    context.setAttribute(RETRY_FIRST_ROOT_CAUSE_KEY, EtlExceptionDetails.rootCauseMessage(throwable));
                }
                logger.warn("STEP_EVENT event=retry_attempt mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} attemptNumber={} maxAttempts={} backoffMs={} failureCategory={} exceptionType={} rootCause={} action={}",
                        runConfigurationMetadata.mainFlowName(),
                        stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                        runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                        stepName,
                        sourceConfig.getSourceName(),
                        targetConfig.getTargetName(),
                        failureCount,
                        retryPolicy.getMaxAttempts(),
                        retryPolicy.getBackoffMs(),
                        EtlExceptionDetails.categoryValueOf(throwable),
                        EtlExceptionDetails.exceptionType(throwable),
                        EtlExceptionDetails.rootCauseMessage(throwable),
                        failureCount < retryPolicy.getMaxAttempts() ? "retry_scheduled" : "retry_exhausted");
            }

            @Override
            public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                int failureCount = retryFailureCount(context);
                if (failureCount <= 0) {
                    return;
                }
                int totalAttempts = throwable == null ? failureCount + 1 : failureCount;
                String firstFailureCategory = stringAttribute(context, RETRY_FIRST_FAILURE_CATEGORY_KEY, "unknown");
                String firstExceptionType = stringAttribute(context, RETRY_FIRST_EXCEPTION_TYPE_KEY, "unknown");
                String firstRootCause = stringAttribute(context, RETRY_FIRST_ROOT_CAUSE_KEY, "none");
                if (throwable == null) {
                    logger.info("STEP_EVENT event=retry_summary mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} outcome=succeeded_after_retry totalAttempts={} maxAttempts={} backoffMs={} firstFailureCategory={} firstExceptionType={} firstRootCause={}",
                            runConfigurationMetadata.mainFlowName(),
                            stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                            runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                            stepName,
                            sourceConfig.getSourceName(),
                            targetConfig.getTargetName(),
                            totalAttempts,
                            retryPolicy.getMaxAttempts(),
                            retryPolicy.getBackoffMs(),
                            firstFailureCategory,
                            firstExceptionType,
                            firstRootCause);
                    return;
                }
                logger.error("STEP_EVENT event=retry_summary mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} outcome=failed_after_retries totalAttempts={} maxAttempts={} backoffMs={} firstFailureCategory={} firstExceptionType={} firstRootCause={} terminalFailureCategory={} terminalExceptionType={} terminalRootCause={}",
                        runConfigurationMetadata.mainFlowName(),
                        stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                        runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                        stepName,
                        sourceConfig.getSourceName(),
                        targetConfig.getTargetName(),
                        totalAttempts,
                        retryPolicy.getMaxAttempts(),
                        retryPolicy.getBackoffMs(),
                        firstFailureCategory,
                        firstExceptionType,
                        firstRootCause,
                        EtlExceptionDetails.categoryValueOf(throwable),
                        EtlExceptionDetails.exceptionType(throwable),
                        EtlExceptionDetails.rootCauseMessage(throwable),
                        throwable);
            }
        };
    }

    private List<String> resolveSkippableCategories(JobConfig.SkipPolicyConfig skipPolicy) {
        List<String> configuredCategories = skipPolicy.getSkippableCategories();
        if (configuredCategories == null || configuredCategories.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String configuredCategory : configuredCategories) {
            if (configuredCategory != null && !configuredCategory.isBlank()) {
                normalized.add(configuredCategory.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(normalized);
    }

    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> resolveSkippableExceptionClasses(JobConfig.SkipPolicyConfig skipPolicy,
                                                                               String stepName) {
        List<String> configuredExceptions = skipPolicy.getSkippableExceptions();
        if (configuredExceptions == null || configuredExceptions.isEmpty()) {
            return List.of();
        }
        List<Class<? extends Throwable>> exceptionClasses = new ArrayList<>();
        for (String className : configuredExceptions) {
            try {
                Class<?> candidate = Class.forName(className);
                if (!Throwable.class.isAssignableFrom(candidate)) {
                    throw new IllegalStateException("Step '" + stepName + "' skipPolicy exception class '" + className + "' must extend Throwable.");
                }
                exceptionClasses.add((Class<? extends Throwable>) candidate);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Step '" + stepName + "' skipPolicy exception class '" + className + "' was not found.", e);
            }
        }
        return List.copyOf(exceptionClasses);
    }

    private List<String> resolveRetryableCategories(JobConfig.RetryPolicyConfig retryPolicy) {
        List<String> configuredCategories = retryPolicy.getRetryableCategories();
        if (configuredCategories == null || configuredCategories.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String configuredCategory : configuredCategories) {
            if (configuredCategory != null && !configuredCategory.isBlank()) {
                normalized.add(configuredCategory.trim().toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(normalized);
    }

    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> resolveRetryableExceptionClasses(JobConfig.RetryPolicyConfig retryPolicy,
                                                                               String stepName) {
        List<String> configuredExceptions = retryPolicy.getRetryableExceptions();
        List<Class<? extends Throwable>> exceptionClasses = new ArrayList<>(exceptionClassesForCategories(retryPolicy.getRetryableCategories()));
        if (configuredExceptions == null || configuredExceptions.isEmpty()) {
            return List.copyOf(exceptionClasses);
        }
        for (String className : configuredExceptions) {
            try {
                Class<?> candidate = Class.forName(className);
                if (!Throwable.class.isAssignableFrom(candidate)) {
                    throw new IllegalStateException("Step '" + stepName + "' retryPolicy exception class '" + className + "' must extend Throwable.");
                }
                exceptionClasses.add((Class<? extends Throwable>) candidate);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Step '" + stepName + "' retryPolicy exception class '" + className + "' was not found.", e);
            }
        }
        return List.copyOf(exceptionClasses);
    }

    List<Class<? extends Throwable>> exceptionClassesForCategories(List<String> configuredCategories) {
        if (configuredCategories == null || configuredCategories.isEmpty()) {
            return List.of();
        }
        List<Class<? extends Throwable>> exceptionClasses = new ArrayList<>();
        for (String configuredCategory : configuredCategories) {
            if (configuredCategory == null || configuredCategory.isBlank()) {
                continue;
            }
            java.util.Optional<EtlErrorCategory> resolvedCategory = EtlErrorCategory.fromToken(configuredCategory);
            if (resolvedCategory.isEmpty()) {
                continue;
            }
            switch (resolvedCategory.get()) {
                case CONFIG -> exceptionClasses.add(ConfigException.class);
                case VALIDATION -> {
                    exceptionClasses.add(ValidationException.class);
                    exceptionClasses.add(ProcessorException.class);
                }
                case TRANSFORMATION -> {
                    exceptionClasses.add(TransformationException.class);
                    exceptionClasses.add(ProcessorException.class);
                }
                case SOURCE_READ -> {
                    exceptionClasses.add(SourceReadException.class);
                    exceptionClasses.add(ReaderException.class);
                }
                case TARGET_WRITE -> {
                    exceptionClasses.add(TargetWriteException.class);
                    exceptionClasses.add(WriterException.class);
                }
                case RUNTIME -> exceptionClasses.add(RuntimeEtlException.class);
                case FACTORY -> exceptionClasses.add(FactoryException.class);
                case LISTENER -> exceptionClasses.add(ListenerException.class);
                case RELATIONAL -> exceptionClasses.add(RelationalException.class);
                case UNCLASSIFIED -> {
                    // Explicit 'unclassified' category does not map to a concrete exception class.
                }
            }
        }
        return List.copyOf(exceptionClasses);
    }

    private boolean matchesConfiguredRetryCategory(Throwable throwable, List<String> retryableCategories) {
        if (retryableCategories.isEmpty()) {
            return false;
        }
        String category = EtlExceptionDetails.categoryValueOf(throwable).toLowerCase(Locale.ROOT);
        return retryableCategories.contains(category);
    }

    private boolean matchesConfiguredRetryException(Throwable throwable,
                                                    List<Class<? extends Throwable>> retryableExceptions) {
        if (retryableExceptions.isEmpty()) {
            return false;
        }
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            for (Class<? extends Throwable> retryableException : retryableExceptions) {
                if (retryableException.isAssignableFrom(current.getClass())) {
                    return true;
                }
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }

    private int retryFailureCount(RetryContext context) {
        Object value = context.getAttribute(RETRY_FAILURE_COUNT_KEY);
        return value instanceof Number number ? number.intValue() : 0;
    }

    private String stringAttribute(RetryContext context, String attributeName, String defaultValue) {
        Object value = context.getAttribute(attributeName);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static final class ConfiguredSkipPolicy implements SkipPolicy {

        private final int skipLimit;
        private final List<String> skippableCategories;
        private final List<Class<? extends Throwable>> skippableExceptions;

        private ConfiguredSkipPolicy(int skipLimit,
                                     List<String> skippableCategories,
                                     List<Class<? extends Throwable>> skippableExceptions) {
            this.skipLimit = skipLimit;
            this.skippableCategories = skippableCategories == null ? List.of() : skippableCategories;
            this.skippableExceptions = skippableExceptions == null ? List.of() : skippableExceptions;
        }

        @Override
        public boolean shouldSkip(@NonNull Throwable throwable, long skipCount) {
            if (skipCount >= skipLimit) {
                return false;
            }
            if (matchesConfiguredCategory(throwable)) {
                return true;
            }
            return matchesConfiguredException(throwable);
        }

        private boolean matchesConfiguredCategory(Throwable throwable) {
            if (skippableCategories.isEmpty()) {
                return false;
            }
            String category = EtlExceptionDetails.categoryValueOf(throwable).toLowerCase(Locale.ROOT);
            return skippableCategories.contains(category);
        }

        private boolean matchesConfiguredException(Throwable throwable) {
            if (skippableExceptions.isEmpty()) {
                return false;
            }
            for (Class<? extends Throwable> skippableException : skippableExceptions) {
                if (skippableException.isAssignableFrom(throwable.getClass())) {
                    return true;
                }
            }
            return false;
        }
    }
}


