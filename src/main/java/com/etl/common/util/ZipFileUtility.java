package com.etl.common.util;

import com.etl.exception.ZipExtractionException;
import com.etl.exception.ZipPackagingException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Shared ZIP utility for local staged file artifacts.
 *
 * <p>This utility stays independent from any specific source type, reader, writer, or transport.
 * Any OneFlow path that has a local readable file artifact, including future staged transports such
 * as SFTP, can reuse it to extract one file from a ZIP or package one file into a ZIP.</p>
 *
 * <p>Callers still own higher-level lifecycle policy such as staging-location decisions, cleanup,
 * archive naming, and runtime evidence.</p>
 */
public final class ZipFileUtility {

	private ZipFileUtility() {
	}

	public static Path extractSingleFile(Path archivePath, Path extractDir, String configuredEntryName) throws IOException {
		Path extractedFilePath = null;
		try {
			requireReadableRegularFileForExtraction(archivePath);
			createParentDirectories(extractDir);
			String normalizedConfiguredEntryName = normalizeEntryName(configuredEntryName);
			List<String> candidateEntries = new ArrayList<>();
			boolean extracted = false;

			try (InputStream inputStream = Files.newInputStream(archivePath);
			     ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
				ZipEntry entry;
				while ((entry = zipInputStream.getNextEntry()) != null) {
					if (entry.isDirectory()) {
						continue;
					}

					String normalizedEntryName = normalizeEntryName(entry.getName());
					candidateEntries.add(normalizedEntryName);
					if (!matchesConfiguredEntry(normalizedConfiguredEntryName, normalizedEntryName)) {
						continue;
					}

					if (normalizedConfiguredEntryName == null && extracted) {
						throw multipleFileEntriesFound(candidateEntries);
					}

					Path targetPath = toExtractedFilePath(extractDir, normalizedEntryName);
					createParentDirectories(targetPath.getParent());
					Files.copy(zipInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
					extractedFilePath = targetPath;
					extracted = true;
				}
			}

			if (!extracted) {
				throw missingZipEntry(normalizedConfiguredEntryName, archivePath);
			}

			return extractedFilePath;
		} catch (IOException e) {
			deleteIfExistsQuietly(extractedFilePath);
			throw new ZipExtractionException("Failed to extract ZIP source artifact: " + archivePath, e);
		} catch (RuntimeException e) {
			deleteIfExistsQuietly(extractedFilePath);
			throw e;
		}
	}

	public static void packageSingleFile(Path sourceFilePath, Path zipFilePath, String entryName) throws IOException {
		try {
			requireReadableRegularFileForPackaging(sourceFilePath);
			createParentDirectories(zipFilePath.toAbsolutePath().normalize().getParent());

			String archiveEntryName = safePackagingEntryName(entryName);
			try (OutputStream outputStream = Files.newOutputStream(zipFilePath);
			     ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
				zipOutputStream.putNextEntry(new ZipEntry(archiveEntryName));
				Files.copy(sourceFilePath, zipOutputStream);
				zipOutputStream.closeEntry();
			}
		} catch (IOException e) {
			throw new ZipPackagingException("Failed to package ZIP artifact from source file: " + sourceFilePath, e);
		}
	}

	private static void requireReadableRegularFileForExtraction(Path path) {
		if (!Files.exists(path)) {
			throw new ZipExtractionException("ZIP source artifact must exist: " + path);
		}
		if (!Files.isRegularFile(path)) {
			throw new ZipExtractionException("ZIP source artifact must be a regular file: " + path);
		}
		if (!Files.isReadable(path)) {
			throw new ZipExtractionException("ZIP source artifact must be readable: " + path);
		}
	}

	private static void requireReadableRegularFileForPackaging(Path path) {
		if (!Files.exists(path)) {
			throw new ZipPackagingException("ZIP package source artifact must exist: " + path);
		}
		if (!Files.isRegularFile(path)) {
			throw new ZipPackagingException("ZIP package source artifact must be a regular file: " + path);
		}
		if (!Files.isReadable(path)) {
			throw new ZipPackagingException("ZIP package source artifact must be readable: " + path);
		}
	}

	private static Path toExtractedFilePath(Path extractDir, String entryName) {
		// Matching honors the full normalized ZIP entry path, but the extracted working file stays
		// basename-only so callers do not inherit ZIP-internal folder structures in local staging.
		Path targetPath = extractDir.resolve(safeExtractionEntryName(entryName)).normalize();
		if (!targetPath.startsWith(extractDir)) {
			throw new ZipExtractionException("ZIP entry resolves outside the prepared extract directory: " + entryName);
		}
		return targetPath;
	}

	private static String safeExtractionEntryName(String entryName) {
		String normalizedEntryName = normalizeEntryName(entryName);
		if (normalizedEntryName == null) {
			throw new ZipExtractionException("ZIP entry name must not be blank.");
		}
		return Path.of(normalizedEntryName).getFileName().toString();
	}

	private static String safePackagingEntryName(String entryName) {
		String normalizedEntryName = normalizeEntryName(entryName);
		if (normalizedEntryName == null) {
			throw new ZipPackagingException("ZIP entry name must not be blank.");
		}
		// Packaging also uses basename-only output so callers can provide either a simple filename or
		// a ZIP-internal path without leaking nested folder structures into the shared contract.
		return Path.of(normalizedEntryName).getFileName().toString();
	}

	private static boolean matchesConfiguredEntry(String configuredEntryName, String candidateEntryName) {
		return configuredEntryName == null || configuredEntryName.equals(candidateEntryName);
	}

	private static IllegalArgumentException multipleFileEntriesFound(List<String> candidateEntries) {
		return new ZipExtractionException(
				"ZIP source preparation currently supports exactly one file entry unless unzip.entryName is configured. Found entries="
						+ candidateEntries);
	}

	private static IllegalArgumentException missingZipEntry(String configuredEntryName, Path archivePath) {
		if (configuredEntryName != null) {
			return new ZipExtractionException("Configured unzip.entryName '" + configuredEntryName
					+ "' was not found in ZIP source artifact: " + archivePath);
		}
		return new ZipExtractionException("ZIP source artifact must contain at least one file entry: " + archivePath);
	}

	private static String normalizeEntryName(String entryName) {
		if (entryName == null || entryName.isBlank()) {
			return null;
		}
		return entryName.trim().replace('\\', '/');
	}

	private static void createParentDirectories(Path directory) throws IOException {
		if (directory != null) {
			Files.createDirectories(directory);
		}
	}

	private static void deleteIfExistsQuietly(Path path) {
		if (path == null) {
			return;
		}
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// Preserve the original failure.
		}
	}
}


