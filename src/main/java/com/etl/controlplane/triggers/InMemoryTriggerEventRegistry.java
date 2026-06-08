package com.etl.controlplane.triggers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounded in-memory trigger-event registry for early UI/control-plane work.
 */
@Component
@ConditionalOnProperty(name = "controlplane.triggers.persistence.mode", havingValue = "memory", matchIfMissing = true)
public class InMemoryTriggerEventRegistry implements TriggerEventRegistry {

	private final int retentionPerJob;
	private final Map<String, Deque<TriggerEventView>> eventsByJobKey = new ConcurrentHashMap<>();
	private final Map<String, Deque<TriggerEventView>> eventsByScheduleId = new ConcurrentHashMap<>();

	public InMemoryTriggerEventRegistry(@Value("${controlplane.triggers.retention-per-job:100}") int retentionPerJob) {
		this.retentionPerJob = Math.max(1, retentionPerJob);
	}

	@Override
	public TriggerEventView recordAccepted(String jobKey, String reason, String requestedBy, String message) {
		return recordAcceptedInternal(null, "MANUAL", jobKey, reason, requestedBy, message);
	}

	@Override
	public TriggerEventView recordAcceptedForSchedule(String scheduleId, String jobKey, String reason, String requestedBy, String message) {
		return recordAcceptedInternal(scheduleId, "SCHEDULE", jobKey, reason, requestedBy, message);
	}

	private TriggerEventView recordAcceptedInternal(String scheduleId,
	                                              String triggerOrigin,
	                                              String jobKey,
	                                              String reason,
	                                              String requestedBy,
	                                              String message) {
		String normalizedJobKey = normalize(jobKey);
		String normalizedScheduleId = normalize(scheduleId);
		TriggerEventView event = new TriggerEventView(
				"te-" + UUID.randomUUID(),
				normalizedJobKey,
				"ACCEPTED",
				normalize(reason),
				normalize(requestedBy),
				Instant.now(),
				null,
				message,
				normalize(triggerOrigin)
		);
		Deque<TriggerEventView> queue = eventsByJobKey.computeIfAbsent(normalizedJobKey, ignored -> new ArrayDeque<>());
		synchronized (queue) {
			queue.addFirst(event);
			while (queue.size() > retentionPerJob) {
				queue.removeLast();
			}
		}
		if (!normalizedScheduleId.isBlank()) {
			Deque<TriggerEventView> scheduleQueue = eventsByScheduleId.computeIfAbsent(normalizedScheduleId, ignored -> new ArrayDeque<>());
			synchronized (scheduleQueue) {
				scheduleQueue.addFirst(event);
				while (scheduleQueue.size() > retentionPerJob) {
					scheduleQueue.removeLast();
				}
			}
		}
		return event;
	}

	@Override
	public List<TriggerEventView> listByJobKey(String jobKey, int limit) {
		return listFromQueue(eventsByJobKey.get(normalize(jobKey)), limit);
	}

	@Override
	public List<TriggerEventView> listByScheduleId(String scheduleId, int limit) {
		return listFromQueue(eventsByScheduleId.get(normalize(scheduleId)), limit);
	}

	private List<TriggerEventView> listFromQueue(Deque<TriggerEventView> queue, int limit) {
		if (queue == null || limit <= 0) {
			return List.of();
		}
		synchronized (queue) {
			return queue.stream()
					.sorted(Comparator.comparing(TriggerEventView::requestedAt).reversed())
					.limit(limit)
					.toList();
		}
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}

