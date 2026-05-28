package com.etl.controlplane.triggers;

import java.util.List;

public interface TriggerEventRegistry {

	TriggerEventView recordAccepted(String jobKey, String reason, String requestedBy, String message);

	TriggerEventView recordAcceptedForSchedule(String scheduleId, String jobKey, String reason, String requestedBy, String message);

	List<TriggerEventView> listByJobKey(String jobKey, int limit);

	List<TriggerEventView> listByScheduleId(String scheduleId, int limit);
}

