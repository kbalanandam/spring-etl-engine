package com.etl.controlplane.triggers;

import com.etl.controlplane.persistence.jpa.repository.TriggerEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * R3 bridge: exposes a JPA-selected mode while preserving proven trigger semantics.
 */
@Repository
@ConditionalOnProperty(name = "controlplane.triggers.persistence.mode", havingValue = "jpa")
public class JpaTriggerEventRegistry implements TriggerEventRegistry {

	private final JdbcTriggerEventRegistry delegate;

	public JpaTriggerEventRegistry(JdbcTemplate jdbcTemplate,
	                              TriggerEventRepository ignoredRepository,
	                              @Value("${controlplane.triggers.retention-per-job:100}") int retentionPerJob,
	                              @Value("${controlplane.db.vendor:mysql}") String dbVendor,
	                              @Value("${spring.application.name:spring-etl-engine}") String applicationName) {
		this.delegate = new JdbcTriggerEventRegistry(jdbcTemplate, retentionPerJob, dbVendor, applicationName);
	}

	@Override
	public TriggerEventView recordAccepted(String jobKey, String reason, String requestedBy, String message) {
		return delegate.recordAccepted(jobKey, reason, requestedBy, message);
	}

	@Override
	public TriggerEventView recordAcceptedForSchedule(String scheduleId, String jobKey, String reason, String requestedBy, String message) {
		return delegate.recordAcceptedForSchedule(scheduleId, jobKey, reason, requestedBy, message);
	}

	@Override
	public List<TriggerEventView> listByJobKey(String jobKey, int limit) {
		return delegate.listByJobKey(jobKey, limit);
	}

	@Override
	public List<TriggerEventView> listByJobKey(String jobKey, int offset, int limit) {
		return delegate.listByJobKey(jobKey, offset, limit);
	}

	@Override
	public long countByJobKey(String jobKey) {
		return delegate.countByJobKey(jobKey);
	}

	@Override
	public List<TriggerEventView> listByScheduleId(String scheduleId, int limit) {
		return delegate.listByScheduleId(scheduleId, limit);
	}

	@Override
	public List<TriggerEventView> listByScheduleId(String scheduleId, int offset, int limit) {
		return delegate.listByScheduleId(scheduleId, offset, limit);
	}

	@Override
	public long countByScheduleId(String scheduleId) {
		return delegate.countByScheduleId(scheduleId);
	}
}

