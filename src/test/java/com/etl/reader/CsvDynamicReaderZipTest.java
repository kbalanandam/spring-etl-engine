package com.etl.reader;

import com.etl.config.ColumnConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.FileUnzipConfig;
import com.etl.reader.impl.CsvDynamicReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvDynamicReaderZipTest {

	@TempDir
	Path tempDir;

	@Test
	void readsCsvRowsFromZipArtifactWithoutSeparatePreExtractionStep() throws Exception {
		Path zipFile = tempDir.resolve("events.zip");
		try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
			zipOutputStream.putNextEntry(new ZipEntry("events.csv"));
			zipOutputStream.write("id,eventTime\nEVT-1,08:30:00\nEVT-2,09:45:00\n".getBytes(StandardCharsets.UTF_8));
			zipOutputStream.closeEntry();
		}

		CsvSourceConfig sourceConfig = new CsvSourceConfig();
		sourceConfig.setSourceName("Events");
		sourceConfig.setPackageName("com.etl.model.source");
		sourceConfig.setFilePath(zipFile.toString());
		sourceConfig.setDelimiter(",");
		sourceConfig.setFields(List.of(column("id"), column("eventTime")));

		ItemReader<EventRow> reader = new CsvDynamicReader<EventRow>().getReader(sourceConfig, EventRow.class);
		if (reader instanceof ItemStream itemStream) {
			itemStream.open(new ExecutionContext());
		}
		try {
			EventRow first = reader.read();
			EventRow second = reader.read();

			assertNotNull(first);
			assertEquals("EVT-1", first.getId());
			assertEquals("08:30:00", first.getEventTime());
			assertNotNull(second);
			assertEquals("EVT-2", second.getId());
			assertEquals("09:45:00", second.getEventTime());
			assertEquals(null, reader.read());
			assertNotNull(sourceConfig.getPreparedFilePath());
			Path preparedPath = Path.of(sourceConfig.getPreparedFilePath());
			assertTrue(Files.exists(preparedPath));
			assertTrue(preparedPath.startsWith(defaultPreparedRoot()));
			assertFalse(preparedPath.startsWith(zipFile.getParent()), "Default ZIP preparation should not stage files beside the source artifact.");
		} finally {
			if (reader instanceof ItemStream itemStream) {
				itemStream.close();
			}
		}
	}

	@Test
	void failsFastFromReaderPathWhenExplicitUnzipTargetsNonZipArtifact() throws Exception {
		Path csvFile = tempDir.resolve("events.csv");
		Files.writeString(csvFile, "id,eventTime\nEVT-1,08:30:00\n", StandardCharsets.UTF_8);

		CsvSourceConfig sourceConfig = new CsvSourceConfig();
		sourceConfig.setSourceName("Events");
		sourceConfig.setPackageName("com.etl.model.source");
		sourceConfig.setFilePath(csvFile.toString());
		sourceConfig.setDelimiter(",");
		sourceConfig.setFields(List.of(column("id"), column("eventTime")));
		FileUnzipConfig unzip = new FileUnzipConfig();
		unzip.setEnabled(true);
		sourceConfig.setUnzip(unzip);

		IllegalArgumentException exception = assertThrows(
				IllegalArgumentException.class,
				() -> new CsvDynamicReader<EventRow>().getReader(sourceConfig, EventRow.class)
		);

		assertTrue(exception.getMessage().contains("unzip.enabled=true"));
		assertTrue(exception.getMessage().contains(".zip"));
		assertNull(sourceConfig.getPreparedFilePath());
		assertTrue(Files.exists(csvFile));
	}

	private ColumnConfig column(String name) {
		ColumnConfig column = new ColumnConfig();
		column.setName(name);
		column.setType("String");
		return column;
	}

	private Path defaultPreparedRoot() {
		return Path.of(System.getProperty("java.io.tmpdir"))
				.toAbsolutePath()
				.normalize()
				.resolve("spring-etl-engine")
				.resolve("prepared-sources");
	}

	public static class EventRow {
		private String id;
		private String eventTime;

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
	}
}





