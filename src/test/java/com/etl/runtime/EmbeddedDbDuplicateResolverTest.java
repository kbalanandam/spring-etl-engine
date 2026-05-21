package com.etl.runtime;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.etl.processor.validation.DuplicateProcessorValidationRule;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDbDuplicateResolverTest {

	@Test
	void emitsEmbeddedDbLifecycleEvidenceIncludingSummaryAndCleanup() {
		ch.qos.logback.classic.Logger resolverLogger =
				(ch.qos.logback.classic.Logger) LoggerFactory.getLogger(EmbeddedDbDuplicateResolver.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		resolverLogger.addAppender(appender);
		try (EmbeddedDbDuplicateResolver resolver = new EmbeddedDbDuplicateResolver(
				new DuplicateRule(
						"id",
						List.of("id"),
						List.of(new DuplicateProcessorValidationRule.OrderSelector("eventTime", true)),
						DuplicateRule.StorageMode.AUTO
				)
		)) {
			resolver.accept(event("EVT-1001", "08:30:00", "first", 1));
			resolver.accept(event("EVT-1001", "09:30:00", "winner", 2));
			resolver.complete();
		}

		assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("DUPLICATE_RESOLVER event=resolver_open")
				&& event.getFormattedMessage().contains("resolverMode=embeddedDb")
				&& event.getFormattedMessage().contains("storageEngine=h2")));
		assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("DUPLICATE_RESOLVER event=resolver_summary")
				&& event.getFormattedMessage().contains("resolverMode=embeddedDb")
				&& event.getFormattedMessage().contains("stagedRankedCount=2")
				&& event.getFormattedMessage().contains("databaseBasePath=")));
		assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("DUPLICATE_RESOLVER event=resolver_close")
				&& event.getFormattedMessage().contains("dataFileExistsAfterClose=false")
				&& event.getFormattedMessage().contains("directoryExistsAfterClose=false")));
		resolverLogger.detachAppender(appender);
	}

	@Test
	void keepsBestRecordPerKeyUsingStructuredOrder() {
		try (EmbeddedDbDuplicateResolver resolver = new EmbeddedDbDuplicateResolver(
				new DuplicateRule(
						"id",
						List.of("id"),
						List.of(
								new DuplicateProcessorValidationRule.OrderSelector("eventTime", true),
								new DuplicateProcessorValidationRule.OrderSelector("sequenceNo", false)
						),
						DuplicateRule.StorageMode.AUTO
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
						List.of(new DuplicateProcessorValidationRule.OrderSelector("eventTime", true)),
						DuplicateRule.StorageMode.AUTO
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
						List.of(new DuplicateProcessorValidationRule.OrderSelector("TVLAccountDetails.AccountNumber", true)),
						DuplicateRule.StorageMode.AUTO
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

	@Test
	void matchesInMemoryDiscardOrderingAcrossDuplicateKeys() {
		DuplicateRule rule = new DuplicateRule(
				"id",
				List.of("id"),
				List.of(new DuplicateProcessorValidationRule.OrderSelector("eventTime", true)),
				DuplicateRule.StorageMode.AUTO
		);
		List<EventBean> events = List.of(
				event("Z-KEY", "08:00:00", "z-loser", 1),
				event("A-KEY", "08:05:00", "a-loser", 1),
				event("Z-KEY", "09:00:00", "z-winner", 2),
				event("A-KEY", "09:05:00", "a-winner", 2)
		);

		DuplicateResolution embeddedResolution;
		try (EmbeddedDbDuplicateResolver embeddedResolver = new EmbeddedDbDuplicateResolver(rule)) {
			events.forEach(embeddedResolver::accept);
			embeddedResolution = embeddedResolver.complete();
		}

		DuplicateResolution inMemoryResolution;
		try (InMemoryDuplicateResolver inMemoryResolver = new InMemoryDuplicateResolver(rule)) {
			events.forEach(inMemoryResolver::accept);
			inMemoryResolution = inMemoryResolver.complete();
		}

		assertEquals(
				inMemoryResolution.discardedRecords().stream()
						.map(discard -> ((EventBean) discard.discardedRecord()).getDescription())
						.toList(),
				embeddedResolution.discardedRecords().stream()
						.map(discard -> ((EventBean) discard.discardedRecord()).getDescription())
						.toList()
		);
		assertEquals(
				inMemoryResolution.retainedRecords().stream()
						.map(record -> ((EventBean) record).getDescription())
						.toList(),
				embeddedResolution.retainedRecords().stream()
						.map(record -> ((EventBean) record).getDescription())
						.toList()
		);
	}

	@Test
	void deletesTemporaryDatabaseFilesOnClose() throws Exception {
		EmbeddedDbDuplicateResolver resolver = new EmbeddedDbDuplicateResolver(
				new DuplicateRule(
						"id",
						List.of("id"),
						List.of(new DuplicateProcessorValidationRule.OrderSelector("eventTime", true)),
						DuplicateRule.StorageMode.AUTO
				)
		);
		Path databaseBasePath = resolverPathField(resolver, "databaseBasePath");
		Path databaseDirectory = resolverPathField(resolver, "databaseDirectory");
		try {
			resolver.accept(event("EVT-1001", "08:30:00", "first", 1));
			resolver.complete();
			assertTrue(Files.exists(Path.of(databaseBasePath + ".mv.db")));
		} finally {
			resolver.close();
		}

		assertFalse(Files.exists(Path.of(databaseBasePath + ".mv.db")));
		assertFalse(Files.exists(Path.of(databaseBasePath + ".trace.db")));
		assertFalse(Files.exists(Path.of(databaseBasePath + ".lock.db")));
		assertFalse(Files.exists(databaseDirectory));
	}

	private Path resolverPathField(EmbeddedDbDuplicateResolver resolver, String fieldName) throws Exception {
		Field field = EmbeddedDbDuplicateResolver.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		return (Path) field.get(resolver);
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



