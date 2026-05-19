package com.etl.common.util;

import com.etl.exception.ZipExtractionException;
import com.etl.exception.ZipPackagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipFileUtilityTest {

	@TempDir
	Path tempDir;

	@Test
	void extractsConfiguredEntryFromZipForAnyStagedArtifactPath() throws Exception {
		Path zipPath = tempDir.resolve("staged-inbound.zip");
		writeZip(
				zipPath,
				new ZipTestEntry("ignored/readme.txt", "ignore"),
				new ZipTestEntry("payload/customer-feed.csv", "id,name\n1,Ada\n")
		);

		Path extracted = ZipFileUtility.extractSingleFile(zipPath, tempDir.resolve("prepared"), "payload/customer-feed.csv");

		assertEquals("customer-feed.csv", extracted.getFileName().toString());
		assertTrue(Files.exists(extracted));
		assertEquals("id,name\n1,Ada\n", Files.readString(extracted));
	}

	@Test
	void packagesSingleFileIntoZipWithBasenameEntry() throws Exception {
		Path sourceFile = tempDir.resolve("partner-drop").resolve("orders.csv");
		Files.createDirectories(sourceFile.getParent());
		Files.writeString(sourceFile, "id\nA-1\n", StandardCharsets.UTF_8);
		Path zipPath = tempDir.resolve("archive").resolve("orders-archive.zip");

		ZipFileUtility.packageSingleFile(sourceFile, zipPath, "nested/orders.csv");

		assertTrue(Files.exists(zipPath));
		assertEquals(List.of("orders.csv"), zipEntryNames(zipPath));
		assertEquals("id\nA-1\n", readOrdersZipEntry(zipPath));
	}

	@Test
	void failsFastWhenZipHasMultipleFileEntriesWithoutConfiguredEntryName() throws Exception {
		Path zipPath = tempDir.resolve("multi.zip");
		writeZip(
				zipPath,
				new ZipTestEntry("one.csv", "id\n1\n"),
				new ZipTestEntry("two.csv", "id\n2\n")
		);

		ZipExtractionException exception = assertThrows(
				ZipExtractionException.class,
				() -> ZipFileUtility.extractSingleFile(zipPath, tempDir.resolve("prepared"), null)
		);

		assertTrue(exception.getMessage().contains("exactly one file entry"));
		assertTrue(exception.getMessage().contains("one.csv"));
		assertTrue(exception.getMessage().contains("two.csv"));
	}

	@Test
	void failsFastWhenConfiguredEntryMatchesMultipleZipEntries() throws Exception {
		Path zipPath = tempDir.resolve("duplicate-configured-entry.zip");
		writeZip(
				zipPath,
				new ZipTestEntry("payload/events.csv", "id,eventTime\nEVT-1,08:30:00\n"),
				new ZipTestEntry("payload\\events.csv", "id,eventTime\nEVT-2,09:45:00\n")
		);
		Path preparedDir = tempDir.resolve("prepared");

		ZipExtractionException exception = assertThrows(
				ZipExtractionException.class,
				() -> ZipFileUtility.extractSingleFile(zipPath, preparedDir, "payload/events.csv")
		);

		assertTrue(exception.getMessage().contains("multiple matching file entries"));
		assertTrue(exception.getMessage().contains("unzip.entryName"));
		assertFalse(Files.exists(preparedDir.resolve("events.csv")));
		try (var preparedFiles = Files.exists(preparedDir) ? Files.list(preparedDir) : java.util.stream.Stream.<Path>empty()) {
			assertEquals(List.of(), preparedFiles.toList());
		}
	}

	@Test
	void rejectsExtractionEntryNamesWithoutFileNameComponent() throws Exception {
		var method = ZipFileUtility.class.getDeclaredMethod("safeExtractionEntryName", String.class);
		method.setAccessible(true);

		java.lang.reflect.InvocationTargetException exception = assertThrows(
				java.lang.reflect.InvocationTargetException.class,
				() -> method.invoke(null, "/")
		);

		assertTrue(exception.getCause() instanceof ZipExtractionException);
		assertTrue(exception.getCause().getMessage().contains("must include a file name component"));
	}

	@Test
	void rejectsZipSlipEntriesThatResolveOutsidePreparedExtractDirectory() throws Exception {
		Path preparedDir = tempDir.resolve("prepared");
		List<String> maliciousEntries = List.of("../evil.csv", "..\\evil.csv", "/evil.csv", "C:/evil.csv");

		for (String maliciousEntry : maliciousEntries) {
			Path zipPath = tempDir.resolve("slip-" + sanitize(maliciousEntry) + ".zip");
			writeZip(zipPath, new ZipTestEntry(maliciousEntry, "id\n1\n"));

			ZipExtractionException exception = assertThrows(
					ZipExtractionException.class,
					() -> ZipFileUtility.extractSingleFile(zipPath, preparedDir, maliciousEntry)
			);

			assertTrue(exception.getMessage().contains("outside the prepared extract directory"));
			assertFalse(Files.exists(preparedDir.resolve("evil.csv")));
			try (var preparedFiles = Files.exists(preparedDir) ? Files.list(preparedDir) : java.util.stream.Stream.<Path>empty()) {
				assertEquals(List.of(), preparedFiles.toList());
			}
		}
	}

	@Test
	void refusesToOverwriteExistingPreparedFileDuringExtraction() throws Exception {
		Path preparedDir = tempDir.resolve("prepared");
		Files.createDirectories(preparedDir);
		Path existingPreparedFile = preparedDir.resolve("events.csv");
		Files.writeString(existingPreparedFile, "id,eventTime\nEXISTING,00:00:00\n", StandardCharsets.UTF_8);

		Path zipPath = tempDir.resolve("events.zip");
		writeZip(zipPath, new ZipTestEntry("events.csv", "id,eventTime\nEVT-1,08:30:00\n"));

		ZipExtractionException exception = assertThrows(
				ZipExtractionException.class,
				() -> ZipFileUtility.extractSingleFile(zipPath, preparedDir, "events.csv")
		);

		assertTrue(exception.getMessage().contains("Refusing to overwrite existing prepared file"));
		assertEquals("id,eventTime\nEXISTING,00:00:00\n", Files.readString(existingPreparedFile));
	}

	@Test
	void failsFastWithZipPackagingExceptionWhenSourceFileDoesNotExist() {
		Path missingSource = tempDir.resolve("missing.csv");
		Path zipPath = tempDir.resolve("archive").resolve("orders.zip");

		ZipPackagingException exception = assertThrows(
				ZipPackagingException.class,
				() -> ZipFileUtility.packageSingleFile(missingSource, zipPath, "orders.csv")
		);

		assertTrue(exception.getMessage().contains("ZIP package source artifact must exist"));
	}

	@Test
	void rejectsPackagingTargetPathsThatResolveToExistingDirectories() throws Exception {
		Path sourceFile = tempDir.resolve("partner-drop").resolve("orders.csv");
		Files.createDirectories(sourceFile.getParent());
		Files.writeString(sourceFile, "id\nA-1\n", StandardCharsets.UTF_8);
		Path zipDirectory = tempDir.resolve("archive-target");
		Files.createDirectories(zipDirectory);

		ZipPackagingException exception = assertThrows(
				ZipPackagingException.class,
				() -> ZipFileUtility.packageSingleFile(sourceFile, zipDirectory, "orders.csv")
		);

		assertTrue(exception.getMessage().contains("must not be a directory"));
	}

	@Test
	void rejectsPackagingTargetPathsThatAliasTheSourceFile() throws Exception {
		Path sourceFile = tempDir.resolve("partner-drop").resolve("orders.csv");
		Files.createDirectories(sourceFile.getParent());
		Files.writeString(sourceFile, "id\nA-1\n", StandardCharsets.UTF_8);

		ZipPackagingException exception = assertThrows(
				ZipPackagingException.class,
				() -> ZipFileUtility.packageSingleFile(sourceFile, sourceFile, "orders.csv")
		);

		assertTrue(exception.getMessage().contains("must differ from the source file path"));
		assertEquals("id\nA-1\n", Files.readString(sourceFile));
	}

	@Test
	void rejectsPackagingEntryNamesWithoutFileNameComponent() throws Exception {
		Path sourceFile = tempDir.resolve("partner-drop").resolve("orders.csv");
		Files.createDirectories(sourceFile.getParent());
		Files.writeString(sourceFile, "id\nA-1\n", StandardCharsets.UTF_8);
		Path zipPath = tempDir.resolve("archive").resolve("orders.zip");

		ZipPackagingException exception = assertThrows(
				ZipPackagingException.class,
				() -> ZipFileUtility.packageSingleFile(sourceFile, zipPath, "/")
		);

		assertTrue(exception.getMessage().contains("must include a file name component"));
		assertFalse(Files.exists(zipPath));
	}

	@Test
	void deletesPartiallyCreatedZipWhenPackagingFails() throws Exception {
		Path sourceFile = tempDir.resolve("partner-drop").resolve("orders.csv");
		Files.createDirectories(sourceFile.getParent());
		Files.writeString(sourceFile, "id\nA-1\n", StandardCharsets.UTF_8);
		Path zipPath = tempDir.resolve("archive").resolve("orders.zip");

		ZipPackagingException exception = assertThrows(
				ZipPackagingException.class,
				() -> ZipFileUtility.packageSingleFile(sourceFile, zipPath, "orders.csv", (path, outputStream) -> {
					outputStream.write("partial".getBytes(StandardCharsets.UTF_8));
					throw new IOException("Simulated ZIP copy failure");
				})
		);

		assertTrue(exception.getMessage().contains("Failed to package ZIP artifact"));
		assertTrue(exception.getCause() instanceof IOException);
		assertEquals("Simulated ZIP copy failure", exception.getCause().getMessage());
		assertFalse(Files.exists(zipPath));
	}

	private List<String> zipEntryNames(Path zipPath) throws Exception {
		try (ZipInputStream inputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
			java.util.ArrayList<String> names = new java.util.ArrayList<>();
			ZipEntry entry;
			while ((entry = inputStream.getNextEntry()) != null) {
				if (!entry.isDirectory()) {
					names.add(entry.getName());
				}
			}
			return names;
		}
	}

	private String readOrdersZipEntry(Path zipPath) throws Exception {
		try (ZipInputStream inputStream = new ZipInputStream(Files.newInputStream(zipPath))) {
			ZipEntry entry;
			while ((entry = inputStream.getNextEntry()) != null) {
				if (!entry.isDirectory() && "orders.csv".equals(entry.getName())) {
					return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
				}
			}
		}
		throw new IllegalArgumentException("Expected ZIP entry was not found: orders.csv");
	}

	private void writeZip(Path zipPath, ZipTestEntry... entries) throws Exception {
		Files.createDirectories(zipPath.getParent());
		try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
			for (ZipTestEntry entry : entries) {
				outputStream.putNextEntry(new ZipEntry(entry.name()));
				outputStream.write(entry.contents().getBytes(StandardCharsets.UTF_8));
				outputStream.closeEntry();
			}
		}
	}

	private String sanitize(String value) {
		return value.replaceAll("[^a-zA-Z0-9._-]", "-");
	}

	private record ZipTestEntry(String name, String contents) {
	}
}