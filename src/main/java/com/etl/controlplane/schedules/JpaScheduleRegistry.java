package com.etl.controlplane.schedules;

import com.etl.controlplane.persistence.jpa.repository.ScheduleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * R3 bridge: keeps schedule persistence behavior stable while JPA repositories are introduced.
 */
@Repository
@ConditionalOnProperty(name = "controlplane.schedules.persistence.mode", havingValue = "jpa")
public class JpaScheduleRegistry implements ScheduleRegistry {

	private final JdbcScheduleRegistry delegate;

	public JpaScheduleRegistry(JdbcTemplate jdbcTemplate,
	                          ScheduleRepository ignoredRepository,
	                          @Value("${controlplane.db.vendor:mysql}") String dbVendor,
	                          @Value("${spring.application.name:spring-etl-engine}") String applicationName) {
		this.delegate = new JdbcScheduleRegistry(jdbcTemplate, dbVendor, applicationName);
	}

	@Override
	public ScheduleView upsert(ScheduleView schedule) {
		return delegate.upsert(schedule);
	}

	@Override
	public Optional<ScheduleView> findByScheduleId(String scheduleId) {
		return delegate.findByScheduleId(scheduleId);
	}

	@Override
	public Optional<ScheduleView> findByScheduleKey(String scheduleKey) {
		return delegate.findByScheduleKey(scheduleKey);
	}

	@Override
	public List<ScheduleView> list(int limit) {
		return delegate.list(limit);
	}

	@Override
	public boolean tryAdvanceLastAcceptedDueAt(String scheduleId, Instant dueAt) {
		return delegate.tryAdvanceLastAcceptedDueAt(scheduleId, dueAt);
	}
}

