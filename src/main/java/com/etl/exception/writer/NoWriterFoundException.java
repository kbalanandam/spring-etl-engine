package com.etl.exception.writer;

public class NoWriterFoundException extends WriterException {

    public NoWriterFoundException(String message) {
        super(message);
    }

    public NoWriterFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

