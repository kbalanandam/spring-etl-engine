package com.etl.exception;

/**
 * Low-level ZIP packaging failure.
 */
public class ZipPackagingException extends ZipArtifactException {

	public ZipPackagingException(String message) {
		super(message);
	}

	public ZipPackagingException(String message, Throwable cause) {
		super(message, cause);
	}
}

