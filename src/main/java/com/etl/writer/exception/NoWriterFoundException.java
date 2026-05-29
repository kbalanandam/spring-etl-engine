package com.etl.writer.exception;

/**
 * @deprecated Use {@link com.etl.exception.writer.NoWriterFoundException}.
 */
@Deprecated(forRemoval = false, since = "1.7.9")
public class NoWriterFoundException extends com.etl.exception.writer.NoWriterFoundException {

    public NoWriterFoundException(String message) {
        super(message);
    }

    public NoWriterFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
