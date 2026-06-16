package com.etl.config.batch.bridge;

import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.RunConfigurationMetadata;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.job.listener.FileIngestionHardeningStepListener;
import com.etl.job.listener.StepLoggingContextListener;
import com.etl.runtime.DuplicateDiscard;
import com.etl.runtime.DuplicateResolution;
import com.etl.runtime.DuplicateResolver;
import com.etl.runtime.DuplicateResolverFactory;
import com.etl.runtime.DuplicateRule;
import com.etl.runtime.FileIngestionRuntimeSupport;
import com.etl.runtime.job.JobSubFlowDescriptor;
import org.slf4j.Logger;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.builder.FaultTolerantStepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.RetryPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Bridge seam that assembles one resolved standard step (chunk or tasklet mode).
 */
public final class BatchStandardStepAssembler {

    private static final String ORDERED_DUPLICATE_RESOLVER_MODE_KEY = "orderedDuplicateResolverMode";
    private static final String ORDERED_DUPLICATE_RESOLVER_REASON_KEY = "orderedDuplicateResolverReason";

    private final Logger logger;
    private final RunConfigurationMetadata runConfigurationMetadata;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final StepLoggingContextListener stepLoggingContextListener;
    private final ProcessorConfig processorConfig;
    private final FileIngestionRuntimeSupport fileIngestionRuntimeSupport;
    private final DuplicateResolverFactory duplicateResolverFactory;
    private final BatchStepPolicySupport batchStepPolicySupport;

    public BatchStandardStepAssembler(Logger logger,
                                      RunConfigurationMetadata runConfigurationMetadata,
                                      JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      StepLoggingContextListener stepLoggingContextListener,
                                      ProcessorConfig processorConfig,
                                      FileIngestionRuntimeSupport fileIngestionRuntimeSupport,
                                      DuplicateResolverFactory duplicateResolverFactory,
                                      BatchStepPolicySupport batchStepPolicySupport) {
        this.logger = logger;
        this.runConfigurationMetadata = runConfigurationMetadata;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.stepLoggingContextListener = stepLoggingContextListener;
        this.processorConfig = processorConfig;
        this.fileIngestionRuntimeSupport = fileIngestionRuntimeSupport;
        this.duplicateResolverFactory = duplicateResolverFactory;
        this.batchStepPolicySupport = batchStepPolicySupport;
    }

    public Step assemble(StandardStepBuildContext context) {
        FileIngestionHardeningStepListener fileIngestionHardeningStepListener =
                new FileIngestionHardeningStepListener(
                        context.source(),
                        processorConfig,
                        context.mapping(),
                        fileIngestionRuntimeSupport
                );

        StepBuilder stepBuilder = new StepBuilder(context.stepName(), jobRepository);
        if (context.jobHierarchyContextListener() != null) {
            stepBuilder.listener(context.jobHierarchyContextListener());
        }

        return context.useChunk()
                ? buildChunkStep(context, stepBuilder, fileIngestionHardeningStepListener)
                : buildTaskletStep(context, stepBuilder, fileIngestionHardeningStepListener);
    }

