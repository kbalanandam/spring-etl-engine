package com.etl.config.source;

/**
 * Shared contract for file-backed sources that can expose archive-on-success behavior.
 */
public interface FileSourceConfig {

	String getFilePath();

	void setFilePath(String filePath);

	FileArchiveConfig getArchiveConfig();

	default boolean isArchiveOnSuccessEnabled() {
		FileArchiveConfig archiveConfig = getArchiveConfig();
		return archiveConfig != null && archiveConfig.isEnabled();
	}
}

