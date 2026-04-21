package com.etl.config;

import java.util.ArrayList;
import java.util.List;

import com.etl.common.util.DynamicBatchUtils;
import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.ResolvedModelMetadata;
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
    private final ProcessorConfig processorConfig;

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
                       ProcessorConfig processorConfig, TargetWrapper targetWrapper) {
        this.sourceWrapper = sourceWrapper;
        this.readerFactory = readerFactory;
        this.targetWrapper = targetWrapper;
        this.writerFactory = writerFactory;
        this.processorFactory = processorFactory;
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.listener = listener;
        this.processorConfig = processorConfig;

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
    private List<Step> buildSteps() throws Exception {
        List<Step> steps = new ArrayList<>();
        List<? extends SourceConfig> sources = sourceWrapper.getSources();
        List<TargetConfig> targets = targetWrapper.getTargets();

        if (sources == null || sources.isEmpty()) {
            throw new IllegalStateException("No source configurations found.");
        }
        if (targets == null || targets.isEmpty()) {
            throw new IllegalStateException("No target configurations found.");
        }

        int count = Math.min(sources.size(), targets.size());

        for (int i = 0; i < count; i++) {
            SourceConfig s = sources.get(i);
            TargetConfig t = targets.get(i);

            String stepName = "etlStep_" + i + "_" + s.getSourceName();

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
                        .reader(reader)
                        .processor(processor)
                        .writer(writer)
                        .build();
                logger.info("Created CHUNK Step: {} ({} → {}), recordCount={}, threshold={}", stepName, s.getSourceName(), t.getTargetName(), recordCount, chunkThreshold);
            } else {
                step = stepBuilder
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
                                        logger.info("[DEBUG] Writing wrapper: {}.{} with {} records",
                                                metadata.getTargetWriteClassName(),
                                                metadata.getWrapperFieldName(),
                                                buffer.size());
                                        for (Object rec : buffer) {
                                            logger.info("[DEBUG] Record type: {}", rec.getClass().getName());
                                        }
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
                logger.info("Created TASKLET Step: {} ({} → {}), recordCount={}, threshold={}", stepName, s.getSourceName(), t.getTargetName(), recordCount, chunkThreshold);
            }
            steps.add(step);
        }

        return steps;
    }
}
