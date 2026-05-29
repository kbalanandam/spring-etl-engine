package com.etl.exception.reader;

public class NoReaderFoundException extends ReaderException {

    public NoReaderFoundException(String message) {
        super(message);
    }

    public NoReaderFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
