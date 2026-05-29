package com.etl.processor.exception;

/**
 * @deprecated Use {@link com.etl.exception.processor.ProcessorException}.
 */
@Deprecated(forRemoval = false, since = "1.7.9")
public class ProcessorException extends com.etl.exception.processor.ProcessorException {
    public ProcessorException(String message) {
        super(message);
    }

    public ProcessorException(String message, Throwable cause) {
        super(message, cause);
    }
}
