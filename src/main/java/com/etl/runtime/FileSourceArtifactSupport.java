package com.etl.runtime;

import com.etl.common.util.ZipFileUtility;
import com.etl.config.source.FileSourceConfig;
import com.etl.config.source.FileArchiveConfig;
import com.etl.config.source.FileUnzipConfig;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Shared support for preparing readable file artifacts from file-backed source inputs.
 *
	 * <p>The shipped ZIP slice auto-prepares local ZIP-backed file paths before validation and reading
	 * begin, while still preserving the original configured file path as the source artifact used for
	 * reject/archive handling. Within this support path, use {@code artifact} for lifecycle-owned
	 * original/reject/archive identity and {@code file}/{@code path} for prepared readable working
	 * files.</p>
 */
public class FileSourceArtifactSupport {

	private static final String DEFAULT_PREPARED_ROOT_FOLDER = "spring-etl-engine";
	private static final String DEFAULT_PREPARED_SOURCE_FOLDER = "prepared-sources";

	public FileSourceArtifactSupport() {
	}

	public Path resolveReadablePath(FileSourceConfig fileSourceConfig) {
		validateUnzipConfiguration(fileSourceConfig);
		Path originalSourcePath = originalSourcePath(fileSourceConfig);
		if (!fileSourceConfig.isUnzipEnabled()) {
			return originalSourcePath;
		}

		String preparedFilePath = fileSourceConfig.getPreparedFilePath();
		if (preparedFilePath != null && !preparedFilePath.isBlank()) {
			Path preparedPath = Path.of(preparedFilePath).toAbsolutePath().normalize();
			if (Files.exists(preparedPath)) {
				return preparedPath;
			}
		}

		return unzipToPreparedFile(fileSourceConfig, originalSourcePath);
	}

	public void validateUnzipConfiguration(FileSourceConfig fileSourceConfig) {
		if (!fileSourceConfig.isUnzipEnabled()) {
			return;
		}

		if (fileSourceConfig.getFilePath() == null || fileSourceConfig.getFilePath().isBlank()) {
			throw new IllegalArgumentException("ZIP source preparation requires a non-blank filePath.");
		}

		if (fileSourceConfig.isExplicitUnzipEnabled() && !fileSourceConfig.hasZipFilePath()) {
			throw new IllegalArgumentException("unzip.enabled=true requires filePath to reference a .zip source artifact.");
		}
	}

	public void validateArchiveConfiguration(FileSourceConfig fileSourceConfig) {
		FileArchiveConfig archiveConfig = fileSourceConfig.getArchiveConfig();
		if (archiveConfig == null || !archiveConfig.isEnabled()) {
			return;
		}

		if (archiveConfig.getSuccessPath() == null || archiveConfig.getSuccessPath().isBlank()) {
			throw new IllegalArgumentException("archive.enabled=true requires a non-blank successPath.");
		}

		if (archiveConfig.isPackageAsZip() && fileSourceConfig.hasZipFilePath()) {
			throw new IllegalArgumentException("archive.packageAsZip=true is not supported when filePath already references a .zip source artifact.");
		}
	}

	public Path resolveArchivedPath(Path archiveDirectory, String resolvedName, boolean packageAsZip) {
		String archiveFileName = resolvedName == null ? "" : resolvedName.trim();
		if (archiveFileName.isBlank()) {
			throw new IllegalArgumentException("Archive file name must not be blank.");
		}
		if (packageAsZip && !archiveFileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
			archiveFileName = archiveFileName + ".zip";
		}
		return archiveDirectory.resolve(archiveFileName).normalize();
	}

	public Path originalSourcePath(FileSourceConfig fileSourceConfig) {
		return Path.of(fileSourceConfig.getFilePath()).toAbsolutePath().normalize();
	}

	public Path rejectablePath(FileSourceConfig fileSourceConfig, Path readableFilePath) {
		return fileSourceConfig.isUnzipEnabled() ? originalSourcePath(fileSourceConfig) : readableFilePath;
	}

