package com.etl.runtime;

import com.etl.processor.validation.DuplicateProcessorValidationRule;
import com.etl.processor.validation.ValidationIssue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryDuplicateResolverTest {

	@Test
	void keepsBestRecordForDuplicateKeyUsingConfiguredDescendingOrder() {
		InMemoryDuplicateResolver resolver = new InMemoryDuplicateResolver(
				new DuplicateRule(
						"id",
						List.of("id"),
						List.of(new DuplicateProcessorValidationRule.OrderSelector("eventTime", true))
				)
		);

		resolver.accept(new EventRecord("EVT-1001", "08:30:00", "first", 1));
		resolver.accept(new EventRecord("EVT-1002", "07:15:00", "other-key", 5));
		resolver.accept(new EventRecord("EVT-1001", "09:45:00", "latest", 2));
		resolver.accept(new EventRecord("EVT-1001", "09:00:00", "older", 3));
		DuplicateResolution resolution = resolver.complete();

		assertEquals(2, resolution.discardedRecords().size());
		assertEquals("older", ((EventRecord) resolution.discardedRecords().get(0).discardedRecord()).description());
		assertEquals("first", ((EventRecord) resolution.discardedRecords().get(1).discardedRecord()).description());

		List<Object> retainedRecords = resolution.retainedRecords();
		assertEquals(2, retainedRecords.size());
		assertEquals("other-key", ((EventRecord) retainedRecords.get(0)).description());
		assertEquals("latest", ((EventRecord) retainedRecords.get(1)).description());
	}

	@Test
	void supportsNonTimestampOrderingSuchAsIntegerDescending() {
		InMemoryDuplicateResolver resolver = new InMemoryDuplicateResolver(
				new DuplicateRule(
						"id",
						List.of("id"),
						List.of(new DuplicateProcessorValidationRule.OrderSelector("sequenceNo", true))
				)
		);

		resolver.accept(new EventRecord("EVT-1001", "08:30:00", "low-seq", 10));
		resolver.accept(new EventRecord("EVT-1001", "08:20:00", "high-seq", 25));
		DuplicateResolution resolution = resolver.complete();

		assertEquals(1, resolution.discardedRecords().size());
		assertEquals("low-seq", ((EventRecord) resolution.discardedRecords().get(0).discardedRecord()).description());
		assertEquals("high-seq", ((EventRecord) resolution.retainedRecords().get(0)).description());
	}

	@Test
	void keepsFirstReceivedRecordWhenConfiguredOrderTies() {
		InMemoryDuplicateResolver resolver = new InMemoryDuplicateResolver(
				new DuplicateRule(
						"id",
						List.of("id"),
						List.of(new DuplicateProcessorValidationRule.OrderSelector("eventTime", true))
				)
		);

		resolver.accept(new EventRecord("EVT-1001", "08:30:00", "first", 1));
		resolver.accept(new EventRecord("EVT-1001", "08:30:00", "last-received", 1));
		DuplicateResolution resolution = resolver.complete();

		assertEquals(1, resolution.discardedRecords().size());
		assertEquals("last-received", ((EventRecord) resolution.discardedRecords().get(0).discardedRecord()).description());
		assertEquals("first", ((EventRecord) resolution.retainedRecords().get(0)).description());
	}

	@Test
	void returnsInvalidOrderingDecisionWhenConfiguredSortFieldIsMissing() {
		InMemoryDuplicateResolver resolver = new InMemoryDuplicateResolver(
				new DuplicateRule(
						"id",
						List.of("id"),
						List.of(new DuplicateProcessorValidationRule.OrderSelector("eventTime", true))
				)
		);

		resolver.accept(new EventRecord("EVT-1001", null, "broken", 1));
		DuplicateResolution resolution = resolver.complete();
		DuplicateDiscard decision = resolution.discardedRecords().get(0);

		assertEquals(1, resolution.discardedRecords().size());
		assertTrue(decision.invalidOrderingValue());
		ValidationIssue issue = decision.issue();
		assertNotNull(issue);
		assertEquals("duplicate", issue.rule());
		assertTrue(issue.message().contains("comparable"));
	}

	@Test
	void bypassesDeduplicationWhenKeyIsIncomplete() {
		InMemoryDuplicateResolver resolver = new InMemoryDuplicateResolver(
				new DuplicateRule(
						"id",
						List.of("id", "lane"),
						List.of(new DuplicateProcessorValidationRule.OrderSelector("eventTime", true))
				)
		);

		resolver.accept(new LaneEventRecord("EVT-1001", "", "08:30:00", "missing-lane"));
		resolver.accept(new LaneEventRecord("EVT-1001", "", "09:30:00", "still-missing-lane"));
		DuplicateResolution resolution = resolver.complete();

		assertTrue(resolution.discardedRecords().isEmpty());
		assertEquals(2, resolution.retainedRecords().size());
	}

	private record EventRecord(String id, String eventTime, String description, Integer sequenceNo) {
	}

	private record LaneEventRecord(String id, String lane, String eventTime, String description) {
	}
}





