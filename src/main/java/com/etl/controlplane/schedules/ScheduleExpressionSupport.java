package com.etl.controlplane.schedules;

import org.springframework.scheduling.support.CronExpression;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Shared schedule expression helpers for validation and API projection.
 */
public final class ScheduleExpressionSupport {

	private ScheduleExpressionSupport() {
	}

	public static CronExpression parseCron(String expression) {
		String normalized = normalize(expression);
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("expression is required");
		}
		String[] fields = normalized.split("\\s+");
		if (fields.length == 5) {
			return CronExpression.parse("0 " + normalized);
		}
		if (fields.length == 6) {
			return CronExpression.parse(normalized);
		}
		throw new IllegalArgumentException("unsupported cron field count");
	}

	public static ZoneId parseZoneId(String timezone) {
		String normalized = normalize(timezone);
		if (normalized.isBlank()) {
			throw new IllegalArgumentException("timezone is required");
		}
		try {
			return ZoneId.of(normalized);
		} catch (Exception ex) {
			throw new IllegalArgumentException("invalid timezone", ex);
		}
	}

	public static Instant resolveNextDueAt(String expression,
	                                      String timezone,
	                                      boolean enabled,
	                                      boolean paused,
	                                      Clock clock) {
		if (!enabled || paused) {
			return null;
		}
		CronExpression cron = parseCron(expression);
		ZoneId zoneId = parseZoneId(timezone);
		ZonedDateTime now = ZonedDateTime.now(clock == null ? Clock.systemUTC() : clock).withZoneSameInstant(zoneId);
		ZonedDateTime next = cron.next(now);
		return next == null ? null : next.toInstant();
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}


