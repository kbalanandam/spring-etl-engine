package com.etl.validation.rules;

import org.springframework.batch.item.validator.ValidationException;

import java.util.Map;

/**
 * @deprecated Legacy standalone rule implementation. Active not-null validation is handled
 * by {@code com.etl.processor.validation.ValidationRuleEvaluator} from processor config.
 */
@Deprecated(since = "1.4.0")
public class NotNullRule implements ValidationRule<Map<String, Object>> {
    private final String fieldName;

    public NotNullRule(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public void validate(Map<String, Object> record) throws ValidationException {
        if (record.get(fieldName) == null) {
            throw new ValidationException(fieldName + " must not be null");
        }
    }
}
