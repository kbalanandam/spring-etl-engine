package com.etl.exception.writer;

import com.etl.exception.EtlErrorCategory;
import com.etl.exception.EtlException;

public class WriterException extends EtlException {

    public WriterException(String message) {
        super(EtlErrorCategory.TARGET_WRITE, message);
    }

    public WriterException(String message, Throwable cause) {
        super(EtlErrorCategory.TARGET_WRITE, message, cause);
    }
}

