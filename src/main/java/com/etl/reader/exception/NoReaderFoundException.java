package com.etl.reader.exception;

/**
 * @deprecated Use {@link com.etl.exception.reader.NoReaderFoundException}.
 */
@Deprecated(forRemoval = false, since = "1.7.9")
public class NoReaderFoundException extends com.etl.exception.reader.NoReaderFoundException {

    public NoReaderFoundException(String message) {
        super(message);
    }

    public NoReaderFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

