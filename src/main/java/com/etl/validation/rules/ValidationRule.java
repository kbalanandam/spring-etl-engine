package com.etl.validation.rules;

import org.springframework.batch.item.validator.ValidationException;

public interface ValidationRule<T> {
    void validate(T record) throws ValidationException;
}
