package com.etl.exception;

/**
 * Helper methods for extracting operator-friendly details from runtime failures.
 */
public final class EtlExceptionDetails {

	private EtlExceptionDetails() {
	}

	public static EtlErrorCategory categoryOf(Throwable failure) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (current instanceof EtlException etlException) {
				return etlException.category();
			}
		}
		return EtlErrorCategory.UNCLASSIFIED;
	}

	public static String categoryValueOf(Throwable failure) {
		return categoryOf(failure).logValue();
	}

	public static Throwable rootCause(Throwable failure) {
		Throwable current = failure;
		while (current != null && current.getCause() != null && current.getCause() != current) {
			current = current.getCause();
		}
		return current;
	}

	public static String rootCauseMessage(Throwable failure) {
		Throwable rootCause = rootCause(failure);
		if (rootCause == null || rootCause.getMessage() == null || rootCause.getMessage().isBlank()) {
			return "none";
		}
		return rootCause.getMessage();
	}

	public static String exceptionType(Throwable failure) {
		return failure == null ? "unknown" : failure.getClass().getSimpleName();
	}
}

