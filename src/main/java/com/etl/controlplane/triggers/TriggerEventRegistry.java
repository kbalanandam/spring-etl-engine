package com.etl.controlplane.triggers;

import java.util.List;

public interface TriggerEventRegistry {

	TriggerEventView recordAccepted(String jobKey, String reason, String requestedBy, String message);

	TriggerEventView recordAcceptedForSchedule(String scheduleId, String jobKey, String reason, String requestedBy, String message);

	List<TriggerEventView> listByJobKey(String jobKey, int limit);

	default List<TriggerEventView> listByJobKey(String jobKey, int offset, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		int safeOffset = Math.max(0, offset);
		int fetchSize = safeOffset + limit;
		List<TriggerEventView> events = listByJobKey(jobKey, fetchSize);
		if (safeOffset >= events.size()) {
			return List.of();
		}
		int toIndex = Math.min(events.size(), safeOffset + limit);
		return events.subList(safeOffset, toIndex);
	}

	default long countByJobKey(String jobKey) {
		return listByJobKey(jobKey, Integer.MAX_VALUE).size();
	}

	List<TriggerEventView> listByScheduleId(String scheduleId, int limit);

	default List<TriggerEventView> listByScheduleId(String scheduleId, int offset, int limit) {
		if (limit <= 0) {
			return List.of();
		}
		int safeOffset = Math.max(0, offset);
		int fetchSize = safeOffset + limit;
		List<TriggerEventView> events = listByScheduleId(scheduleId, fetchSize);
		if (safeOffset >= events.size()) {
			return List.of();
		}
		int toIndex = Math.min(events.size(), safeOffset + limit);
		return events.subList(safeOffset, toIndex);
	}

	default long countByScheduleId(String scheduleId) {
		return listByScheduleId(scheduleId, Integer.MAX_VALUE).size();
	}
}

