package com.etl.exception;

/**
 * Low-level ZIP artifact failure used to preserve ZIP-specific cause types without introducing a
 * new top-level operator error category.
 */
public abstract class ZipArtifactException extends IllegalArgumentException {

	protected ZipArtifactException(String message) {
		super(message);
	}

	protected ZipArtifactException(String message, Throwable cause) {
		super(message, cause);
	}
}

