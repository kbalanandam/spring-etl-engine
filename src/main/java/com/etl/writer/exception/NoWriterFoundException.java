package com.etl.writer.exception;

public class NoWriterFoundException extends WriterException {

    public NoWriterFoundException(String message) {
        super(message);
    }

    public NoWriterFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
