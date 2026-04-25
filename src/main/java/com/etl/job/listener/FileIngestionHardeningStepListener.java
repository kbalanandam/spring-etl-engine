package com.etl.job.listener;

import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;

public class FileIngestionHardeningStepListener implements StepExecutionListener {

	private final SourceConfig sourceConfig;
	private final ProcessorConfig processorConfig;
	private final ProcessorConfig.EntityMapping entityMapping;
	private final FileIngestionRuntimeSupport fileIngestionRuntimeSupport;

	public FileIngestionHardeningStepListener(SourceConfig sourceConfig,
	                                       ProcessorConfig processorConfig,
	                                       ProcessorConfig.EntityMapping entityMapping,
	                                       FileIngestionRuntimeSupport fileIngestionRuntimeSupport) {
		this.sourceConfig = sourceConfig;
		this.processorConfig = processorConfig;
		this.entityMapping = entityMapping;
		this.fileIngestionRuntimeSupport = fileIngestionRuntimeSupport;
	}

	@Override
	public void beforeStep(StepExecution stepExecution) {
		fileIngestionRuntimeSupport.initializeStep(stepExecution, sourceConfig, processorConfig, entityMapping);
	}

	@Override
	public ExitStatus afterStep(StepExecution stepExecution) {
		return fileIngestionRuntimeSupport.completeStep(stepExecution, sourceConfig);
	}
}
