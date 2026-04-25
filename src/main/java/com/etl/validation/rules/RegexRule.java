package com.etl.validation.rules;

import org.springframework.batch.item.validator.ValidationException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * @deprecated Legacy standalone regex rule implementation. If regex validation is added to
 * the active runtime, it should be expressed through processor field rules instead.
 */
@Deprecated(since = "1.4.0")
public class RegexRule implements ValidationRule<Map<String, Object>> {
    private final String fieldName;
    private final Pattern pattern;

    public RegexRule(String fieldName, String regex) {
        this.fieldName = fieldName;
        this.pattern = Pattern.compile(regex);
    }

    @Override
    public void validate(Map<String, Object> record) throws ValidationException {
        Object value = record.get(fieldName);
        if (value == null || !pattern.matcher(value.toString()).matches()) {
            throw new ValidationException(fieldName + " does not match pattern: " + pattern);
        }
    }
}
