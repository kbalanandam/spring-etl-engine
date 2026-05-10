package com.etl.config.source;

/**
 * Shared archive-on-success configuration for file-backed sources.
 */
public class FileArchiveConfig {

	private boolean enabled;
	private String successPath;
	private String namePattern;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getSuccessPath() {
		return successPath;
	}

	public void setSuccessPath(String successPath) {
		this.successPath = successPath;
	}

	public String getNamePattern() {
		return namePattern;
	}

	public void setNamePattern(String namePattern) {
		this.namePattern = namePattern;
	}
}

