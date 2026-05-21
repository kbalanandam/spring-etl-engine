package com.etl.processor.validation;

import com.etl.enums.ModelFormat;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * XML-scoped duplicate rule registration.
 *
 * <p>This delegates to the existing duplicate rule behavior while reserving a dedicated
 * XML-only dispatch slot for future XML-native semantics.</p>
 */
@Component
public class XmlDuplicateProcessorValidationRule extends DuplicateProcessorValidationRule {

    @Autowired
    public XmlDuplicateProcessorValidationRule(FileIngestionRuntimeSupport fileIngestionRuntimeSupport) {
        super(fileIngestionRuntimeSupport);
    }

    @Override
    public Set<ModelFormat> supportedSourceFormats() {
        return Set.of(ModelFormat.XML);
    }

  @Override
  protected Object resolveKeyValue(Object input, String keyField) {
    if (input == null || keyField == null || keyField.isBlank()) {
      return null;
    }

    Object directValue = super.resolveKeyValue(input, keyField);
    if (directValue != null) {
      return directValue;
    }

    if (!keyField.contains("/")) {
      return null;
    }

    String[] tokens = keyField.split("/");
    Object current = input;
    for (String token : tokens) {
      String normalized = token == null ? "" : token.trim();
      if (normalized.isEmpty()) {
        continue;
      }
      current = resolvePathToken(current, normalized);
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  @Override
  protected String trackingKey(String fieldName, java.util.List<String> keyFields) {
    return "xml::" + super.trackingKey(fieldName, keyFields);
  }

  private Object resolvePathToken(Object current, String token) {
    if (current instanceof Map<?, ?> map) {
      if (map.containsKey(token)) {
        return map.get(token);
      }
      String withoutAttributePrefix = token.startsWith("@") ? token.substring(1) : token;
      if (map.containsKey(withoutAttributePrefix)) {
        return map.get(withoutAttributePrefix);
      }
      String withAttributePrefix = token.startsWith("@") ? token : "@" + token;
      if (map.containsKey(withAttributePrefix)) {
        return map.get(withAttributePrefix);
      }
      return null;
    }

    String propertyToken = token.startsWith("@") ? token.substring(1) : token;
    return super.resolveKeyValue(current, propertyToken);
  }
}


