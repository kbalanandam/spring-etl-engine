package com.etl.controlplane.api;

import com.etl.controlplane.schedules.ScheduleExpressionSupport;
import com.etl.controlplane.schedules.ScheduleView;

import java.time.Clock;
import java.time.Instant;

/**
 * Maps internal schedule projections to API response payloads.
 */
final class ScheduleResponseMapper {

    private final Clock responseClock;

    ScheduleResponseMapper(Clock responseClock) {
        this.responseClock = responseClock;
    }

    ScheduleViewResponse toViewResponse(ScheduleView schedule) {
        Instant nextDueAt = null;
        try {
            nextDueAt = ScheduleExpressionSupport.resolveNextDueAt(
                    schedule.expression(),
                    schedule.timezone(),
                    schedule.enabled(),
                    schedule.paused(),
                    responseClock
            );
        } catch (IllegalArgumentException ignored) {
            // Keep existing persisted schedules readable even if authored expression/timezone is invalid.
        }
        return new ScheduleViewResponse(
                schedule.scheduleId(),
                schedule.scheduleKey(),
                schedule.selectedJobKey(),
                schedule.expression(),
                schedule.timezone(),
                schedule.enabled(),
                schedule.paused(),
                schedule.description(),
                schedule.updatedAt(),
                schedule.lastAcceptedDueAt(),
                nextDueAt
        );
    }

    ScheduleStateChangeResponse toStateChangeResponse(ScheduleView schedule) {
        return new ScheduleStateChangeResponse(
                schedule.scheduleId(),
                schedule.enabled(),
                schedule.paused(),
                schedule.updatedAt()
        );
    }
}

