package com.etl.writer.exception;

import com.etl.exception.EtlException;
import com.etl.exception.EtlErrorCategory;

public class WriterException extends EtlException {

    public WriterException(String message) {
		super(EtlErrorCategory.FACTORY, message);
    }

    public WriterException(String message, Throwable cause) {
		super(EtlErrorCategory.FACTORY, message, cause);
    }
}
