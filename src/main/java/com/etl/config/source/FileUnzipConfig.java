package com.etl.config.source;

/**
 * Optional advanced ZIP-preparation overrides for file-backed sources.
 *
 * <p>The common shipped path now infers ZIP preparation directly from a `.zip` {@code filePath}.
 * Use this block only when a scenario needs an override such as a custom extract directory or an
 * explicit ZIP entry selection.</p>
 */
public class FileUnzipConfig {

	private boolean enabled;
	private String extractDir;
	private String entryName;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public String getExtractDir() {
		return extractDir;
	}

	public void setExtractDir(String extractDir) {
		this.extractDir = extractDir;
	}

	public String getEntryName() {
		return entryName;
	}

	public void setEntryName(String entryName) {
		this.entryName = entryName;
	}
}


