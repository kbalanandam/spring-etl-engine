package com.etl.writer.exception;

import com.etl.exception.EtlException;

public class WriterException extends EtlException {

    public WriterException(String message) {
        super(message);
    }

    public WriterException(String message, Throwable cause) {
        super(message, cause);
    }
}
