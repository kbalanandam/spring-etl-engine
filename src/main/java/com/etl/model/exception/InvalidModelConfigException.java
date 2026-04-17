package com.etl.model.exception;

public class InvalidModelConfigException extends ModelException {
    public InvalidModelConfigException(String message) {
        super(message);
    }

	public InvalidModelConfigException(String message, Throwable cause) {
		super(message, cause);
	}
}
