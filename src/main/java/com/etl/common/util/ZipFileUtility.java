package com.etl.common.util;

import com.etl.exception.ZipExtractionException;
import com.etl.exception.ZipPackagingException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiFunction;
import java.util.function.Function;
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

	/**
	 * Internal seam used by tests to force deterministic copy failures without mocking static Files APIs.
	 */
	@FunctionalInterface
	interface SourceFileCopyOperation {
		void copy(Path sourceFilePath, OutputStream outputStream) throws IOException;
	}

	private ZipFileUtility() {
	}

	/**
	 * Extract exactly one matching file entry from a ZIP artifact into a prepared local directory.
	 *
	 * <p>If {@code configuredEntryName} is provided, only that normalized entry is eligible. If it is
	 * absent, the ZIP must contain exactly one file entry. The extracted file name is always basename-only
	 * so ZIP-internal folder structure is not propagated into local staging.</p>
	 *
	 * <p>On any failure after a file is created, this method removes the partially extracted file.</p>
	 */
	public static Path extractSingleFile(Path archivePath, Path extractDir, String configuredEntryName) throws IOException {
		Path extractedFilePath = null;
		try {
			requireReadableRegularFileForExtraction(archivePath);
			createParentDirectories(extractDir);
			String normalizedConfiguredEntryName = normalizeEntryName(configuredEntryName);
			// Track only matched entries for diagnostics; avoid accumulating all archive entries.
			String firstMatchedEntryName = null;
			boolean extracted = false;

			try (InputStream inputStream = Files.newInputStream(archivePath);
			     ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
				ZipEntry entry;
				while ((entry = zipInputStream.getNextEntry()) != null) {
					if (entry.isDirectory()) {
						continue;
					}

					String normalizedEntryName = normalizeEntryName(entry.getName());
					if (!matchesConfiguredEntry(normalizedConfiguredEntryName, normalizedEntryName)) {
						continue;
					}

					// A second matching entry is always invalid: extraction must be one-entry deterministic.
					if (extracted) {
						throw duplicateMatchingEntriesFound(normalizedConfiguredEntryName, firstMatchedEntryName, normalizedEntryName);
					}

					Path targetPath = toExtractedFilePath(extractDir, normalizedEntryName);
					createParentDirectories(targetPath.getParent());
					failIfTargetAlreadyExists(targetPath, archivePath, normalizedEntryName);
					Files.copy(zipInputStream, targetPath);
					extractedFilePath = targetPath;
					firstMatchedEntryName = normalizedEntryName;
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

	/**
	 * Package one source file as a single-entry ZIP artifact.
	 *
	 * <p>The ZIP entry name is normalized and written as basename-only. On any packaging failure, this
	 * method removes the partially created ZIP file so callers do not inherit orphaned artifacts.</p>
	 */
	public static void packageSingleFile(Path sourceFilePath, Path zipFilePath, String entryName) {
		packageSingleFile(sourceFilePath, zipFilePath, entryName, Files::copy);
	}

	/**
	 * Internal overload that accepts a copy operation for deterministic failure-path tests.
	 */
	static void packageSingleFile(
			Path sourceFilePath,
			Path zipFilePath,
			String entryName,
			SourceFileCopyOperation copyOperation) {
		Path normalizedZipFilePath = null;
		try {
			requireReadableRegularFileForPackaging(sourceFilePath);
			Path normalizedSourceFilePath = sourceFilePath.toAbsolutePath().normalize();
			normalizedZipFilePath = validatePackagingTargetPath(zipFilePath, normalizedSourceFilePath);
			createParentDirectories(normalizedZipFilePath.getParent());

			String archiveEntryName = safePackagingEntryName(entryName);
			try (OutputStream outputStream = Files.newOutputStream(normalizedZipFilePath);
			     ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
				zipOutputStream.putNextEntry(new ZipEntry(archiveEntryName));
				copyOperation.copy(sourceFilePath, zipOutputStream);
				zipOutputStream.closeEntry();
			}
		} catch (IOException e) {
			// Best-effort cleanup so failed packaging never leaves an orphaned partial ZIP file.
			deleteIfExistsQuietly(normalizedZipFilePath);
			throw new ZipPackagingException("Failed to package ZIP artifact from source file: " + sourceFilePath, e);
		} catch (RuntimeException e) {
			// Keep runtime failures symmetric with I/O failures from a lifecycle/cleanup perspective.
			deleteIfExistsQuietly(normalizedZipFilePath);
			throw e;
		}
	}

	private static Path validatePackagingTargetPath(Path zipFilePath, Path normalizedSourceFilePath) {
		if (zipFilePath == null) {
			throw new ZipPackagingException("ZIP package target path must not be null.");
		}

		Path normalizedZipFilePath = zipFilePath.toAbsolutePath().normalize();
		Path targetFileName = normalizedZipFilePath.getFileName();
		if (targetFileName == null) {
			throw new ZipPackagingException("ZIP package target path must include a file name component: " + zipFilePath);
		}
		if (Files.exists(normalizedZipFilePath) && Files.isDirectory(normalizedZipFilePath)) {
			throw new ZipPackagingException("ZIP package target path must not be a directory: " + normalizedZipFilePath);
		}
		if (normalizedZipFilePath.equals(normalizedSourceFilePath)) {
			throw new ZipPackagingException("ZIP package target path must differ from the source file path: " + normalizedZipFilePath);
		}

		return normalizedZipFilePath;
	}

	private static void failIfTargetAlreadyExists(Path targetPath, Path archivePath, String entryName) {
		if (Files.exists(targetPath)) {
			throw new ZipExtractionException(
					"Refusing to overwrite existing prepared file '" + targetPath
							+ "' while extracting ZIP entry '" + entryName + "' from archive: " + archivePath);
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
		String normalizedEntryName = normalizeEntryName(entryName);
		if (normalizedEntryName == null) {
			throw new ZipExtractionException("ZIP entry name must not be blank.");
		}

		Path normalizedExtractDir = extractDir.toAbsolutePath().normalize();
		Path realExtractDir;
		try {
			realExtractDir = normalizedExtractDir.toRealPath().normalize();
		} catch (IOException e) {
			throw new ZipExtractionException("Prepared extract directory must exist and be readable: " + extractDir, e);
		}

		validateEntryResolvesWithinExtractDir(realExtractDir, normalizedEntryName);

		// Matching honors the full normalized ZIP entry path, but the extracted working file stays
		// basename-only so callers do not inherit ZIP-internal folder structures in local staging.
		Path targetPath = normalizedExtractDir.resolve(safeExtractionEntryName(normalizedEntryName)).normalize();
		if (!targetPath.startsWith(normalizedExtractDir)) {
			throw new ZipExtractionException("ZIP entry resolves outside the prepared extract directory: " + entryName);
		}
		return targetPath;
	}

	private static void validateEntryResolvesWithinExtractDir(Path realExtractDir, String normalizedEntryName) {
		if (isAbsoluteLikeEntryName(normalizedEntryName) || hasParentTraversalSegment(normalizedEntryName)) {
			throw new ZipExtractionException("ZIP entry resolves outside the prepared extract directory: " + normalizedEntryName);
		}

		Path entryPath;
		try {
			entryPath = Path.of(normalizedEntryName);
		} catch (RuntimeException e) {
			throw new ZipExtractionException("ZIP entry path is invalid: " + normalizedEntryName, e);
		}

		Path resolvedEntryPath = realExtractDir.resolve(entryPath).normalize();
		if (!resolvedEntryPath.startsWith(realExtractDir)) {
			throw new ZipExtractionException("ZIP entry resolves outside the prepared extract directory: " + normalizedEntryName);
		}
	}

	private static boolean isAbsoluteLikeEntryName(String normalizedEntryName) {
		if (normalizedEntryName == null) {
			return false;
		}

		return normalizedEntryName.startsWith("/")
				|| normalizedEntryName.startsWith("\\")
				|| normalizedEntryName.matches("^[A-Za-z]:/.*")
				|| normalizedEntryName.startsWith("//");
	}

	private static boolean hasParentTraversalSegment(String normalizedEntryName) {
		if (normalizedEntryName == null || normalizedEntryName.isBlank()) {
			return false;
		}

		String[] segments = normalizedEntryName.split("/");
		for (String segment : segments) {
			if ("..".equals(segment)) {
				return true;
			}
		}
		return false;
	}

	private static String safeExtractionEntryName(String entryName) {
		String normalizedEntryName = normalizeEntryName(entryName);
		if (normalizedEntryName == null) {
			throw new ZipExtractionException("ZIP entry name must not be blank.");
		}
		return requireEntryFileNameComponent(
				normalizedEntryName,
				name -> new ZipExtractionException("ZIP entry name must include a file name component: " + name),
				(name, cause) -> new ZipExtractionException("ZIP entry path is invalid: " + name, cause)
		);
	}

	private static String safePackagingEntryName(String entryName) {
		String normalizedEntryName = normalizeEntryName(entryName);
		if (normalizedEntryName == null) {
			throw new ZipPackagingException("ZIP entry name must not be blank.");
		}
		// Packaging also uses basename-only output so callers can provide either a simple filename or
		// a ZIP-internal path without leaking nested folder structures into the shared contract.
		return requireEntryFileNameComponent(
				normalizedEntryName,
				name -> new ZipPackagingException("ZIP entry name must include a file name component: " + name),
				(name, cause) -> new ZipPackagingException("ZIP entry path is invalid: " + name, cause)
		);
	}

	private static String requireEntryFileNameComponent(
			String normalizedEntryName,
			Function<String, RuntimeException> missingFileNameExceptionFactory,
			BiFunction<String, RuntimeException, RuntimeException> invalidPathExceptionFactory) {
		Path entryPath;
		try {
			entryPath = Path.of(normalizedEntryName);
		} catch (RuntimeException e) {
			throw invalidPathExceptionFactory.apply(normalizedEntryName, e);
		}

		Path fileName = entryPath.getFileName();
		if (fileName == null) {
			throw missingFileNameExceptionFactory.apply(normalizedEntryName);
		}

		return fileName.toString();
	}

	private static boolean matchesConfiguredEntry(String configuredEntryName, String candidateEntryName) {
		return configuredEntryName == null || configuredEntryName.equals(candidateEntryName);
	}

	private static ZipExtractionException duplicateMatchingEntriesFound(
			String configuredEntryName,
			String firstMatchedEntryName,
			String duplicateEntryName) {
		String duplicateSummary = "[" + firstMatchedEntryName + ", " + duplicateEntryName + "]";
		if (configuredEntryName != null) {
			return new ZipExtractionException(
					"ZIP source preparation found multiple matching file entries for configured unzip.entryName '"
							+ configuredEntryName + "'. Found entries=" + duplicateSummary);
		}

		return new ZipExtractionException(
				"ZIP source preparation currently supports exactly one file entry unless unzip.entryName is configured. Found entries="
						+ duplicateSummary);
	}

	private static ZipExtractionException missingZipEntry(String configuredEntryName, Path archivePath) {
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


