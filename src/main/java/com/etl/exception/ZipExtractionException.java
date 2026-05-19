package com.etl.exception;

/**
 * Low-level ZIP extraction failure.
 */
public class ZipExtractionException extends ZipArtifactException {

	public ZipExtractionException(String message) {
		super(message);
	}

	public ZipExtractionException(String message, Throwable cause) {
		super(message, cause);
	}
}

