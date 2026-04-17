package com.etl.model.exception;

public class UnsupportedModelFormatException extends ModelException {
    public UnsupportedModelFormatException(String message) {
        super(message);
    }

	public UnsupportedModelFormatException(String message, Throwable cause) {
		super(message, cause);
	}
}
