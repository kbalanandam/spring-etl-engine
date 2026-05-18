package com.etl.config.source.validation;

import com.etl.config.ColumnConfig;
import com.etl.config.exception.ConfigException;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.FileArchiveConfig;
import com.etl.config.source.FileUnzipConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.enums.ModelFormat;
import com.etl.exception.ZipExtractionException;
import com.etl.runtime.FileSourceArtifactSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
	void failsFastForInvalidXmlArchiveConfigThroughSourceValidationSpi() throws IOException {
		FileArchiveConfig archive = new FileArchiveConfig();
		archive.setEnabled(true);

		Path xmlFile = tempDir.resolve("events.xml");
		Files.writeString(xmlFile, "<Events><Event><id>1</id></Event></Events>");

		XmlSourceConfig sourceConfig = xmlSource(xmlFile);
		sourceConfig.setArchive(archive);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("xml-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("archive"));
		assertTrue(exception.getMessage().contains("successPath"));
	}

	@Test
	void failsFastWhenCsvArchiveZipPackagingTargetsAlreadyZippedSource() {
		CsvSourceConfig.ArchiveConfig archive = new CsvSourceConfig.ArchiveConfig();
		archive.setEnabled(true);
		archive.setSuccessPath(tempDir.resolve("archive").toString());
		archive.setPackageAsZip(true);

		CsvSourceConfig sourceConfig = csvSource(tempDir.resolve("events.zip"));
		sourceConfig.setArchive(archive);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("archive.packageAsZip"));
		assertTrue(exception.getMessage().contains(".zip"));
	}

	@Test
	void failsFastWhenXmlArchiveZipPackagingTargetsAlreadyZippedSource() throws IOException {
		FileArchiveConfig archive = new FileArchiveConfig();
		archive.setEnabled(true);
		archive.setSuccessPath(tempDir.resolve("archive").toString());
		archive.setPackageAsZip(true);

		Path xmlZip = tempDir.resolve("events.zip");
		Files.writeString(xmlZip, "zip-placeholder");
		XmlSourceConfig sourceConfig = xmlSource(xmlZip);
		sourceConfig.setArchive(archive);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("xml-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("archive.packageAsZip"));
		assertTrue(exception.getMessage().contains(".zip"));
	}

	@Test
	void extractsSingleCsvZipEntryBeforeValidation() throws IOException {
		Path zipFile = tempDir.resolve("events.zip");
		writeZip(zipFile, List.of(new ZipTestEntry("events.csv", "id,eventTime\nEVT-1,08:30:00\n")));

		CsvSourceConfig sourceConfig = csvSource(zipFile);
		CsvSourceConfig.ValidationConfig validation = new CsvSourceConfig.ValidationConfig();
		validation.setAllowEmpty(false);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		assertDoesNotThrow(() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml")));

		assertNotNull(sourceConfig.getPreparedFilePath());
		Path preparedPath = Path.of(sourceConfig.getPreparedFilePath());
		assertTrue(preparedPath.endsWith("events.csv"));
		assertTrue(Files.exists(preparedPath));
		assertTrue(preparedPath.startsWith(defaultPreparedRoot()));
		assertFalse(preparedPath.startsWith(tempDir));
		assertTrue(Files.exists(zipFile));
	}

	@Test
	void failsFastWhenCsvExplicitlyEnablesUnzipForNonZipFilePath() throws IOException {
		Path csvFile = tempDir.resolve("events.csv");
		Files.writeString(csvFile, "id,eventTime\nEVT-1,08:30:00\n");

		CsvSourceConfig sourceConfig = csvSource(csvFile);
		FileUnzipConfig unzip = new FileUnzipConfig();
		unzip.setEnabled(true);
		sourceConfig.setUnzip(unzip);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("unzip.enabled=true"));
		assertTrue(exception.getMessage().contains(".zip"));
		assertNull(sourceConfig.getPreparedFilePath());
		assertTrue(Files.exists(csvFile));
	}

	@Test
	void failsFastWhenCsvZipContainsMultipleEntriesWithoutConfiguredEntryName() throws IOException {
		Path zipFile = tempDir.resolve("events.zip");
		writeZip(zipFile, List.of(
				new ZipTestEntry("events-1.csv", "id,eventTime\nEVT-1,08:30:00\n"),
				new ZipTestEntry("events-2.csv", "id,eventTime\nEVT-2,09:30:00\n")
		));

		CsvSourceConfig sourceConfig = csvSource(zipFile);
		CsvSourceConfig.ValidationConfig validation = new CsvSourceConfig.ValidationConfig();
		validation.setAllowEmpty(false);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("exactly one file entry"));
		assertTrue(exception.getCause() instanceof ZipExtractionException);
		assertNull(sourceConfig.getPreparedFilePath());
		try (var preparedFiles = Files.find(tempDir, 5,
				(path, attributes) -> attributes.isRegularFile() && path.getFileName().toString().endsWith(".csv"))) {
			assertEquals(List.of(), preparedFiles.toList());
		}
	}

	@Test
	void extractsConfiguredXmlZipEntryBeforeStructureValidation() throws IOException {
		Path zipFile = tempDir.resolve("customers.zip");
		writeZip(zipFile, List.of(new ZipTestEntry("nested/customers.xml", """
				<?xml version="1.0" encoding="UTF-8"?>
				<Customers>
				  <Customer><id>ABC</id></Customer>
				</Customers>
				""")));

		XmlSourceConfig sourceConfig = xmlSource(zipFile);
		FileUnzipConfig unzip = new FileUnzipConfig();
		unzip.setEntryName("nested/customers.xml");
		sourceConfig.setUnzip(unzip);

		SourceValidationService service = new SourceValidationService();
		assertDoesNotThrow(() -> service.validate(sourceConfig, new SourceValidationContext("xml-validation", "tmp/source-config.yaml")));

		assertNotNull(sourceConfig.getPreparedFilePath());
		Path preparedPath = Path.of(sourceConfig.getPreparedFilePath());
		assertTrue(preparedPath.endsWith("customers.xml"));
		assertTrue(Files.exists(preparedPath));
		assertTrue(preparedPath.startsWith(defaultPreparedRoot()));
		assertFalse(preparedPath.startsWith(tempDir));
		assertTrue(Files.exists(zipFile));
	}

	@Test
	void failsFastWhenXmlExplicitlyEnablesUnzipForNonZipFilePath() throws IOException {
		Path xmlFile = tempDir.resolve("customers.xml");
		Files.writeString(xmlFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<Customers>
				  <Customer><id>ABC</id></Customer>
				</Customers>
				""");

		XmlSourceConfig sourceConfig = xmlSource(xmlFile);
		FileUnzipConfig unzip = new FileUnzipConfig();
		unzip.setEnabled(true);
		sourceConfig.setUnzip(unzip);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("xml-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("unzip.enabled=true"));
		assertTrue(exception.getMessage().contains(".zip"));
		assertNull(sourceConfig.getPreparedFilePath());
		assertTrue(Files.exists(xmlFile));
	}

	@Test
	void rejectsOriginalZipArtifactAndCleansPreparedCsvFileWhenValidationFails() throws IOException {
		Path zipFile = tempDir.resolve("events.zip");
		writeZip(zipFile, List.of(new ZipTestEntry("events.csv", "event_id,event_time\nEVT-1,08:30:00\n")));
		Path rejectDir = tempDir.resolve("rejects");

		CsvSourceConfig sourceConfig = csvSource(zipFile);

		CsvSourceConfig.ValidationConfig validation = new CsvSourceConfig.ValidationConfig();
		validation.setRequireHeaderMatch(true);
		validation.setAllowEmpty(false);
		validation.setOnFailure("rejectFile");
		validation.setRejectPath(rejectDir.toString());
		sourceConfig.setValidation(validation);
		Path preparedPath = new FileSourceArtifactSupport().resolveReadablePath(sourceConfig);
		Path preparedDir = preparedPath.getParent();
		assertTrue(Files.exists(preparedPath));
		assertTrue(preparedPath.startsWith(defaultPreparedRoot()));

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("header"));
		assertTrue(exception.getMessage().contains("moved to reject path"));
		assertFalse(Files.exists(zipFile));
		assertFalse(Files.exists(preparedPath));
		assertFalse(Files.exists(preparedDir));
		assertNull(sourceConfig.getPreparedFilePath());
		try (var rejectedFiles = Files.list(rejectDir)) {
			Path rejected = rejectedFiles.findFirst().orElse(null);
			assertNotNull(rejected);
			assertEquals("events.zip", rejected.getFileName().toString());
		}
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
				  <Customer><id>ABC</id></Customer>
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
	void rejectsXmlFileWhenRootElementValidationFailsAndOnFailureIsRejectFile() throws IOException {
		Path xmlFile = tempDir.resolve("customers.xml");
		Files.writeString(xmlFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<Clients>
				  <Customer><id>1</id></Customer>
				</Clients>
				""");
		Path rejectDir = tempDir.resolve("xml-root-rejects");

		XmlSourceConfig sourceConfig = xmlSource(xmlFile);
		XmlSourceConfig.ValidationConfig validation = rejectFileValidation(rejectDir);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("xml-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("rootElement"));
		assertRejected(xmlFile, rejectDir, "customers.xml");
	}

	@Test
	void rejectsXmlFileWhenRecordElementValidationFailsAndOnFailureIsRejectFile() throws IOException {
		Path xmlFile = tempDir.resolve("customers.xml");
		Files.writeString(xmlFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<Customers>
				  <Client><id>1</id></Client>
				</Customers>
				""");
		Path rejectDir = tempDir.resolve("xml-record-rejects");

		XmlSourceConfig sourceConfig = xmlSource(xmlFile);
		XmlSourceConfig.ValidationConfig validation = rejectFileValidation(rejectDir);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("xml-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("record element"));
		assertRejected(xmlFile, rejectDir, "customers.xml");
	}

	@Test
	void rejectsXmlFileWhenXmlIsMalformedAndOnFailureIsRejectFile() throws IOException {
		Path xmlFile = tempDir.resolve("customers.xml");
		Files.writeString(xmlFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<Customers>
				  <Customer><id>1</id></Customer>
				""");
		Path rejectDir = tempDir.resolve("xml-malformed-rejects");

		XmlSourceConfig sourceConfig = xmlSource(xmlFile);
		XmlSourceConfig.ValidationConfig validation = rejectFileValidation(rejectDir);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("xml-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("well-formed"));
		assertRejected(xmlFile, rejectDir, "customers.xml");
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
	void passesWhenCsvValidationHeaderMatchesUsingConfiguredQuoteCharacter() throws IOException {
		Path csvFile = tempDir.resolve("events-valid-single-quoted-header.csv");
		Files.writeString(csvFile, "'id','eventTime'\n'EVT-1','08:30:00'\n");

		CsvSourceConfig.ValidationConfig validation = new CsvSourceConfig.ValidationConfig();
		validation.setRequireHeaderMatch(true);
		validation.setAllowEmpty(false);

		CsvSourceConfig sourceConfig = csvSource(csvFile);
		CsvSourceConfig.ParserConfig parser = new CsvSourceConfig.ParserConfig();
		parser.setQuoteCharacter("'");
		sourceConfig.setParser(parser);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		assertDoesNotThrow(() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml")));
	}

	@Test
	void failsFastWhenCsvParserQuoteCharacterIsInvalid() {
		CsvSourceConfig sourceConfig = csvSource(tempDir.resolve("events.csv"));
		CsvSourceConfig.ParserConfig parser = new CsvSourceConfig.ParserConfig();
		parser.setQuoteCharacter("''");
		sourceConfig.setParser(parser);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("parser.quoteCharacter"));
		assertTrue(exception.getMessage().contains("exactly one character"));
	}

	@Test
	void passesWhenHeaderlessCsvDisablesHeaderSkipping() throws IOException {
		Path csvFile = tempDir.resolve("events-no-header.csv");
		Files.writeString(csvFile, "EVT-1,08:30:00\nEVT-2,09:15:00\n");

		CsvSourceConfig.ValidationConfig validation = new CsvSourceConfig.ValidationConfig();
		validation.setAllowEmpty(false);

		CsvSourceConfig sourceConfig = csvSource(csvFile);
		sourceConfig.setSkipHeader(false);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		assertDoesNotThrow(() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml")));
	}

	@Test
	void failsFastWhenHeaderMatchValidationIsRequestedForHeaderlessCsv() throws IOException {
		Path csvFile = tempDir.resolve("events-no-header.csv");
		Files.writeString(csvFile, "EVT-1,08:30:00\n");

		CsvSourceConfig.ValidationConfig validation = new CsvSourceConfig.ValidationConfig();
		validation.setAllowEmpty(false);
		validation.setRequireHeaderMatch(true);

		CsvSourceConfig sourceConfig = csvSource(csvFile);
		sourceConfig.setSkipHeader(false);
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("csv-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("skipHeader=true"));
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
				  <Customer><id>ABC</id></Customer>
				</Customers>
				""");

		SourceValidationService service = new SourceValidationService();
		assertDoesNotThrow(() -> service.validate(
				xmlSource(xmlFile),
				new SourceValidationContext("xml-validation", "tmp/source-config.yaml")
		));
	}

	@Test
	void passesWhenXmlValidationSchemaPathMatchesXmlContract() throws IOException {
		Path xmlFile = tempDir.resolve("customers.xml");
		Files.writeString(xmlFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<Customers>
				  <Customer><id>ABC</id></Customer>
				</Customers>
				""");
		Path schemaFile = tempDir.resolve("customers.xsd");
		Files.writeString(schemaFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
				  <xs:element name="Customers">
				    <xs:complexType>
				      <xs:sequence>
				        <xs:element name="Customer" maxOccurs="unbounded">
				          <xs:complexType>
				            <xs:sequence>
				              <xs:element name="id" type="xs:string"/>
				            </xs:sequence>
				          </xs:complexType>
				        </xs:element>
				      </xs:sequence>
				    </xs:complexType>
				  </xs:element>
				</xs:schema>
				""");

		XmlSourceConfig sourceConfig = xmlSource(xmlFile);
		XmlSourceConfig.ValidationConfig validation = new XmlSourceConfig.ValidationConfig();
		validation.setSchemaPath(schemaFile.toString());
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		assertDoesNotThrow(() -> service.validate(sourceConfig, new SourceValidationContext("xml-validation", "tmp/source-config.yaml")));
	}

	@Test
	void failsFastWhenXmlValidationSchemaPathDoesNotMatchXmlContract() throws IOException {
		Path xmlFile = tempDir.resolve("customers.xml");
		Files.writeString(xmlFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<Customers>
				  <Customer><id>ABC</id></Customer>
				</Customers>
				""");
		Path schemaFile = tempDir.resolve("customers.xsd");
		Files.writeString(schemaFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
				  <xs:element name="Customers">
				    <xs:complexType>
				      <xs:sequence>
				        <xs:element name="Customer" maxOccurs="unbounded">
				          <xs:complexType>
				            <xs:sequence>
				              <xs:element name="id" type="xs:int"/>
				            </xs:sequence>
				          </xs:complexType>
				        </xs:element>
				      </xs:sequence>
				    </xs:complexType>
				  </xs:element>
				</xs:schema>
				""");

		XmlSourceConfig sourceConfig = xmlSource(xmlFile);
		XmlSourceConfig.ValidationConfig validation = new XmlSourceConfig.ValidationConfig();
		validation.setSchemaPath(schemaFile.toString());
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("xml-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("schema validation failed"));
		assertTrue(exception.getMessage().contains("schemaPath"));
	}

	@Test
	void rejectsXmlFileWhenSchemaValidationFailsAndOnFailureIsRejectFile() throws IOException {
		Path xmlFile = tempDir.resolve("customers.xml");
		Files.writeString(xmlFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<Customers>
				  <Customer><id>ABC</id></Customer>
				</Customers>
				""");
		Path schemaFile = tempDir.resolve("customers.xsd");
		Files.writeString(schemaFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
				  <xs:element name="Customers">
				    <xs:complexType>
				      <xs:sequence>
				        <xs:element name="Customer" maxOccurs="unbounded">
				          <xs:complexType>
				            <xs:sequence>
				              <xs:element name="id" type="xs:int"/>
				            </xs:sequence>
				          </xs:complexType>
				        </xs:element>
				      </xs:sequence>
				    </xs:complexType>
				  </xs:element>
				</xs:schema>
				""");
		Path rejectDir = tempDir.resolve("xml-schema-rejects");

		XmlSourceConfig sourceConfig = xmlSource(xmlFile);
		XmlSourceConfig.ValidationConfig validation = new XmlSourceConfig.ValidationConfig();
		validation.setSchemaPath(schemaFile.toString());
		validation.setOnFailure("rejectFile");
		validation.setRejectPath(rejectDir.toString());
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("xml-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("schema validation failed"));
		assertTrue(exception.getMessage().contains("moved to reject path"));
		assertFalse(Files.exists(xmlFile));
		try (var rejectedFiles = Files.list(rejectDir)) {
			Path rejected = rejectedFiles.findFirst().orElse(null);
			assertNotNull(rejected);
			assertEquals("customers.xml", rejected.getFileName().toString());
		}
	}

	@Test
	void failsFastWhenXmlValidationSchemaPathDoesNotExist() throws IOException {
		Path xmlFile = tempDir.resolve("customers.xml");
		Files.writeString(xmlFile, """
				<?xml version="1.0" encoding="UTF-8"?>
				<Customers>
				  <Customer><id>1</id></Customer>
				</Customers>
				""");

		XmlSourceConfig sourceConfig = xmlSource(xmlFile);
		XmlSourceConfig.ValidationConfig validation = new XmlSourceConfig.ValidationConfig();
		validation.setSchemaPath(tempDir.resolve("missing-customers.xsd").toString());
		sourceConfig.setValidation(validation);

		SourceValidationService service = new SourceValidationService();
		ConfigException exception = assertThrows(
				ConfigException.class,
				() -> service.validate(sourceConfig, new SourceValidationContext("xml-validation", "tmp/source-config.yaml"))
		);

		assertTrue(exception.getMessage().contains("schemaPath must exist"));
		assertTrue(Files.exists(xmlFile));
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

	private XmlSourceConfig.ValidationConfig rejectFileValidation(Path rejectDir) {
		XmlSourceConfig.ValidationConfig validation = new XmlSourceConfig.ValidationConfig();
		validation.setOnFailure("rejectFile");
		validation.setRejectPath(rejectDir.toString());
		return validation;
	}

	private void assertRejected(Path originalFile, Path rejectDir, String expectedFileName) throws IOException {
		assertTrue(Files.notExists(originalFile));
		try (var rejectedFiles = Files.list(rejectDir)) {
			Path rejected = rejectedFiles.findFirst().orElse(null);
			assertNotNull(rejected);
			assertEquals(expectedFileName, rejected.getFileName().toString());
		}
	}

	private void writeZip(Path zipFile, List<ZipTestEntry> entries) throws IOException {
		try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile))) {
			for (ZipTestEntry entry : entries) {
				zipOutputStream.putNextEntry(new ZipEntry(entry.entryName()));
				zipOutputStream.write(entry.contents().getBytes());
				zipOutputStream.closeEntry();
			}
		}
	}

	private Path defaultPreparedRoot() {
		return Path.of(System.getProperty("java.io.tmpdir"))
				.toAbsolutePath()
				.normalize()
				.resolve("spring-etl-engine")
				.resolve("prepared-sources");
	}

	private record ZipTestEntry(String entryName, String contents) {
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