    private Step buildChunkStep(StandardStepBuildContext context,
                                StepBuilder stepBuilder,
                                FileIngestionHardeningStepListener fileIngestionHardeningStepListener) {
        var chunkStepBuilder = stepBuilder.chunk(context.chunkThreshold(), transactionManager);
        chunkStepBuilder
                .listener(stepLoggingContextListener)
                .listener(fileIngestionHardeningStepListener)
                .reader(context.reader())
                .processor(context.processor())
                .writer(context.writer());

        if (context.writerStepExecutionListener() != null) {
            chunkStepBuilder.listener(context.writerStepExecutionListener());
        }

        Step step;
        JobConfig.SkipPolicyConfig configuredSkipPolicy = context.configuredSkipPolicy();
        JobConfig.RetryPolicyConfig configuredRetryPolicy = context.configuredRetryPolicy();
        if (configuredSkipPolicy != null && configuredSkipPolicy.isEnabled()) {
            FaultTolerantStepBuilder<Object, Object> faultTolerantBuilder = chunkStepBuilder
                    .faultTolerant()
                    .skipPolicy(batchStepPolicySupport.configuredSkipPolicy(configuredSkipPolicy, context.stepName()));
            step = faultTolerantBuilder.build();
            logger.info("STEP_READY event=skip_policy_enabled mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} skipLimit={} skippableCategories={} skippableExceptions={}",
                    runConfigurationMetadata.mainFlowName(),
                    subFlowName(context.stepSubFlow()),
                    recoveryPolicy(),
                    context.stepName(),
                    context.source().getSourceName(),
                    context.target().getTargetName(),
                    configuredSkipPolicy.getSkipLimit(),
                    configuredSkipPolicy.getSkippableCategories(),
                    configuredSkipPolicy.getSkippableExceptions());
        } else if (configuredRetryPolicy != null && configuredRetryPolicy.isEnabled()) {
            RetryPolicy retryPolicy = batchStepPolicySupport.configuredRetryPolicy(configuredRetryPolicy, context.stepName());
            FixedBackOffPolicy backOffPolicy = batchStepPolicySupport.configuredRetryBackOffPolicy(configuredRetryPolicy);
            RetryListener retryListener = batchStepPolicySupport.configuredRetryListener(
                    configuredRetryPolicy,
                    context.stepName(),
                    context.source(),
                    context.target(),
                    context.stepSubFlow()
            );
            FaultTolerantStepBuilder<Object, Object> faultTolerantBuilder = chunkStepBuilder
                    .faultTolerant()
                    .retryPolicy(retryPolicy)
                    .backOffPolicy(backOffPolicy)
                    .listener(retryListener);
            step = faultTolerantBuilder.build();
            logger.info("STEP_READY event=retry_policy_enabled mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} maxAttempts={} backoffMs={} retryableCategories={} retryableExceptions={}",
                    runConfigurationMetadata.mainFlowName(),
                    subFlowName(context.stepSubFlow()),
                    recoveryPolicy(),
                    context.stepName(),
                    context.source().getSourceName(),
                    context.target().getTargetName(),
                    configuredRetryPolicy.getMaxAttempts(),
                    configuredRetryPolicy.getBackoffMs(),
                    configuredRetryPolicy.getRetryableCategories(),
                    configuredRetryPolicy.getRetryableExceptions());
        } else {
            step = chunkStepBuilder.build();
        }

        logger.info("STEP_READY event=step_ready mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} mode=chunk recordCount={} threshold={}",
                runConfigurationMetadata.mainFlowName(),
                subFlowName(context.stepSubFlow()),
                recoveryPolicy(),
                context.stepName(),
                context.source().getSourceName(),
                context.target().getTargetName(),
                context.recordCount(),
                context.chunkThreshold());
        return step;
    }

