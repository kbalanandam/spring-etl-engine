package com.etl.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.etl.common.util.DynamicBatchUtils;
import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.job.JobConfig;
import com.etl.config.source.SourceConfig;
import com.etl.runtime.DuplicateDiscard;
import com.etl.runtime.DuplicateResolution;
import com.etl.runtime.DuplicateResolver;
import com.etl.runtime.DuplicateResolverFactory;
import com.etl.runtime.DuplicateRule;
import com.etl.runtime.job.JobHierarchyLoggingSupport;
import com.etl.runtime.job.JobRuntimeDescriptor;
import com.etl.runtime.job.JobStepLinkDescriptor;
import com.etl.runtime.job.JobStepDescriptor;
import com.etl.runtime.job.JobStepModelDescriptor;
import com.etl.runtime.job.JobSubFlowDescriptor;
import com.etl.job.listener.FileIngestionHardeningStepListener;
import com.etl.runtime.FileIngestionRuntimeSupport;
import com.etl.step.CustomStepHandler;
import com.etl.step.DynamicCustomStepFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.builder.FaultTolerantStepBuilder;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.job.listener.JobCompletionNotificationListener;
import com.etl.job.listener.StepLoggingContextListener;
import com.etl.processor.DynamicProcessorFactory;
import com.etl.reader.DynamicReaderFactory;
import com.etl.writer.DynamicWriterFactory;

/**
 * BatchConfig sets up the Spring Batch job and steps for the ETL engine.
 * <p>
 * It dynamically builds steps based on the source and target configurations, and
 * chooses between chunk-oriented or tasklet-based processing depending on the source record count
 * and a configurable threshold. This optimizes performance for both small and large files.
 * </p>
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This class remains important while migrating away from the current 1.4.x runtime
 * assembly, but it should not quietly become the final center of the next-generation
 * architecture. Use it to support migration and compatibility while new generator-first
 * runtime paths are proven.</p>
 */
@Configuration
@EnableBatchProcessing
public class BatchConfig {

    private static final Logger logger = LoggerFactory.getLogger(BatchConfig.class);
    private static final String ORDERED_DUPLICATE_RESOLVER_MODE_KEY = "orderedDuplicateResolverMode";
    private static final String ORDERED_DUPLICATE_RESOLVER_REASON_KEY = "orderedDuplicateResolverReason";

    private final SourceWrapper sourceWrapper;
    private final TargetWrapper targetWrapper;
    private final DynamicReaderFactory readerFactory;
    private final DynamicWriterFactory writerFactory;
    private final DynamicProcessorFactory processorFactory;
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobCompletionNotificationListener listener;
    private final StepLoggingContextListener stepLoggingContextListener;
    private final ProcessorConfig processorConfig;
    private final RunConfigurationMetadata runConfigurationMetadata;
    private final JobRuntimeDescriptor jobRuntimeDescriptor;
	private final FileIngestionRuntimeSupport fileIngestionRuntimeSupport;
	private final DuplicateResolverFactory duplicateResolverFactory;
    private final DynamicCustomStepFactory customStepFactory;
    private final BatchStepPolicySupport batchStepPolicySupport;
    private final BatchStepModePlanner batchStepModePlanner;

    /**
     * The threshold for switching between chunk and tasklet processing.
     * If the source record count exceeds this value, chunk processing is used.
     */
    private final int chunkThreshold;

