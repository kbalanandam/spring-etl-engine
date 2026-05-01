package com.etl.config.source.validation;

import com.etl.config.ColumnConfig;
import com.etl.config.exception.ConfigException;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.enums.ModelFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceValidationServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void failsFastForInvalidCsvArchiveConfigThroughSourceValidationSpi() {
		CsvSourceConfig.ArchiveConfig archive = new CsvSourceConfig.ArchiveConfig();
		archive.setEnabled(true);

		CsvSourceConfig sourceConfig = new CsvSourceConfig();
		sourceConfig.setSourceName("Events");
		sourceConfig.setPackageName("com.etl.model.source");
		sourceConfig.setFilePath("input/events.csv");
		sourceConfig.setDelimiter(",");
		sourceConfig.setFields(List.of(column("id")));
		sourceConfig.setArchive(archive);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("archive"));
		assertTrue(exception.getMessage().contains("successPath"));
	}

	@Test
	void failsFastWhenCsvFileValidationCannotFindFile() {
		CsvSourceConfig.ValidationConfig validation = new CsvSourceConfig.ValidationConfig();
		validation.setAllowEmpty(false);

		CsvSourceConfig sourceConfig = csvSource(tempDir.resolve("missing-events.csv"));
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("must exist"));
	}

	@Test
	void failsFastWhenCsvValidationDisallowsHeaderOnlyFiles() throws IOException {
		Path csvFile = tempDir.resolve("events-header-only.csv");
		Files.writeString(csvFile, "id,eventTime\n");

		CsvSourceConfig.ValidationConfig validation = new CsvSourceConfig.ValidationConfig();
		validation.setAllowEmpty(false);

		CsvSourceConfig sourceConfig = csvSource(csvFile);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("at least one data row"));
	}

	@Test
	void failsFastWhenCsvValidationRequiresMatchingHeader() throws IOException {
		Path csvFile = tempDir.resolve("events-bad-header.csv");
		Files.writeString(csvFile, "event_id,event_time\nEVT-1,08:30:00\n");

		CsvSourceConfig.ValidationConfig validation = new CsvSourceConfig.ValidationConfig();
		validation.setRequireHeaderMatch(true);
		validation.setAllowEmpty(false);

		CsvSourceConfig sourceConfig = csvSource(csvFile);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("header"));
		assertTrue(exception.getMessage().contains("expected=[id, eventTime]"));
	}

	@Test
	void rejectsCsvFileWhenHeaderValidationFailsAndOnFailureIsRejectFile() throws IOException {
		Path csvFile = tempDir.resolve("events-bad-header.csv");
		Files.writeString(csvFile, "event_id,event_time\nEVT-1,08:30:00\n");
		Path rejectDir = tempDir.resolve("rejects");

		CsvSourceConfig.ValidationConfig validation = new CsvSourceConfig.ValidationConfig();
		validation.setRequireHeaderMatch(true);
		validation.setAllowEmpty(false);
		validation.setOnFailure("rejectFile");
		validation.setRejectPath(rejectDir.toString());

		CsvSourceConfig sourceConfig = csvSource(csvFile);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("header"));
		assertTrue(exception.getMessage().contains("moved to reject path"));
		assertFalse(Files.exists(csvFile));
		try (var rejectedFiles = Files.list(rejectDir)) {
			Path rejected = rejectedFiles.findFirst().orElse(null);
			assertNotNull(rejected);
			assertTrue(rejected.getFileName().toString().endsWith("events-bad-header.csv"));
		}
	}

	@Test
	void rejectsXmlFileWhenFileNamePatternFailsAndOnFailureIsRejectFile() throws IOException {
		Path xmlFile = tempDir.resolve("bad-name.xml");
		Files.writeString(xmlFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<Customers>
				  <Customer><id>1</id></Customer>
				</Customers>
				""");
		Path rejectDir = tempDir.resolve("xml-rejects");

		XmlSourceConfig.ValidationConfig validation = new XmlSourceConfig.ValidationConfig();
		validation.setFileNamePattern("^customers-\\d+\\.xml$");
		validation.setOnFailure("rejectFile");
		validation.setRejectPath(rejectDir.toString());

		XmlSourceConfig sourceConfig = xmlSource(xmlFile);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("xml-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("fileNamePattern"));
		assertTrue(exception.getMessage().contains("moved to reject path"));
		assertFalse(Files.exists(xmlFile));
		try (var rejectedFiles = Files.list(rejectDir)) {
			Path rejected = rejectedFiles.findFirst().orElse(null);
			assertNotNull(rejected);
			assertTrue(rejected.getFileName().toString().endsWith("bad-name.xml"));
		}
	}

	@Test
	void failsFastWhenCsvRejectFileValidationOmitsRejectPath() {
		CsvSourceConfig.ValidationConfig validation = new CsvSourceConfig.ValidationConfig();
		validation.setFileNamePattern("^events-.*\\.csv$");
		validation.setOnFailure("rejectFile");

		CsvSourceConfig sourceConfig = csvSource(tempDir.resolve("events.csv"));
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("rejectPath"));
	}

	@Test
	void passesWhenCsvValidationHeaderMatchesAndDataExists() throws IOException {
		Path csvFile = tempDir.resolve("events-valid.csv");
		Files.writeString(csvFile, "id,eventTime\nEVT-1,08:30:00\n");

		CsvSourceConfig.ValidationConfig validation = new CsvSourceConfig.ValidationConfig();
		validation.setRequireHeaderMatch(true);
		validation.setAllowEmpty(false);

		CsvSourceConfig sourceConfig = csvSource(csvFile);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		assertDoesNotThrow(() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml")));
	}

	@Test
	void invokesCustomSourceValidatorExtensions() {
		AtomicBoolean validated = new AtomicBoolean(false);
		SourceValidationService service = new SourceValidationService(List.of(new SourceValidator() {
			@Override
			public boolean supports(SourceConfig sourceConfig) {
				return sourceConfig instanceof TestSourceConfig;
			}

			@Override
			public void validate(SourceConfig sourceConfig, SourceValidationContext context) {
				validated.set(true);
			}
		}));

		TestSourceConfig sourceConfig = new TestSourceConfig();
		sourceConfig.setSourceName("CustomSource");
		service.validate(sourceConfig, new SourceValidationContext("custom-scenario", "tmp/source-config.yaml"));

		assertTrue(validated.get());
	}

	@Test
	void failsFastWhenXmlValidationRootElementDoesNotMatchConfiguredRoot() throws IOException {
		Path xmlFile = tempDir.resolve("customers.xml");
		Files.writeString(xmlFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<Clients>
				  <Customer><id>1</id></Customer>
				</Clients>
				""");

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(xmlSource(xmlFile), new SourceValidationContext("xml-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("rootElement"));
		assertTrue(exception.getMessage().contains("expected=Customers"));
	}

	@Test
	void failsFastWhenXmlValidationCannotFindConfiguredRecordElement() throws IOException {
		Path xmlFile = tempDir.resolve("customers.xml");
		Files.writeString(xmlFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<Customers>
				  <Client><id>1</id></Client>
				</Customers>
				""");

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(xmlSource(xmlFile), new SourceValidationContext("xml-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("record element"));
		assertTrue(exception.getMessage().contains("Customer"));
	}

	@Test
	void passesWhenXmlValidationFindsConfiguredRootAndRecordElement() throws IOException {
		Path xmlFile = tempDir.resolve("customers.xml");
		Files.writeString(xmlFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<Customers>
				  <Customer><id>1</id></Customer>
				</Customers>
				""");

		SourceValidationService service = new SourceValidationService();
		assertDoesNotThrow(() -> service.validate(
				xmlSource(xmlFile),
				new SourceValidationContext("xml-validation", "tmp/source-config.yaml")
		));
	}

	private CsvSourceConfig csvSource(Path filePath) {
		CsvSourceConfig sourceConfig = new CsvSourceConfig();
		sourceConfig.setSourceName("Events");
		sourceConfig.setPackageName("com.etl.model.source");
		sourceConfig.setFilePath(filePath.toString());
		sourceConfig.setDelimiter(",");
		sourceConfig.setFields(List.of(
				column("id"),
				column("eventTime")
		));
		return sourceConfig;
	}

	private ColumnConfig column(String name) {
		ColumnConfig column = new ColumnConfig();
		column.setName(name);
		column.setType("String");
		return column;
	}

	private XmlSourceConfig xmlSource(Path filePath) {
		XmlSourceConfig sourceConfig = new XmlSourceConfig();
		sourceConfig.setSourceName("Customers");
		sourceConfig.setPackageName("com.etl.model.source");
		sourceConfig.setFilePath(filePath.toString());
		sourceConfig.setRootElement("Customers");
		sourceConfig.setRecordElement("Customer");
		sourceConfig.setFields(List.of(column("id")));
		return sourceConfig;
	}

	private static final class TestSourceConfig extends SourceConfig {

		@Override
		public ModelFormat getFormat() {
			return ModelFormat.CSV;
		}

		@Override
		public int getRecordCount() {
			return 0;
		}
	}
}
