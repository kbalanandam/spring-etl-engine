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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.builder.SimpleJobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.*;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 */
@Configuration
@EnableBatchProcessing
public class BatchConfig {

    private static final Logger logger = LoggerFactory.getLogger(BatchConfig.class);

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

    /**
     * The threshold for switching between chunk and tasklet processing.
     * If the source record count exceeds this value, chunk processing is used.
     */
    @Value("${etl.chunk.threshold:10000}")
    private int chunkThreshold;

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
    public BatchConfig(SourceWrapper sourceWrapper, DynamicReaderFactory readerFactory,
                       DynamicWriterFactory writerFactory, JobRepository jobRepository,
                       PlatformTransactionManager transactionManager,
                       JobCompletionNotificationListener listener, DynamicProcessorFactory processorFactory,
                       ProcessorConfig processorConfig, TargetWrapper targetWrapper,
                       StepLoggingContextListener stepLoggingContextListener,
                       RunConfigurationMetadata runConfigurationMetadata) {
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

        logger.info("EtlJobConfiguration initialized.");
    }

    /**
     * Defines the main ETL job bean.
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
     * @return the list of configured steps
     * @throws Exception if step creation fails
     */
    List<Step> buildSteps() throws Exception {
        List<Step> steps = new ArrayList<>();
        List<? extends SourceConfig> sources = sourceWrapper.getSources();
        List<TargetConfig> targets = targetWrapper.getTargets();
        List<JobConfig.JobStepConfig> configuredSteps = runConfigurationMetadata.steps();

        if (sources == null || sources.isEmpty()) {
            throw new IllegalStateException("No source configurations found.");
        }
        if (targets == null || targets.isEmpty()) {
            throw new IllegalStateException("No target configurations found.");
        }
        if (configuredSteps == null || configuredSteps.isEmpty()) {
            throw new IllegalStateException("No explicit job steps were resolved for scenario '" + runConfigurationMetadata.scenarioName() + "'.");
        }

        logger.info("Building ETL job for scenario '{}' with {} explicit steps.", runConfigurationMetadata.scenarioName(), configuredSteps.size());

        Map<String, SourceConfig> sourceByName = mapSourcesByName(sources);
        Map<String, TargetConfig> targetByName = mapTargetsByName(targets);

        for (int i = 0; i < configuredSteps.size(); i++) {
            JobConfig.JobStepConfig configuredStep = configuredSteps.get(i);
            SourceConfig s = requireSource(configuredStep, sourceByName);
            TargetConfig t = requireTarget(configuredStep, targetByName);
            requireProcessorMapping(configuredStep);

            String stepName = configuredStep.getName();
            logger.info("STEP_PLAN event=step_plan stepName={} source={} target={} stepOrder={}", stepName, configuredStep.getSource(), configuredStep.getTarget(), i);

            boolean useChunk;
            int recordCount;
            try {
                recordCount = s.getRecordCount();
            } catch (Exception e) {
                logger.warn("Could not count records for source: {}. Defaulting to chunk mode.", s.getSourceName(), e);
                recordCount = chunkThreshold + 1;
            }
            if (recordCount < 0) {
                logger.info("Record count is unknown for source: {}. Defaulting to chunk mode.", s.getSourceName());
                recordCount = chunkThreshold + 1;
            }
            useChunk = recordCount > chunkThreshold;

            ResolvedModelMetadata metadata = GeneratedModelClassResolver.resolveMetadata(s, t);
            ItemReader<Object> reader = DynamicBatchUtils.getDynamicReader(readerFactory, s, metadata);
            Class<?> writerClass = metadata.isWrapperRequired() && useChunk
                    ? GeneratedModelClassResolver.resolveTargetProcessingClass(metadata)
                    : GeneratedModelClassResolver.resolveTargetWriteClass(metadata);
            ItemWriter<Object> writer = DynamicBatchUtils.getDynamicWriter(writerFactory, t, writerClass);
            ItemProcessor<Object, Object> processor = processorFactory.getProcessor(processorConfig, s, t, metadata);

            StepBuilder stepBuilder = new StepBuilder(stepName, jobRepository);
            Step step;
            if (useChunk) {
                step = stepBuilder
                        .chunk(chunkThreshold, transactionManager)
                        .listener(stepLoggingContextListener)
                        .reader(reader)
                        .processor(processor)
                        .writer(writer)
                        .build();
                logger.info("STEP_READY event=step_ready stepName={} source={} target={} mode=chunk recordCount={} threshold={}", stepName, s.getSourceName(), t.getTargetName(), recordCount, chunkThreshold);
            } else {
                step = stepBuilder
                        .listener(stepLoggingContextListener)
                        .tasklet((contribution, chunkContext) -> {
                            Object item;
                            List<Object> buffer = new ArrayList<>();
                            ExecutionContext executionContext = new ExecutionContext();
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
                                    Object processed = processor.process(item);
                                    buffer.add(processed);
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
                                }
                            } finally {
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
                logger.info("STEP_READY event=step_ready stepName={} source={} target={} mode=tasklet recordCount={} threshold={}", stepName, s.getSourceName(), t.getTargetName(), recordCount, chunkThreshold);
            }
            steps.add(step);
        }

        return steps;
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

    private TargetConfig requireTarget(JobConfig.JobStepConfig configuredStep, Map<String, TargetConfig> targetByName) {
        TargetConfig targetConfig = targetByName.get(configuredStep.getTarget());
        if (targetConfig == null) {
            throw new IllegalStateException("Step '" + configuredStep.getName() + "' references unknown target '" + configuredStep.getTarget() + "'.");
        }
        return targetConfig;
    }

    private void requireProcessorMapping(JobConfig.JobStepConfig configuredStep) {
        boolean mappingExists = processorConfig.getMappings() != null && processorConfig.getMappings().stream()
                .anyMatch(mapping -> configuredStep.getSource().equals(mapping.getSource())
                        && configuredStep.getTarget().equals(mapping.getTarget()));
        if (!mappingExists) {
            throw new IllegalStateException("Step '" + configuredStep.getName() + "' does not have a matching processor mapping for source '"
                    + configuredStep.getSource() + "' and target '" + configuredStep.getTarget() + "'.");
        }
    }
}