    /**
     * Constructs the BatchConfig with all required dependencies.
     *
     * @param sourceWrapper         the wrapper for source configurations
     * @param readerFactory         the factory for dynamic readers
     * @param writerFactory         the factory for dynamic writers
     * @param jobRepository         the Spring Batch job repository
     * @param transactionManager    the transaction manager
     * @param listener              the job completion listener
     * @param processorFactory      the factory for dynamic processors
     * @param processorConfig       the processor configuration
     * @param targetWrapper         the wrapper for target configurations
     */
    @Autowired
    public BatchConfig(SourceWrapper sourceWrapper, DynamicReaderFactory readerFactory,
                       DynamicWriterFactory writerFactory, JobRepository jobRepository,
                       PlatformTransactionManager transactionManager,
                       JobCompletionNotificationListener listener, DynamicProcessorFactory processorFactory,
                       ProcessorConfig processorConfig, TargetWrapper targetWrapper,
                       StepLoggingContextListener stepLoggingContextListener,
               RunConfigurationMetadata runConfigurationMetadata,
                   JobRuntimeDescriptor jobRuntimeDescriptor,
             FileIngestionRuntimeSupport fileIngestionRuntimeSupport,
              DuplicateResolverFactory duplicateResolverFactory,
              DynamicCustomStepFactory customStepFactory,
              EtlBatchProperties etlBatchProperties) {
        this.sourceWrapper = sourceWrapper;
        this.readerFactory = readerFactory;
        this.targetWrapper = targetWrapper;
        this.writerFactory = writerFactory;
        this.processorFactory = processorFactory;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.listener = listener;
        this.stepLoggingContextListener = stepLoggingContextListener;
        this.processorConfig = processorConfig;
            this.runConfigurationMetadata = runConfigurationMetadata;
            this.jobRuntimeDescriptor = jobRuntimeDescriptor;
		this.fileIngestionRuntimeSupport = fileIngestionRuntimeSupport;
    this.duplicateResolverFactory = duplicateResolverFactory;
        this.customStepFactory = customStepFactory == null ? new DynamicCustomStepFactory(List.of()) : customStepFactory;
        this.batchStepPolicySupport = new BatchStepPolicySupport(logger, runConfigurationMetadata);
        this.chunkThreshold = Math.max(1, etlBatchProperties == null ? 10000 : etlBatchProperties.getThreshold());
        this.batchStepModePlanner = new BatchStepModePlanner(logger, runConfigurationMetadata);

        logger.info("EtlJobConfiguration initialized.");
    }

    public BatchConfig(SourceWrapper sourceWrapper, DynamicReaderFactory readerFactory,
                       DynamicWriterFactory writerFactory, JobRepository jobRepository,
                       PlatformTransactionManager transactionManager,
                       JobCompletionNotificationListener listener, DynamicProcessorFactory processorFactory,
                       ProcessorConfig processorConfig, TargetWrapper targetWrapper,
                       StepLoggingContextListener stepLoggingContextListener,
                       RunConfigurationMetadata runConfigurationMetadata,
                       JobRuntimeDescriptor jobRuntimeDescriptor,
                       FileIngestionRuntimeSupport fileIngestionRuntimeSupport,
                       DuplicateResolverFactory duplicateResolverFactory) {
        this(sourceWrapper,
                readerFactory,
                writerFactory,
                jobRepository,
                transactionManager,
                listener,
                processorFactory,
                processorConfig,
                targetWrapper,
                stepLoggingContextListener,
                runConfigurationMetadata,
                jobRuntimeDescriptor,
                fileIngestionRuntimeSupport,
                duplicateResolverFactory,
                null,
                new EtlBatchProperties());
    }

  public BatchConfig(SourceWrapper sourceWrapper, DynamicReaderFactory readerFactory,
                    DynamicWriterFactory writerFactory, JobRepository jobRepository,
                    PlatformTransactionManager transactionManager,
                    JobCompletionNotificationListener listener, DynamicProcessorFactory processorFactory,
                    ProcessorConfig processorConfig, TargetWrapper targetWrapper,
                    StepLoggingContextListener stepLoggingContextListener,
                    RunConfigurationMetadata runConfigurationMetadata,
                    FileIngestionRuntimeSupport fileIngestionRuntimeSupport,
                    DuplicateResolverFactory duplicateResolverFactory) {
    this(sourceWrapper,
        readerFactory,
        writerFactory,
        jobRepository,
        transactionManager,
        listener,
        processorFactory,
        processorConfig,
        targetWrapper,
        stepLoggingContextListener,
        runConfigurationMetadata,
        null,
        fileIngestionRuntimeSupport,
        duplicateResolverFactory,
        null,
        new EtlBatchProperties());
  }

    /**
     * Defines the main ETL job bean.
     *
     * <p>The runtime executes one flat ordered Spring Batch job per selected scenario.
     * MainFlow/SubFlow descriptors are emitted for observability, but execution still
     * follows the explicit step order resolved from {@code job-config.yaml}.</p>
     *
     * @return the configured Job
     * @throws Exception if step creation fails
     */
    @Bean
    public Job etlJob() throws Exception {
        List<Step> steps = buildSteps();

        if (steps.isEmpty()) {
            throw new IllegalStateException("No steps were created. Cannot build Job.");
        }

        SimpleJobBuilder jobBuilder = new JobBuilder("etlJob", jobRepository)
                .listener(listener)
                .start(steps.get(0));

        for (int i = 1; i < steps.size(); i++) {
            jobBuilder = jobBuilder.next(steps.get(i));
        }

        return jobBuilder.build();
    }

