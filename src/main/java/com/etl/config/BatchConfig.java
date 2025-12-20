package com.etl.config;

import java.util.ArrayList;
import java.util.List;

import com.etl.common.util.DynamicBatchUtils;
import com.etl.config.source.SourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
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
	private final DynamicProcessorFactory dynamicProcessorFactory;
	private final ProcessorConfig processorConfig;

	public BatchConfig(SourceWrapper sourceWrapper, DynamicReaderFactory readerFactory,
					   DynamicWriterFactory writerFactory, JobRepository jobRepository,
					   PlatformTransactionManager transactionManager,
					   JobCompletionNotificationListener listener, DynamicProcessorFactory dynamicProcessorFactory,
					   ProcessorConfig processorConfig, TargetWrapper targetWrapper, DynamicProcessorFactory processorFactory) {
		this.sourceWrapper = sourceWrapper;
		this.readerFactory = readerFactory;
		this.targetWrapper = targetWrapper;
		this.writerFactory = writerFactory;
		this.processorFactory = processorFactory;
		this.jobRepository = jobRepository;
		this.transactionManager = transactionManager;
		this.listener = listener;
		this.dynamicProcessorFactory = dynamicProcessorFactory;
		this.processorConfig = processorConfig;

		logger.info("EtlJobConfiguration initialized.");
	}

	@Bean
	public Job etlJob() throws Exception {
		List<Step> steps = buildSteps();

		if (steps.isEmpty()) {
			throw new IllegalStateException("No steps were created. Cannot build Job.");
		}

		JobBuilder jobBuilder = new JobBuilder("etlJob", jobRepository).listener(listener);
		// Chain all steps dynamically
		Job job = jobBuilder.start(steps.get(0)).build();

		for (int i = 1; i < steps.size(); i++) {
			job = jobBuilder.start(steps.get(0)).next(steps.get(i)).build();
		}

		return job;
	}

	@SuppressWarnings("unchecked")
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

		// Assuming a one-to-one mapping: sources.get(i) -> targets.get(i)
		int count = Math.min(sources.size(), targets.size());

		for (int i = 0; i < count; i++) {
			SourceConfig s = sources.get(i);
			TargetConfig t = targets.get(i);

			String stepName = "etlStep_" + i + "_" + s.getSourceName();

			String sourceClass = s.getPackageName() + "." + s.getSourceName();
			String targetClass = t.getPackageName() + "." + t.getTargetName();

			ItemReader<?> reader = DynamicBatchUtils.getDynamicReader(readerFactory, s, sourceClass);
			ItemWriter<?> writer = DynamicBatchUtils.getDynamicWriter(writerFactory, t, targetClass);

			ItemProcessor<?, ?> processor =
					processorFactory.getProcessor(processorConfig, s, t); // your MappingEngine fits here

			Step step = new StepBuilder(stepName, jobRepository)
					.<Object, Object>chunk(50, transactionManager)
					.reader(reader)
					.processor((ItemProcessor<Object, Object>) processor)
					.writer((ItemWriter<Object>) writer)
					.build();

			logger.info("Created Step: {} ({} → {})", stepName, s.getSourceName(), t.getTargetName());

			steps.add(step);
		}

		return steps;
	}
}
