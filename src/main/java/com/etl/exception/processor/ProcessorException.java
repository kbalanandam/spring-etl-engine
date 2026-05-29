package com.etl.exception.processor;

import com.etl.exception.EtlErrorCategory;
import com.etl.exception.EtlException;

public class ProcessorException extends EtlException {

    public ProcessorException(String message) {
        super(EtlErrorCategory.TRANSFORMATION, message);
    }

    public ProcessorException(String message, Throwable cause) {
        super(EtlErrorCategory.TRANSFORMATION, message, cause);
    }
}

