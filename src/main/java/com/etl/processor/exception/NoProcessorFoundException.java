package com.etl.processor.exception;

/**
 * @deprecated Use {@link com.etl.exception.processor.NoProcessorFoundException}.
 */
@Deprecated(forRemoval = false, since = "1.7.9")
public class NoProcessorFoundException extends com.etl.exception.processor.NoProcessorFoundException {

    public NoProcessorFoundException(String message) {
        super(message);
    }

    public NoProcessorFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
