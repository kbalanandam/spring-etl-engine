package com.etl.processor.exception;

/**
 * Exception thrown when a dynamic field mapping or processor-level operation fails.
 */
public class NoProcessorFoundException extends ProcessorException {

    public NoProcessorFoundException(String message) {
        super(message);
    }

    public NoProcessorFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