    private Step buildTaskletStep(StandardStepBuildContext context,
                                  StepBuilder stepBuilder,
                                  FileIngestionHardeningStepListener fileIngestionHardeningStepListener) {
        stepBuilder
                .listener(stepLoggingContextListener)
                .listener(fileIngestionHardeningStepListener);

        if (context.writerStepExecutionListener() != null) {
            stepBuilder.listener(context.writerStepExecutionListener());
        }

        Step step = stepBuilder.tasklet((contribution, chunkContext) -> {
                    Object item;
                    List<Object> buffer = new ArrayList<>();
                    int acceptedCount = 0;
                    boolean rejectHandlingEnabled = processorConfig.getRejectHandling() != null
                            && processorConfig.getRejectHandling().isEnabled();
                    DuplicateResolver duplicateResolver = context.duplicateRule() == null
                            ? null
                            : duplicateResolverFactory.create(context.duplicateRule(), context.useEmbeddedDbDuplicateResolver());
                    recordOrderedDuplicateResolverEvidence(contribution, context);
                    ExecutionContext executionContext = contribution.getStepExecution().getExecutionContext();
                    boolean isReaderStream = context.reader() instanceof ItemStream;
                    boolean isWriterStream = context.writer() instanceof ItemStream;
                    if (isReaderStream) {
                        ((ItemStream) context.reader()).open(executionContext);
                    }
                    if (isWriterStream) {
                        ((ItemStream) context.writer()).open(executionContext);
                    }
                    try {
                        while ((item = context.reader().read()) != null) {
                            contribution.incrementReadCount();
                            if (duplicateResolver != null) {
                                duplicateResolver.accept(item);
                                continue;
                            }
                            Object processed = context.processor().process(item);
                            if (processed == null) {
                                contribution.incrementFilterCount(1);
                                continue;
                            }
                            buffer.add(processed);
                            acceptedCount++;
                        }
                        if (duplicateResolver != null) {
                            DuplicateResolution resolution = duplicateResolver.complete();
                            for (DuplicateDiscard discardedRecord : resolution.discardedRecords()) {
                                contribution.incrementFilterCount(1);
                                if (discardedRecord.invalidOrderingValue() && !rejectHandlingEnabled) {
                                    throw new IllegalStateException(discardedRecord.issue().message());
                                }
                                if (rejectHandlingEnabled) {
                                    boolean recorded = fileIngestionRuntimeSupport.recordRejected(
                                            discardedRecord.discardedRecord(),
                                            List.of(discardedRecord.issue())
                                    );
                                    if (!recorded) {
                                        throw new IllegalStateException("Ordered duplicate winner selection rejected a record but reject handling was not initialized for the current step.");
                                    }
                                }
                            }
                            for (Object retainedRecord : resolution.retainedRecords()) {
                                Object processed = context.processor().process(retainedRecord);
                                if (processed == null) {
                                    contribution.incrementFilterCount(1);
                                    continue;
                                }
                                buffer.add(processed);
                                acceptedCount++;
                            }
                        }
                        if (!buffer.isEmpty()) {
                            writeBufferedRecords(context, buffer);
                            contribution.incrementWriteCount(acceptedCount);
                        }
                    } finally {
                        if (duplicateResolver != null) {
                            duplicateResolver.close();
                        }
                        if (isReaderStream) {
                            ((ItemStream) context.reader()).close();
                        }
                        if (isWriterStream) {
                            ((ItemStream) context.writer()).close();
                        }
                    }
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();

        logger.info("STEP_READY event=step_ready mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} mode=tasklet recordCount={} threshold={}",
                runConfigurationMetadata.mainFlowName(),
                subFlowName(context.stepSubFlow()),
                recoveryPolicy(),
                context.stepName(),
                context.source().getSourceName(),
                context.target().getTargetName(),
                context.recordCount(),
                context.chunkThreshold());
        return step;
    }

    private void writeBufferedRecords(StandardStepBuildContext context, List<Object> buffer) throws Exception {
        if (context.metadata().isWrapperRequired()) {
            Object wrapper = GeneratedModelClassResolver.createWrapper(context.metadata(), buffer);
            logger.debug("Writing XML wrapper {}.{} with {} records",
                    context.metadata().getTargetWriteClassName(),
                    context.metadata().getWrapperFieldName(),
                    buffer.size());
            context.writer().write(new Chunk<>(List.of(wrapper)));
            return;
        }
        context.writer().write(new Chunk<>(buffer));
    }

    private void recordOrderedDuplicateResolverEvidence(StepContribution contribution,
                                                        StandardStepBuildContext context) {
        if (context.duplicateRule() == null || contribution == null) {
            return;
        }
        contribution.getStepExecution().getExecutionContext().putString(ORDERED_DUPLICATE_RESOLVER_MODE_KEY, context.orderedDuplicateResolverMode());
        contribution.getStepExecution().getExecutionContext().putString(ORDERED_DUPLICATE_RESOLVER_REASON_KEY, context.orderedDuplicateResolverReason());
        logger.info("STEP_EVENT event=duplicate_resolver_selected mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} duplicateStrategy=orderBy duplicateIdentityMode={} duplicateIdentityModeReason={} resolverMode={} resolverReason={} recordCount={} threshold={}",
                runConfigurationMetadata.mainFlowName(),
                subFlowName(context.stepSubFlow()),
                recoveryPolicy(),
                context.stepName(),
                context.source().getSourceName(),
                context.target().getTargetName(),
                context.duplicateRule().identityMode().configValue(),
                context.duplicateRule().identityModeReason(),
                context.orderedDuplicateResolverMode(),
                context.orderedDuplicateResolverReason(),
                context.resolvedRecordCount(),
                context.chunkThreshold());
    }

    private String subFlowName(JobSubFlowDescriptor stepSubFlow) {
        return stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName();
    }

    private String recoveryPolicy() {
        return runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue();
    }

    public record StandardStepBuildContext(
            String stepName,
            JobSubFlowDescriptor stepSubFlow,
            SourceConfig source,
            TargetConfig target,
            ProcessorConfig.EntityMapping mapping,
            ItemReader<Object> reader,
            ItemProcessor<Object, Object> processor,
            ItemWriter<Object> writer,
            ResolvedModelMetadata metadata,
            StepExecutionListener jobHierarchyContextListener,
            StepExecutionListener writerStepExecutionListener,
            int chunkThreshold,
            int recordCount,
            JobConfig.SkipPolicyConfig configuredSkipPolicy,
            JobConfig.RetryPolicyConfig configuredRetryPolicy,
            DuplicateRule duplicateRule,
            boolean useChunk,
            boolean useEmbeddedDbDuplicateResolver,
            int resolvedRecordCount,
            String orderedDuplicateResolverMode,
            String orderedDuplicateResolverReason
    ) {
    }
}



