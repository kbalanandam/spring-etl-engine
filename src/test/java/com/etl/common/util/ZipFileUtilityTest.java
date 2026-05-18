package com.etl.common.util;

import com.etl.exception.ZipExtractionException;
import com.etl.exception.ZipPackagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
		try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(zipPath))) {
			for (ZipTestEntry entry : entries) {
				outputStream.putNextEntry(new ZipEntry(entry.name()));
				outputStream.write(entry.contents().getBytes(StandardCharsets.UTF_8));
				outputStream.closeEntry();
			}
		}
	}

	private record ZipTestEntry(String name, String contents) {
	}
}