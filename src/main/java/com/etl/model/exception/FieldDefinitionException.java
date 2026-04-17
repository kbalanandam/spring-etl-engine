package com.etl.model.exception;

public class FieldDefinitionException extends ModelException {
    public FieldDefinitionException(String message) {
        super(message);
    }

	public FieldDefinitionException(String message, Throwable cause) {
		super(message, cause);
	}
}
