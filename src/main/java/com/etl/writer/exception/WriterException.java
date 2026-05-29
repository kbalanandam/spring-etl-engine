package com.etl.writer.exception;

/**
 * @deprecated Use {@link com.etl.exception.writer.WriterException}.
 */
@Deprecated(forRemoval = false, since = "1.7.9")
public class WriterException extends com.etl.exception.writer.WriterException {

    public WriterException(String message) {
        super(message);
    }

    public WriterException(String message, Throwable cause) {
        super(message, cause);
    }
}
