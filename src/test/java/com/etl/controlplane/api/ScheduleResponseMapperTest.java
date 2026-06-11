package com.etl.controlplane.api;

import com.etl.controlplane.schedules.ScheduleView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScheduleResponseMapperTest {

    @Test
    void mapsScheduleViewToApiPayloadWithNextDueAtWhenExpressionIsValid() {
        ScheduleResponseMapper mapper = new ScheduleResponseMapper(
                Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneOffset.UTC)
        );
        ScheduleView schedule = schedule("0 * * * *", "UTC", false);

        ScheduleViewResponse response = mapper.toViewResponse(schedule);

        assertEquals("sch-1", response.scheduleId());
        assertEquals("daily-customers", response.scheduleKey());
        assertNotNull(response.nextDueAt());
    }

    @Test
    void keepsScheduleReadableWhenExpressionIsInvalid() {
        ScheduleResponseMapper mapper = new ScheduleResponseMapper(
                Clock.fixed(Instant.parse("2026-05-28T10:00:00Z"), ZoneOffset.UTC)
        );
        ScheduleView schedule = schedule("not-a-cron", "UTC", false);

        ScheduleViewResponse response = mapper.toViewResponse(schedule);

        assertEquals("sch-1", response.scheduleId());
        assertNull(response.nextDueAt());
    }

    private ScheduleView schedule(String expression, String timezone, boolean paused) {
        return new ScheduleView(
                "sch-1",
                "daily-customers",
                "customer-load",
                expression,
                timezone,
                true,
                paused,
                "daily",
                LocalDateTime.parse("2026-05-28T08:00:00"),
                LocalDateTime.parse("2026-05-28T09:00:00"),
                null,
                null
        );
    }
}

