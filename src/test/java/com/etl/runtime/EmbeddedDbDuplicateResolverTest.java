package com.etl.runtime;

import com.etl.processor.validation.DuplicateProcessorValidationRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbDuplicateResolverTest {

	@Test
	void keepsBestRecordPerKeyUsingStructuredOrder() {
		EmbeddedDbDuplicateResolver resolver = new EmbeddedDbDuplicateResolver(
				new DuplicateRule(
						"id",
						List.of("id"),
						List.of(
								new DuplicateProcessorValidationRule.OrderSelector("eventTime", true),
								new DuplicateProcessorValidationRule.OrderSelector("sequenceNo", false)
						)
				)
		);
		try {
			resolver.accept(event("EVT-1001", "08:30:00", "first", 5));
			resolver.accept(event("EVT-1001", "09:45:00", "winner", 3));
			resolver.accept(event("EVT-1001", "09:45:00", "loser", 7));
			resolver.accept(event("EVT-1002", "07:15:00", "other-key", 1));

			DuplicateResolution resolution = resolver.complete();

			assertEquals(2, resolution.retainedRecords().size());
			assertEquals("winner", ((EventBean) resolution.retainedRecords().get(0)).getDescription());
			assertEquals("other-key", ((EventBean) resolution.retainedRecords().get(1)).getDescription());
			assertEquals(2, resolution.discardedRecords().size());
			assertTrue(resolution.discardedRecords().stream().anyMatch(discard -> ((EventBean) discard.discardedRecord()).getDescription().equals("first")));
			assertTrue(resolution.discardedRecords().stream().anyMatch(discard -> ((EventBean) discard.discardedRecord()).getDescription().equals("loser")));
		} finally {
			resolver.close();
		}
	}

	@Test
	void preservesKeepFirstTieBehaviorThroughEmbeddedDbStaging() {
		EmbeddedDbDuplicateResolver resolver = new EmbeddedDbDuplicateResolver(
				new DuplicateRule(
						"id",
						List.of("id"),
						List.of(new DuplicateProcessorValidationRule.OrderSelector("eventTime", true))
				)
		);
		try {
			resolver.accept(event("EVT-1001", "08:30:00", "first", 1));
			resolver.accept(event("EVT-1001", "08:30:00", "second", 1));

			DuplicateResolution resolution = resolver.complete();

			assertEquals(1, resolution.retainedRecords().size());
			assertEquals("first", ((EventBean) resolution.retainedRecords().get(0)).getDescription());
			assertEquals(1, resolution.discardedRecords().size());
			assertEquals("second", ((EventBean) resolution.discardedRecords().get(0).discardedRecord()).getDescription());
		} finally {
			resolver.close();
		}
	}

	private EventBean event(String id, String eventTime, String description, Integer sequenceNo) {
		EventBean event = new EventBean();
		event.setId(id);
		event.setEventTime(eventTime);
		event.setDescription(description);
		event.setSequenceNo(sequenceNo);
		return event;
	}

	public static class EventBean {
		private String id;
		private String eventTime;
		private String description;
		private Integer sequenceNo;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getEventTime() {
			return eventTime;
		}

		public void setEventTime(String eventTime) {
			this.eventTime = eventTime;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public Integer getSequenceNo() {
			return sequenceNo;
		}

		public void setSequenceNo(Integer sequenceNo) {
			this.sequenceNo = sequenceNo;
		}
	}
}



