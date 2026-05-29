package com.etl.reader.exception;

/**
 * @deprecated Use {@link com.etl.exception.reader.ReaderException}.
 */
@Deprecated(forRemoval = false, since = "1.7.9")
public class ReaderException extends com.etl.exception.reader.ReaderException {

    public ReaderException(String message) {
        super(message);
    }

    public ReaderException(String message, Throwable cause) {
        super(message, cause);
    }
}

