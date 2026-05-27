package com.etl.controlplane.triggers;

import org.springframework.beans.factory.annotation.Value;
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
public class InMemoryTriggerEventRegistry implements TriggerEventRegistry {

	private final int retentionPerJob;
	private final Map<String, Deque<TriggerEventView>> eventsByJobKey = new ConcurrentHashMap<>();

	public InMemoryTriggerEventRegistry(@Value("${controlplane.triggers.retention-per-job:100}") int retentionPerJob) {
		this.retentionPerJob = Math.max(1, retentionPerJob);
	}

	@Override
	public TriggerEventView recordAccepted(String jobKey, String reason, String requestedBy, String message) {
		String normalizedJobKey = normalize(jobKey);
		TriggerEventView event = new TriggerEventView(
				"te-" + UUID.randomUUID(),
				normalizedJobKey,
				"ACCEPTED",
				normalize(reason),
				normalize(requestedBy),
				Instant.now(),
				null,
				message
		);
		Deque<TriggerEventView> queue = eventsByJobKey.computeIfAbsent(normalizedJobKey, ignored -> new ArrayDeque<>());
		synchronized (queue) {
			queue.addFirst(event);
			while (queue.size() > retentionPerJob) {
				queue.removeLast();
			}
		}
		return event;
	}

	@Override
	public List<TriggerEventView> listByJobKey(String jobKey, int limit) {
		Deque<TriggerEventView> queue = eventsByJobKey.get(normalize(jobKey));
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