	public void cleanupPreparedFile(FileSourceConfig fileSourceConfig) {
		String preparedFilePath = fileSourceConfig.getPreparedFilePath();
		if (preparedFilePath == null || preparedFilePath.isBlank()) {
			return;
		}

		Path preparedPath = Path.of(preparedFilePath).toAbsolutePath().normalize();
		Path originalSourcePath = originalSourcePath(fileSourceConfig);
		try {
			if (!preparedPath.equals(originalSourcePath)) {
				Files.deleteIfExists(preparedPath);
				prunePreparedDirectoriesIfOwned(fileSourceConfig, preparedPath);
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to delete prepared source file: " + preparedPath, e);
		} finally {
			fileSourceConfig.setPreparedFilePath(null);
		}
	}

	private Path unzipToPreparedFile(FileSourceConfig fileSourceConfig, Path archivePath) {
		validateReadableRegularFile(archivePath, "ZIP source artifact");
		FileUnzipConfig unzipConfig = fileSourceConfig.getUnzipConfig();
		Path extractDir;
		boolean runtimeOwnedExtractDir;
		try {
			extractDir = resolveExtractDir(fileSourceConfig, archivePath);
			runtimeOwnedExtractDir = isRuntimeOwnedExtractDir(fileSourceConfig, extractDir);
		} catch (IOException e) {
			throw new IllegalArgumentException("Failed to create prepared extract directory for ZIP source file: " + archivePath, e);
		}
		String configuredEntryName = unzipConfig == null ? null : unzipConfig.getEntryName();

		try {
			Path extractedFilePath = ZipFileUtility.extractSingleFile(archivePath, extractDir, configuredEntryName);
			fileSourceConfig.setPreparedFilePath(extractedFilePath.toString());
			return extractedFilePath;
		} catch (IOException e) {
			deleteExtractedDirectoryContentsIfPresent(extractDir, runtimeOwnedExtractDir);
			throw new IllegalArgumentException("Failed to extract ZIP source file: " + archivePath, e);
		} catch (RuntimeException e) {
			deleteExtractedDirectoryContentsIfPresent(extractDir, runtimeOwnedExtractDir);
			throw e;
		}
	}

	public void validateReadableRegularFile(Path path, String description) {
		if (!Files.exists(path)) {
			throw new IllegalArgumentException(description + " must exist: " + path);
		}
		if (!Files.isRegularFile(path)) {
			throw new IllegalArgumentException(description + " must be a regular file: " + path);
		}
		if (!Files.isReadable(path)) {
			throw new IllegalArgumentException(description + " must be readable: " + path);
		}
	}

	private Path resolveExtractDir(FileSourceConfig fileSourceConfig, Path archivePath) throws IOException {
		FileUnzipConfig unzipConfig = fileSourceConfig.getUnzipConfig();
		if (unzipConfig != null && unzipConfig.getExtractDir() != null && !unzipConfig.getExtractDir().isBlank()) {
			return Path.of(unzipConfig.getExtractDir().trim()).toAbsolutePath().normalize();
		}
		return defaultExtractDir(archivePath);
	}

	private Path defaultExtractDir(Path archivePath) throws IOException {
		Path preparedRoot = defaultPreparedRoot();
		Files.createDirectories(preparedRoot);
		return Files.createTempDirectory(preparedRoot, tempDirectoryPrefix(archivePath)).toAbsolutePath().normalize();
	}

	private Path defaultPreparedRoot() {
		return Path.of(System.getProperty("java.io.tmpdir"))
				.toAbsolutePath()
				.normalize()
				.resolve(DEFAULT_PREPARED_ROOT_FOLDER)
				.resolve(DEFAULT_PREPARED_SOURCE_FOLDER);
	}

	private String tempDirectoryPrefix(Path archivePath) {
		String archiveFileName = archivePath.getFileName() == null ? "source" : stripExtension(archivePath.getFileName().toString());
		String sanitized = archiveFileName.replaceAll("[^a-zA-Z0-9._-]", "-");
		String prefix = sanitized.isBlank() ? "source" : sanitized;
		if (prefix.length() < 3) {
			prefix = (prefix + "---").substring(0, 3);
		}
		return prefix + "-";
	}

	private String stripExtension(String fileName) {
		int extensionIndex = fileName.lastIndexOf('.');
		if (extensionIndex <= 0) {
			return fileName;
		}
		return fileName.substring(0, extensionIndex);
	}

	private void deleteExtractedDirectoryContentsIfPresent(Path extractDir, boolean prunePreparedDirectories) {
		if (extractDir == null) {
			return;
		}
		try {
			if (Files.exists(extractDir)) {
				try (var children = Files.list(extractDir)) {
					children.forEach(this::deleteQuietly);
				}
			}
			if (prunePreparedDirectories) {
				pruneEmptyDirectories(extractDir, defaultPreparedRoot());
			}
		} catch (IOException ignored) {
			// Preserve the original unzip failure; a later run can safely recreate the working file.
		}
	}

	private void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// Preserve the original unzip failure; a later run can safely recreate the working file.
		}
	}

	private void prunePreparedDirectoriesIfOwned(FileSourceConfig fileSourceConfig, Path preparedPath) throws IOException {
		if (!isRuntimeOwnedExtractDir(fileSourceConfig, preparedPath.getParent())) {
			return;
		}
		pruneEmptyDirectories(preparedPath.getParent(), defaultPreparedRoot());
	}

	private boolean isRuntimeOwnedExtractDir(FileSourceConfig fileSourceConfig, Path extractDir) {
		if (extractDir == null || !extractDir.startsWith(defaultPreparedRoot())) {
			return false;
		}
		FileUnzipConfig unzipConfig = fileSourceConfig.getUnzipConfig();
		return unzipConfig == null || unzipConfig.getExtractDir() == null || unzipConfig.getExtractDir().isBlank();
	}

	private void pruneEmptyDirectories(Path startingDirectory, Path pruneRoot) throws IOException {
		Path current = startingDirectory;
		while (current != null && current.startsWith(pruneRoot)) {
			try {
				Files.delete(current);
			} catch (DirectoryNotEmptyException e) {
				break;
			} catch (NoSuchFileException ignored) {
				// Continue upward in case a child directory was already removed.
			}
			if (current.equals(pruneRoot)) {
				break;
			}
			current = current.getParent();
		}
	}
}





