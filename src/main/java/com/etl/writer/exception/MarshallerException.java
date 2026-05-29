package com.etl.writer.exception;

/**
 * @deprecated Use {@link com.etl.exception.writer.MarshallerException}.
 */
@Deprecated(forRemoval = false, since = "1.7.9")
public class MarshallerException extends com.etl.exception.writer.MarshallerException {

    public MarshallerException(String message) {
        super(message);
    }

    public MarshallerException(String message, Throwable cause) {
        super(message, cause);
    }
}
