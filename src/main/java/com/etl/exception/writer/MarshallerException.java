package com.etl.exception.writer;

public class MarshallerException extends WriterException {

    public MarshallerException(String message) {
        super(message);
    }

    public MarshallerException(String message, Throwable cause) {
        super(message, cause);
    }
}