    /**
     * Builds the list of ETL steps based on the source and target configurations.
     * Each step is either chunk-oriented or tasklet-based, depending on the record count.
     *
     * <p>This method is where the shipped runtime turns explicit ordered job steps into the
     * concrete Spring Batch plan. Step order is taken from resolved job configuration, not
     * inferred from source/target list position. Runtime descriptor information is used only
     * to enrich logging and handoff metadata around those same ordered steps.</p>
     *
     * @return the list of configured steps
     * @throws Exception if step creation fails
     */
    List<Step> buildSteps() throws Exception {
        List<Step> steps = new ArrayList<>();
        List<String> plannedStepSequence = new ArrayList<>();
        List<? extends SourceConfig> sources = sourceWrapper.getSources();
        List<TargetConfig> targets = targetWrapper.getTargets();
            List<JobConfig.JobStepConfig> configuredSteps = runConfigurationMetadata.steps();
            List<JobStepDescriptor> jobSteps = jobRuntimeDescriptor == null ? List.of() : jobRuntimeDescriptor.steps();
            int resolvedStepCount = configuredSteps.size();

        if (sources == null || sources.isEmpty()) {
            throw new IllegalStateException("No source configurations found.");
        }
        if (targets == null || targets.isEmpty()) {
            throw new IllegalStateException("No target configurations found.");
        }
		if (resolvedStepCount == 0) {
            throw new IllegalStateException("No explicit job steps were resolved for scenario '" + runConfigurationMetadata.scenarioName() + "'.");
        }

    logger.info("Building ETL job for scenario '{}' mainFlow='{}' subFlow='{}' recoveryPolicy='{}' with {} explicit steps.",
        runConfigurationMetadata.scenarioName(),
        runConfigurationMetadata.mainFlowName(),
        runConfigurationMetadata.subFlowName(),
        runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
        resolvedStepCount);

        Map<String, SourceConfig> sourceByName = mapSourcesByName(sources);
        Map<String, TargetConfig> targetByName = mapTargetsByName(targets);
        Map<String, JobStepDescriptor> descriptorByStepName = new LinkedHashMap<>();
        for (JobStepDescriptor jobStep : jobSteps) {
            descriptorByStepName.put(jobStep.stepName(), jobStep);
        }

                for (int i = 0; i < resolvedStepCount; i++) {
              JobConfig.JobStepConfig configuredStep = configuredSteps.get(i);
              if (configuredStep == null) {
                  throw new IllegalStateException("Encountered null configured step at index " + i + ".");
              }
              JobStepDescriptor jobStep = descriptorByStepName.get(configuredStep.getName());
              String stepName = configuredStep.getName();
              Integer descriptorStepOrder = jobStep == null ? null : jobStep.stepOrder();
              JobSubFlowDescriptor stepSubFlow = jobStep == null ? null : JobHierarchyLoggingSupport.subFlowForStep(jobRuntimeDescriptor, stepName);
              List<JobStepLinkDescriptor> inboundLinks = jobStep == null ? List.of() : JobHierarchyLoggingSupport.inboundLinks(jobRuntimeDescriptor, stepName);

              if (configuredStep.isCustomStep()) {
                  String normalizedCustomType = configuredStep.getCustom() == null || configuredStep.getCustom().getType() == null
                          ? ""
                          : configuredStep.getCustom().getType().trim();
                  plannedStepSequence.add(i + ":" + stepName + ":custom(" + normalizedCustomType + ")");
                  steps.add(buildCustomStep(configuredStep, stepName, i, descriptorStepOrder, stepSubFlow, inboundLinks));
                  continue;
              }

              SourceConfig s = jobStep == null ? requireSource(configuredStep, sourceByName) : jobStep.sourceConfig();
              TargetConfig t = jobStep == null ? requireTarget(configuredStep, targetByName) : jobStep.targetConfig();
              ProcessorConfig.EntityMapping mapping = jobStep == null ? requireProcessorMapping(configuredStep) : jobStep.processorMapping();

              String sourceName = jobStep == null ? configuredStep.getSource() : jobStep.sourceName();
              String targetName = jobStep == null ? configuredStep.getTarget() : jobStep.targetName();
                plannedStepSequence.add(i + ":" + stepName + ":standard(" + sourceName + "->" + targetName + ")");
                JobConfig.SkipPolicyConfig configuredSkipPolicy = configuredStep.getSkipPolicy();
                JobConfig.RetryPolicyConfig configuredRetryPolicy = configuredStep.getRetryPolicy();
                logger.info("STEP_PLAN event=step_plan mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} configuredIndex={} descriptorStepOrder={} stepSubFlowOrder={} dependsOnSubFlows={} consumesHandoffAliases={} producesHandoffAliases={} upstreamSteps={} linkTypes={} linkControlSummary={} stepSummary={}",
                      runConfigurationMetadata.mainFlowName(),
                        stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                      runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                      stepName,
                      sourceName,
                      targetName,
                        i,
                        descriptorStepOrder == null ? -1 : descriptorStepOrder,
            stepSubFlow == null ? -1 : stepSubFlow.subFlowOrder(),
            JobHierarchyLoggingSupport.formatList(stepSubFlow == null ? List.of() : stepSubFlow.dependsOnSubFlowNames()),
            JobHierarchyLoggingSupport.formatList(stepSubFlow == null ? List.of() : stepSubFlow.consumesHandoffAliases()),
            JobHierarchyLoggingSupport.formatList(stepSubFlow == null ? List.of() : stepSubFlow.producesHandoffAliases()),
            JobHierarchyLoggingSupport.formatList(inboundLinks.stream().map(JobStepLinkDescriptor::fromStepName).toList()),
            JobHierarchyLoggingSupport.formatList(inboundLinks.stream().map(link -> link.linkType().name()).toList()),
            JobHierarchyLoggingSupport.formatList(inboundLinks.stream().map(link -> link.control().summary()).toList()),
              jobStep == null ? "" : jobStep.flowSummary());

            BatchStepModePlanner.StepModePlan stepModePlan = batchStepModePlanner.plan(
                    stepName,
                    s,
                    t,
                    mapping,
                    configuredSkipPolicy,
                    configuredRetryPolicy,
                    stepSubFlow,
                    chunkThreshold
            );
            boolean useChunk = stepModePlan.useChunk();
            int recordCount = stepModePlan.recordCount();
            DuplicateRule duplicateRule = stepModePlan.duplicateRule();
            boolean useEmbeddedDbDuplicateResolver = stepModePlan.useEmbeddedDbDuplicateResolver();
            final int resolvedRecordCount = stepModePlan.recordCount();
            final String resolvedOrderedDuplicateResolverMode = stepModePlan.orderedDuplicateResolverMode();
            final String resolvedOrderedDuplicateResolverReason = stepModePlan.orderedDuplicateResolverReason();

              ResolvedModelMetadata metadata = jobStep == null
					? GeneratedModelClassResolver.resolveMetadata(s, t)
          : toResolvedModelMetadata(jobStep.modelDescriptor());
            ItemReader<Object> reader = DynamicBatchUtils.getDynamicReader(readerFactory, s, metadata);
            Class<?> writerClass = metadata.isWrapperRequired() && useChunk
                    ? GeneratedModelClassResolver.resolveTargetProcessingClass(metadata)
                    : GeneratedModelClassResolver.resolveTargetWriteClass(metadata);
            ItemWriter<Object> writer = DynamicBatchUtils.getDynamicWriter(writerFactory, t, writerClass);
            if (writer == null) {
                throw new IllegalStateException("Step '" + stepName + "' resolved a null ItemWriter for target '" + t.getTargetName() + "'.");
            }
            ItemProcessor<Object, Object> processor = processorFactory.getProcessor(processorConfig, s, t, metadata);
            FileIngestionHardeningStepListener fileIngestionHardeningStepListener =
					new FileIngestionHardeningStepListener(s, processorConfig, mapping, fileIngestionRuntimeSupport);

            StepBuilder stepBuilder = new StepBuilder(stepName, jobRepository);
            Step step;
              StepExecutionListener writerStepExecutionListener = asStepExecutionListener(writer);
              StepExecutionListener jobHierarchyContextListener = jobHierarchyContextListener(jobStep);
            if (useChunk) {
                var chunkStepBuilder = stepBuilder
                        .chunk(chunkThreshold, transactionManager);
                  if (jobHierarchyContextListener != null) {
                      chunkStepBuilder.listener(jobHierarchyContextListener);
                }
                chunkStepBuilder
                        .listener(stepLoggingContextListener)
						.listener(fileIngestionHardeningStepListener)
                        .reader(reader)
                        .processor(processor)
                        .writer(writer);
                if (writerStepExecutionListener != null) {
                    chunkStepBuilder.listener(writerStepExecutionListener);
                }
                if (configuredSkipPolicy != null && configuredSkipPolicy.isEnabled()) {
                    FaultTolerantStepBuilder<Object, Object> faultTolerantBuilder = chunkStepBuilder
                            .faultTolerant()
                            .skipPolicy(configuredSkipPolicy(configuredSkipPolicy, stepName));
                    step = faultTolerantBuilder.build();
                    logger.info("STEP_READY event=skip_policy_enabled mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} skipLimit={} skippableCategories={} skippableExceptions={}",
                            runConfigurationMetadata.mainFlowName(),
                            stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                            runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                            stepName,
                            s.getSourceName(),
                            t.getTargetName(),
                            configuredSkipPolicy.getSkipLimit(),
                            configuredSkipPolicy.getSkippableCategories(),
                            configuredSkipPolicy.getSkippableExceptions());
                } else if (configuredRetryPolicy != null && configuredRetryPolicy.isEnabled()) {
                    FaultTolerantStepBuilder<Object, Object> faultTolerantBuilder = chunkStepBuilder
                            .faultTolerant()
                            .retryPolicy(configuredRetryPolicy(configuredRetryPolicy, stepName))
                            .backOffPolicy(configuredRetryBackOffPolicy(configuredRetryPolicy))
                            .listener(configuredRetryListener(configuredRetryPolicy, stepName, s, t, stepSubFlow));
                    step = faultTolerantBuilder.build();
                    logger.info("STEP_READY event=retry_policy_enabled mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} maxAttempts={} backoffMs={} retryableCategories={} retryableExceptions={}",
                            runConfigurationMetadata.mainFlowName(),
                            stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                            runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                            stepName,
                            s.getSourceName(),
                            t.getTargetName(),
                            configuredRetryPolicy.getMaxAttempts(),
                            configuredRetryPolicy.getBackoffMs(),
                            configuredRetryPolicy.getRetryableCategories(),
                            configuredRetryPolicy.getRetryableExceptions());
                } else {
                    step = chunkStepBuilder.build();
                }
                  logger.info("STEP_READY event=step_ready mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} mode=chunk recordCount={} threshold={}",
                          runConfigurationMetadata.mainFlowName(),
							stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                          runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                          stepName, s.getSourceName(), t.getTargetName(), recordCount, chunkThreshold);
            } else {
                  if (jobHierarchyContextListener != null) {
                      stepBuilder.listener(jobHierarchyContextListener);
                }
                stepBuilder
                        .listener(stepLoggingContextListener)
						.listener(fileIngestionHardeningStepListener);
                if (writerStepExecutionListener != null) {
                    stepBuilder.listener(writerStepExecutionListener);
                }
                step = stepBuilder.tasklet((contribution, chunkContext) -> {
                            Object item;
                            List<Object> buffer = new ArrayList<>();
                            int acceptedCount = 0;
                            boolean rejectHandlingEnabled = processorConfig.getRejectHandling() != null && processorConfig.getRejectHandling().isEnabled();
                            DuplicateResolver duplicateResolver = duplicateRule == null
                                    ? null
                                    : duplicateResolverFactory.create(duplicateRule, useEmbeddedDbDuplicateResolver);
                            recordOrderedDuplicateResolverEvidence(
                                    contribution,
                                    duplicateRule,
                                    resolvedOrderedDuplicateResolverMode,
                                    resolvedOrderedDuplicateResolverReason,
                                    resolvedRecordCount,
                                    chunkThreshold,
                                    stepName,
                                    s,
                                    t,
                                    stepSubFlow
                            );
                            ExecutionContext executionContext = contribution.getStepExecution().getExecutionContext();
                            boolean isReaderStream = reader instanceof ItemStream;
                            boolean isWriterStream = writer instanceof ItemStream;
                            if (isReaderStream) {
                                ((ItemStream) reader).open(executionContext);
                            }
                            if (isWriterStream) {
                                ((ItemStream) writer).open(executionContext);
                            }
                            try {
                                while ((item = reader.read()) != null) {
									contribution.incrementReadCount();
                                    if (duplicateResolver != null) {
                                        duplicateResolver.accept(item);
                                        continue;
                                    }
                                    Object processed = processor.process(item);
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
                                            boolean recorded = fileIngestionRuntimeSupport.recordRejected(discardedRecord.discardedRecord(), List.of(discardedRecord.issue()));
                                            if (!recorded) {
                                                throw new IllegalStateException("Ordered duplicate winner selection rejected a record but reject handling was not initialized for the current step.");
                                            }
                                        }
                                    }
                                    for (Object retainedRecord : resolution.retainedRecords()) {
                                        Object processed = processor.process(retainedRecord);
                                        if (processed == null) {
                                            contribution.incrementFilterCount(1);
                                            continue;
                                        }
                                        buffer.add(processed);
                                        acceptedCount++;
                                    }
                                }
                                if (!buffer.isEmpty()) {
                                    if (metadata.isWrapperRequired()) {
                                        Object wrapper = GeneratedModelClassResolver.createWrapper(metadata, buffer);
                                        logger.debug("Writing XML wrapper {}.{} with {} records",
                                                metadata.getTargetWriteClassName(),
                                                metadata.getWrapperFieldName(),
                                                buffer.size());
                                        writer.write(new Chunk<>(List.of(wrapper)));
                                    } else {
                                        writer.write(new Chunk<>(buffer));
                                    }
									contribution.incrementWriteCount(acceptedCount);
                                }
                            } finally {
                                if (duplicateResolver != null) {
                                    duplicateResolver.close();
                                }
                                if (isReaderStream) {
                                    ((ItemStream) reader).close();
                                }
                                if (isWriterStream) {
                                    ((ItemStream) writer).close();
                                }
                            }
                            return RepeatStatus.FINISHED;
                        }, transactionManager)
                        .build();
                  logger.info("STEP_READY event=step_ready mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} mode=tasklet recordCount={} threshold={}",
                          runConfigurationMetadata.mainFlowName(),
							stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                          runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                          stepName, s.getSourceName(), t.getTargetName(), recordCount, chunkThreshold);
            }
            steps.add(step);
        }

        logger.info("STEP_SEQUENCE event=step_sequence mainFlow={} subFlow={} recoveryPolicy={} plannedStepCount={} plannedSteps={}",
                runConfigurationMetadata.mainFlowName(),
                runConfigurationMetadata.subFlowName(),
                runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                plannedStepSequence.size(),
                JobHierarchyLoggingSupport.formatList(plannedStepSequence));

        return steps;
    }

    private Step buildCustomStep(JobConfig.JobStepConfig configuredStep,
                                 String stepName,
                                 int configuredIndex,
                                 Integer descriptorStepOrder,
                                 JobSubFlowDescriptor stepSubFlow,
                                 List<JobStepLinkDescriptor> inboundLinks) {
        String customType = configuredStep.getCustom() == null ? "" : configuredStep.getCustom().getType();
        logger.info("STEP_PLAN event=custom_step_plan mainFlow={} subFlow={} recoveryPolicy={} stepName={} stepKind=custom customType={} configuredIndex={} descriptorStepOrder={} stepSubFlowOrder={} dependsOnSubFlows={} consumesHandoffAliases={} producesHandoffAliases={} upstreamSteps={} linkTypes={} linkControlSummary={}",
                runConfigurationMetadata.mainFlowName(),
                stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                stepName,
                customType,
                configuredIndex,
                descriptorStepOrder == null ? -1 : descriptorStepOrder,
                stepSubFlow == null ? -1 : stepSubFlow.subFlowOrder(),
                JobHierarchyLoggingSupport.formatList(stepSubFlow == null ? List.of() : stepSubFlow.dependsOnSubFlowNames()),
                JobHierarchyLoggingSupport.formatList(stepSubFlow == null ? List.of() : stepSubFlow.consumesHandoffAliases()),
                JobHierarchyLoggingSupport.formatList(stepSubFlow == null ? List.of() : stepSubFlow.producesHandoffAliases()),
                JobHierarchyLoggingSupport.formatList(inboundLinks.stream().map(JobStepLinkDescriptor::fromStepName).toList()),
                JobHierarchyLoggingSupport.formatList(inboundLinks.stream().map(link -> link.linkType().name()).toList()),
                JobHierarchyLoggingSupport.formatList(inboundLinks.stream().map(link -> link.control().summary()).toList()));

        StepBuilder stepBuilder = new StepBuilder(stepName, jobRepository);
        StepExecutionListener jobHierarchyContextListener = jobHierarchyContextListener(jobRuntimeDescriptor == null ? null : jobRuntimeDescriptor.stepsByName().get(stepName));
        CustomStepHandler handler = customStepFactory.getHandler(stepName, configuredStep.getCustom());
        if (jobHierarchyContextListener != null) {
            stepBuilder.listener(jobHierarchyContextListener);
        }
        stepBuilder.listener(stepLoggingContextListener);
        Step step = stepBuilder.tasklet((contribution, chunkContext) -> {
                    logger.info("STEP_EVENT event=custom_step_started stepName={} stepExecutionId={} stepKind=custom customType={} configuredIndex={} descriptorStepOrder={}",
                            stepName,
                            contribution.getStepExecution().getId(),
                            customType,
                            configuredIndex,
                            descriptorStepOrder == null ? -1 : descriptorStepOrder);
                    RepeatStatus status = handler.execute(contribution, chunkContext);
                    logger.info("STEP_EVENT event=custom_step_finished stepName={} stepExecutionId={} stepKind=custom customType={} configuredIndex={} descriptorStepOrder={} repeatStatus={}",
                            stepName,
                            contribution.getStepExecution().getId(),
                            customType,
                            configuredIndex,
                            descriptorStepOrder == null ? -1 : descriptorStepOrder,
                            status == null ? RepeatStatus.FINISHED : status);
                    return status == null ? RepeatStatus.FINISHED : status;
                }, transactionManager)
                .build();

        logger.info("STEP_READY event=step_ready mainFlow={} subFlow={} recoveryPolicy={} stepName={} stepKind=custom customType={} mode=custom",
                runConfigurationMetadata.mainFlowName(),
                stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                stepName,
                customType);
        return step;
    }

    private Map<String, SourceConfig> mapSourcesByName(List<? extends SourceConfig> sources) {
        Map<String, SourceConfig> sourceByName = new LinkedHashMap<>();
        for (SourceConfig source : sources) {
            if (source.getSourceName() == null || source.getSourceName().isBlank()) {
                throw new IllegalStateException("Source configuration contains a blank sourceName.");
            }
            if (sourceByName.putIfAbsent(source.getSourceName(), source) != null) {
                throw new IllegalStateException("Duplicate sourceName found in source configuration: " + source.getSourceName());
            }
        }
        return sourceByName;
    }

  private ResolvedModelMetadata toResolvedModelMetadata(JobStepModelDescriptor modelDescriptor) {
    return new ResolvedModelMetadata(
        modelDescriptor.sourceClassName(),
        modelDescriptor.targetProcessingClassName(),
        modelDescriptor.targetWriteClassName(),
        modelDescriptor.wrapperRequired(),
        modelDescriptor.wrapperFieldName()
    );
  }

    private Map<String, TargetConfig> mapTargetsByName(List<TargetConfig> targets) {
        Map<String, TargetConfig> targetByName = new LinkedHashMap<>();
        for (TargetConfig target : targets) {
            if (target.getTargetName() == null || target.getTargetName().isBlank()) {
                throw new IllegalStateException("Target configuration contains a blank targetName.");
            }
            if (targetByName.putIfAbsent(target.getTargetName(), target) != null) {
                throw new IllegalStateException("Duplicate targetName found in target configuration: " + target.getTargetName());
            }
        }
        return targetByName;
    }

    private SourceConfig requireSource(JobConfig.JobStepConfig configuredStep, Map<String, SourceConfig> sourceByName) {
        SourceConfig sourceConfig = sourceByName.get(configuredStep.getSource());
        if (sourceConfig == null) {
            throw new IllegalStateException("Step '" + configuredStep.getName() + "' references unknown source '" + configuredStep.getSource() + "'.");
        }
        return sourceConfig;
    }

    private StepExecutionListener asStepExecutionListener(ItemWriter<Object> writer) {
        return writer instanceof StepExecutionListener stepExecutionListener ? stepExecutionListener : null;
    }

    private StepExecutionListener jobHierarchyContextListener(JobStepDescriptor jobStep) {
      if (jobRuntimeDescriptor == null || jobStep == null) {
      return null;
    }
    return new StepExecutionListener() {
      @Override
      public void beforeStep(@NonNull org.springframework.batch.core.StepExecution stepExecution) {
          JobHierarchyLoggingSupport.populateStepExecutionContext(stepExecution.getExecutionContext(), jobRuntimeDescriptor, jobStep);
      }

      @Override
      public org.springframework.batch.core.ExitStatus afterStep(@NonNull org.springframework.batch.core.StepExecution stepExecution) {
        return stepExecution.getExitStatus();
      }
    };
  }

    private void recordOrderedDuplicateResolverEvidence(StepContribution contribution,
                                                        DuplicateRule duplicateRule,
                                                        String resolverMode,
                                                        String resolverReason,
                                                        int recordCount,
                                                        int threshold,
                                                        String stepName,
                                                        SourceConfig sourceConfig,
                                                        TargetConfig targetConfig,
                                                        JobSubFlowDescriptor stepSubFlow) {
        if (duplicateRule == null || contribution == null) {
            return;
        }
        contribution.getStepExecution().getExecutionContext().putString(ORDERED_DUPLICATE_RESOLVER_MODE_KEY, resolverMode);
        contribution.getStepExecution().getExecutionContext().putString(ORDERED_DUPLICATE_RESOLVER_REASON_KEY, resolverReason);
        logger.info("STEP_EVENT event=duplicate_resolver_selected mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} duplicateStrategy=orderBy duplicateIdentityMode={} duplicateIdentityModeReason={} resolverMode={} resolverReason={} recordCount={} threshold={}",
                runConfigurationMetadata.mainFlowName(),
                stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                stepName,
                sourceConfig.getSourceName(),
                targetConfig.getTargetName(),
                duplicateRule.identityMode().configValue(),
                duplicateRule.identityModeReason(),
                resolverMode,
                resolverReason,
                recordCount,
                threshold);
    }

    private TargetConfig requireTarget(JobConfig.JobStepConfig configuredStep, Map<String, TargetConfig> targetByName) {
        TargetConfig targetConfig = targetByName.get(configuredStep.getTarget());
        if (targetConfig == null) {
            throw new IllegalStateException("Step '" + configuredStep.getName() + "' references unknown target '" + configuredStep.getTarget() + "'.");
        }
        return targetConfig;
    }

  private SkipPolicy configuredSkipPolicy(JobConfig.SkipPolicyConfig skipPolicy, String stepName) {
    return batchStepPolicySupport.configuredSkipPolicy(skipPolicy, stepName);
  }

  private org.springframework.retry.RetryPolicy configuredRetryPolicy(JobConfig.RetryPolicyConfig retryPolicy,
                                                                      String stepName) {
    return batchStepPolicySupport.configuredRetryPolicy(retryPolicy, stepName);
  }

  private FixedBackOffPolicy configuredRetryBackOffPolicy(JobConfig.RetryPolicyConfig retryPolicy) {
    return batchStepPolicySupport.configuredRetryBackOffPolicy(retryPolicy);
  }

  private RetryListener configuredRetryListener(JobConfig.RetryPolicyConfig retryPolicy,
                                                String stepName,
                                                SourceConfig sourceConfig,
                                                TargetConfig targetConfig,
                                                JobSubFlowDescriptor stepSubFlow) {
    return batchStepPolicySupport.configuredRetryListener(retryPolicy, stepName, sourceConfig, targetConfig, stepSubFlow);
  }

  private List<Class<? extends Throwable>> exceptionClassesForCategories(List<String> configuredCategories) {
    return batchStepPolicySupport.exceptionClassesForCategories(configuredCategories);
  }

      private ProcessorConfig.EntityMapping requireProcessorMapping(JobConfig.JobStepConfig configuredStep) {
        ProcessorConfig.EntityMapping mapping = processorConfig.getMappings() == null ? null : processorConfig.getMappings().stream()
            .filter(candidate -> configuredStep.getSource().equals(candidate.getSource())
                && configuredStep.getTarget().equals(candidate.getTarget()))
            .findFirst()
            .orElse(null);
        if (mapping == null) {
            throw new IllegalStateException("Step '" + configuredStep.getName() + "' does not have a matching processor mapping for source '"
                    + configuredStep.getSource() + "' and target '" + configuredStep.getTarget() + "'.");
        }
        return mapping;
    }
}
