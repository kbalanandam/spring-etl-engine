package com.etl.controlplane.monitoring;

import com.etl.controlplane.persistence.jpa.repository.RunSummaryRepository;
import org.springframework.batch.core.StepExecution;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * R3 bridge: exposes JPA mode selection while preserving existing run-summary semantics.
 */
@Repository
@ConditionalOnProperty(name = "controlplane.runs.persistence.mode", havingValue = "jpa")
public class JpaRunSummaryRegistry implements RunSummaryRegistry {

	private final JdbcRunSummaryRegistry delegate;

	public JpaRunSummaryRegistry(JdbcTemplate jdbcTemplate,
	                            RunSummaryRepository ignoredRepository,
	                            @Value("${controlplane.runs.retention:5000}") int retention,
	                            @Value("${controlplane.db.vendor:mysql}") String dbVendor,
	                            @Value("${spring.application.name:spring-etl-engine}") String applicationName) {
		this.delegate = new JdbcRunSummaryRegistry(jdbcTemplate, retention, dbVendor, applicationName);
	}

	@Override
	public void upsert(RunSummaryView runSummary) {
		delegate.upsert(runSummary);
	}

	@Override
	public void upsertStepSnapshots(long jobExecutionId, Collection<? extends StepExecution> stepExecutions) {
		delegate.upsertStepSnapshots(jobExecutionId, stepExecutions);
	}

	@Override
	public Optional<LogReadCheckpoint> findLogCheckpoint(String logPath) {
		return delegate.findLogCheckpoint(logPath);
	}

	@Override
	public void upsertLogCheckpoint(String logPath, long offsetBytes, long fileSizeBytes, long fileLastModifiedMillis) {
		delegate.upsertLogCheckpoint(logPath, offsetBytes, fileSizeBytes, fileLastModifiedMillis);
	}

	@Override
	public List<RunSummaryView> latestRuns(int limit) {
		return delegate.latestRuns(limit);
	}

	@Override
	public Optional<RunSummaryView> findByJobExecutionId(long jobExecutionId) {
		return delegate.findByJobExecutionId(jobExecutionId);
	}

	@Override
	public Optional<RunRecoveryView> findRecoveryByJobExecutionId(long jobExecutionId) {
		return delegate.findRecoveryByJobExecutionId(jobExecutionId);
	}

	@Override
	public List<RunStepRecordView> listStepRecordsByJobExecutionId(long jobExecutionId, int limit) {
		return delegate.listStepRecordsByJobExecutionId(jobExecutionId, limit);
	}

	@Override
	public List<RunArtifactRecordView> listArtifactRecordsByJobExecutionId(long jobExecutionId, int limit) {
		return delegate.listArtifactRecordsByJobExecutionId(jobExecutionId, limit);
	}

	@Override
	public List<RunArtifactRecordView> listArtifactRecordsByStepRecordId(String stepRecordId, int limit) {
		return delegate.listArtifactRecordsByStepRecordId(stepRecordId, limit);
	}
}

