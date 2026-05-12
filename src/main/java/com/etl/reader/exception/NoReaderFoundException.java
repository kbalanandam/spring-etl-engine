package com.etl.reader.exception;

public class NoReaderFoundException extends ReaderException {

    public NoReaderFoundException(String message) {
        super(message);
    }

    public NoReaderFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

