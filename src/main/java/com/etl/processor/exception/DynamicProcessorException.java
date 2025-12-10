package com.etl.processor.exception;

/**
 * Exception thrown when a dynamic field mapping or processor-level operation fails.
 */
public class DynamicProcessorException extends RuntimeException {

    public DynamicProcessorException(String message) {
        super(message);
    }

    public DynamicProcessorException(String message, Throwable cause) {
        super(message, cause);
    }
}
