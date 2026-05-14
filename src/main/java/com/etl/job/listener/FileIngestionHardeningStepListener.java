package com.etl.job.listener;

import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.lang.NonNull;

/**
 * Step listener bridge for file-ingestion hardening concerns.
 *
 * <p>This listener binds the active step's source, processor, and entity mapping into
 * {@link FileIngestionRuntimeSupport}. It is responsible only for lifecycle delegation;
 * reject handling, duplicate tracking, and archive-on-success behavior remain in the
 * shared runtime support component.</p>
 */
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
	public void beforeStep(@NonNull StepExecution stepExecution) {
		fileIngestionRuntimeSupport.initializeStep(stepExecution, sourceConfig, processorConfig, entityMapping);
	}

	@Override
	public ExitStatus afterStep(@NonNull StepExecution stepExecution) {
		return fileIngestionRuntimeSupport.completeStep(stepExecution, sourceConfig);
	}
}
