package com.etl.validation;

import com.etl.validation.rules.ValidationRule;
import org.springframework.batch.item.validator.ValidationException;

import java.util.List;

public class Validator<T> {
    private final List<ValidationRule<T>> rules;

    public Validator(List<ValidationRule<T>> rules) {
        this.rules = rules;
    }

    public void validate(T record) throws ValidationException {
        for (ValidationRule<T> rule : rules) {
            rule.validate(record);
        }
    }
}
