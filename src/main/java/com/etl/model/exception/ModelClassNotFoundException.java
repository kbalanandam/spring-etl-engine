package com.etl.model.exception;

public class ModelClassNotFoundException extends ModelException {
    public ModelClassNotFoundException(String message) {
        super(message);
    }

	public ModelClassNotFoundException(String message, Throwable cause) {
		super(message, cause);
	}
}
