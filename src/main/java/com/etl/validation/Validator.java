package com.etl.validation;

import com.etl.validation.rules.ValidationRule;
import org.springframework.batch.item.validator.ValidationException;

import java.util.List;

/**
 * @deprecated This legacy validation container is not part of the active ETL runtime path.
 * Use source-side validation in {@code com.etl.config} and record validation through
 * {@code com.etl.config.processor.ProcessorConfig} plus
 * {@code com.etl.processor.validation.ValidationRuleEvaluator} instead.
 */
@Deprecated(since = "1.4.0")
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
