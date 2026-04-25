package com.etl.validation.rules;

import org.springframework.batch.item.validator.ValidationException;

/**
 * @deprecated Legacy validation SPI. Active record validation now runs through
 * {@code com.etl.processor.validation.ValidationRuleEvaluator} and source validation
 * should be modeled alongside active source config/runtime flows.
 */
@Deprecated(since = "1.4.0")
public interface ValidationRule<T> {
    void validate(T record) throws ValidationException;
}
