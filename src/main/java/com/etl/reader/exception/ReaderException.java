package com.etl.reader.exception;

import com.etl.exception.EtlException;
import com.etl.exception.EtlErrorCategory;

public class ReaderException extends EtlException {

    public ReaderException(String message) {
        super(EtlErrorCategory.FACTORY, message);
    }

    public ReaderException(String message, Throwable cause) {
        super(EtlErrorCategory.FACTORY, message, cause);
    }
}

