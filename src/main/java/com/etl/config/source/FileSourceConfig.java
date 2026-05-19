package com.etl.config.source;

import java.util.Locale;

/**
 * Shared contract for file-backed sources that can expose archive-on-success behavior.
 */
public interface FileSourceConfig {

	String getFilePath();

	void setFilePath(String filePath);

	FileUnzipConfig getUnzipConfig();

	String getPreparedFilePath();

	void setPreparedFilePath(String preparedFilePath);

	FileArchiveConfig getArchiveConfig();

	default boolean hasZipFilePath() {
		String filePath = getFilePath();
		return filePath != null && filePath.trim().toLowerCase(Locale.ROOT).endsWith(".zip");
	}

	default boolean isUnzipEnabled() {
		FileUnzipConfig unzipConfig = getUnzipConfig();
		return hasZipFilePath() || (unzipConfig != null && unzipConfig.isEnabled());
	}

	default boolean isExplicitUnzipEnabled() {
		FileUnzipConfig unzipConfig = getUnzipConfig();
		return unzipConfig != null && unzipConfig.isEnabled();
	}

	default boolean isArchiveOnSuccessEnabled() {
		FileArchiveConfig archiveConfig = getArchiveConfig();
		return archiveConfig != null && archiveConfig.isEnabled();
	}
}

