package com.etl.controlplane.persistence.jpa;

import com.etl.controlplane.persistence.jpa.entity.ControlPlanePkSequence;
import com.etl.controlplane.persistence.jpa.repository.ControlPlanePkSequenceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

/**
 * Allocates stable surrogate keys through the shared control-plane sequence table.
 */
@Component
public class JpaControlPlanePkAllocator {

	private final ControlPlanePkSequenceRepository sequenceRepository;
	private final String auditActor;

	public JpaControlPlanePkAllocator(ControlPlanePkSequenceRepository sequenceRepository,
	                                @Value("${spring.application.name:spring-etl-engine}") String applicationName) {
		this.sequenceRepository = sequenceRepository;
		String normalized = applicationName == null ? "" : applicationName.trim();
		this.auditActor = normalized.isBlank() ? "spring-etl-engine" : normalized;
	}

	@Transactional
	public synchronized long nextPk(String sequenceName) {
		String normalizedName = normalizeSequenceName(sequenceName);
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		ControlPlanePkSequence sequence = sequenceRepository.findById(normalizedName).orElse(null);
		if (sequence == null) {
			ControlPlanePkSequence created = new ControlPlanePkSequence();
			created.setSequenceName(normalizedName);
			created.setNextValue(2L);
			created.setUpdatedAt(now);
			created.setCreatedBy(auditActor);
			created.setUpdatedBy(auditActor);
			sequenceRepository.save(created);
			return 1L;
		}

		long current = sequence.getNextValue() == null || sequence.getNextValue() < 1L ? 1L : sequence.getNextValue();
		sequence.setNextValue(current + 1L);
		sequence.setUpdatedAt(now);
		sequence.setUpdatedBy(auditActor);
		sequenceRepository.save(sequence);
		return current;
	}

	private String normalizeSequenceName(String sequenceName) {
		String normalized = sequenceName == null ? "" : sequenceName.trim().toLowerCase(Locale.ROOT);
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("Sequence name must not be blank.");
		}
		return normalized;
	}
}

