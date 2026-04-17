package com.etl.model.exception;

import com.etl.exception.EtlException;

public class ModelException extends EtlException {
    public ModelException(String message) {
        super(message);
    }

	public ModelException(String message, Throwable cause) {
		super(message, cause);
	}
}
