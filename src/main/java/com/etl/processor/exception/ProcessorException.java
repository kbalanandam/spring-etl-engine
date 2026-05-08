package com.etl.processor.exception;

import com.etl.exception.EtlException;
import com.etl.exception.EtlErrorCategory;

public class ProcessorException extends EtlException {
    public ProcessorException(String message) {
		super(EtlErrorCategory.FACTORY, message);
    }

    public ProcessorException(String message, Throwable cause) {
		super(EtlErrorCategory.FACTORY, message, cause);
    }
}
