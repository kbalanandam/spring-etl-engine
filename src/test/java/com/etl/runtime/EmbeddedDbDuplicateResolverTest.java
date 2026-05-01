package com.etl.runtime;

import com.etl.processor.validation.DuplicateProcessorValidationRule;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbDuplicateResolverTest {

	@Test
	void keepsBestRecordPerKeyUsingStructuredOrder() {
		try (EmbeddedDbDuplicateResolver resolver = new EmbeddedDbDuplicateResolver(
				new DuplicateRule(
						"id",
						List.of("id"),
						List.of(
								new DuplicateProcessorValidationRule.OrderSelector("eventTime", true),
								new DuplicateProcessorValidationRule.OrderSelector("sequenceNo", false)
						)
				)
		)) {
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
		}
	}

	@Test
	void preservesKeepFirstTieBehaviorThroughEmbeddedDbStaging() {
		try (EmbeddedDbDuplicateResolver resolver = new EmbeddedDbDuplicateResolver(
				new DuplicateRule(
						"id",
						List.of("id"),
						List.of(new DuplicateProcessorValidationRule.OrderSelector("eventTime", true))
				)
		)) {
			resolver.accept(event("EVT-1001", "08:30:00", "first", 1));
			resolver.accept(event("EVT-1001", "08:30:00", "second", 1));

			DuplicateResolution resolution = resolver.complete();

			assertEquals(1, resolution.retainedRecords().size());
			assertEquals("first", ((EventBean) resolution.retainedRecords().get(0)).getDescription());
			assertEquals(1, resolution.discardedRecords().size());
			assertEquals("second", ((EventBean) resolution.discardedRecords().get(0).discardedRecord()).getDescription());
		}
	}

	@Test
	void preservesFlattenedMapPayloadFieldsThroughEmbeddedDbStaging() {
		try (EmbeddedDbDuplicateResolver resolver = new EmbeddedDbDuplicateResolver(
				new DuplicateRule(
						"TagSerialNumber",
						List.of("TagSerialNumber"),
						List.of(new DuplicateProcessorValidationRule.OrderSelector("TVLAccountDetails.AccountNumber", true))
				)
		)) {
			resolver.accept(new LinkedHashMap<>(Map.of(
					"HomeAgencyID", "0056",
					"TagAgencyID", "1300",
					"TagSerialNumber", "0003518358",
					"TVLPlateDetails.PlateCountry", "US",
					"TVLPlateDetails.PlateState", "KS",
					"TVLPlateDetails.PlateNumber", "7064AFP",
					"TVLAccountDetails.AccountNumber", "4773316"
			)));
			resolver.accept(new LinkedHashMap<>(Map.of(
					"HomeAgencyID", "0056",
					"TagAgencyID", "1300",
					"TagSerialNumber", "0003518358",
					"TVLPlateDetails.PlateCountry", "US",
					"TVLPlateDetails.PlateState", "KS",
					"TVLPlateDetails.PlateNumber", "7064AFP",
					"TVLAccountDetails.AccountNumber", "4000000"
			)));

			DuplicateResolution resolution = resolver.complete();

			assertEquals(1, resolution.retainedRecords().size());
			assertInstanceOf(Map.class, resolution.retainedRecords().get(0));
			Map<?, ?> retained = (Map<?, ?>) resolution.retainedRecords().get(0);
			assertEquals("0056", retained.get("HomeAgencyID"));
			assertEquals("1300", retained.get("TagAgencyID"));
			assertEquals("0003518358", retained.get("TagSerialNumber"));
			assertEquals("4773316", retained.get("TVLAccountDetails.AccountNumber"));
			assertEquals("US", retained.get("TVLPlateDetails.PlateCountry"));
			assertNotNull(retained.get("TVLPlateDetails.PlateNumber"));

			assertEquals(1, resolution.discardedRecords().size());
			assertInstanceOf(Map.class, resolution.discardedRecords().get(0).discardedRecord());
			Map<?, ?> discarded = (Map<?, ?>) resolution.discardedRecords().get(0).discardedRecord();
			assertEquals("4000000", discarded.get("TVLAccountDetails.AccountNumber"));
			assertEquals("1300", discarded.get("TagAgencyID"));
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



