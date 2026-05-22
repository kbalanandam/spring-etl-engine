package com.etl.processor.validation;

import com.etl.enums.ModelFormat;
import com.etl.config.processor.ProcessorConfig;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.List;

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
    public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
                                      ProcessorConfig.FieldMapping fieldMapping,
                                      ProcessorConfig.FieldRule rule) {
        if (configuredIdentityMode(rule) != DuplicateIdentityMode.XML_NATIVE) {
            List<String> keyFields = configuredKeyFields(fieldMapping.getFrom(), rule);
            List<String> xmlPathLikeKeyFields = keyFields.stream()
                    .filter(this::isPathLikeXmlSelector)
                    .toList();
            if (!xmlPathLikeKeyFields.isEmpty()) {
            throw new IllegalStateException("FieldMapping rule 'duplicate' for entity "
                    + entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '"
                    + fieldMapping.getFrom() + "' uses XML path-like keyFields " + xmlPathLikeKeyFields
                    + " while duplicateIdentityMode is 'flatMapped'. Set duplicateIdentityMode: xmlNative for nested XML identity keys.");
            }
        } else {
            List<String> keyFields = configuredKeyFields(fieldMapping.getFrom(), rule);
            List<String> unsupportedRepeatingSelectors = keyFields.stream()
                    .filter(this::hasUnsupportedRepeatingSelectorSyntax)
                    .toList();
            if (!unsupportedRepeatingSelectors.isEmpty()) {
                throw new IllegalStateException("FieldMapping rule 'duplicate' for entity "
                        + entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '"
                        + fieldMapping.getFrom() + "' uses xmlNative keyFields " + unsupportedRepeatingSelectors
                        + " with unsupported repeating-selector syntax ([...], [*], or wildcard segments)."
                        + " Repeating-node xmlNative selector syntax is not supported by the current runtime.");
            }
        }
        super.validateConfiguration(entityMapping, fieldMapping, rule);
    }

  @Override
  protected Object resolveKeyValue(Object input, String keyField, ProcessorConfig.FieldRule rule) {
    if (input == null || keyField == null || keyField.isBlank()) {
      return null;
    }

    Object directValue = super.resolveKeyValue(input, keyField, rule);
    if (directValue != null) {
      return directValue;
    }

    if (configuredIdentityMode(rule) != DuplicateIdentityMode.XML_NATIVE) {
      return null;
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
      current = resolvePathToken(current, normalized, keyField, rule);
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  @Override
  protected String trackingKey(String fieldName, java.util.List<String> keyFields, ProcessorConfig.FieldRule rule) {
    DuplicateIdentityMode identityMode = configuredIdentityMode(rule);
    String modePrefix = identityMode == DuplicateIdentityMode.XML_NATIVE ? "xmlNative" : "xmlFlat";
    return modePrefix + "::" + super.trackingKey(fieldName, keyFields, rule);
  }

  @Override
  protected boolean validateKeyFieldsAgainstMappedFields(ProcessorConfig.FieldRule rule) {
    return configuredIdentityMode(rule) != DuplicateIdentityMode.XML_NATIVE;
  }

  @Override
  protected Set<DuplicateIdentityMode> supportedIdentityModes() {
    return Set.of(DuplicateIdentityMode.FLAT_MAPPED, DuplicateIdentityMode.XML_NATIVE);
  }

  private boolean isPathLikeXmlSelector(String keyField) {
    if (keyField == null) {
      return false;
    }
    String normalized = keyField.trim();
    return normalized.startsWith("@") || normalized.contains("/");
  }

  private boolean hasUnsupportedRepeatingSelectorSyntax(String keyField) {
    if (keyField == null) {
      return false;
    }
    String normalized = keyField.trim();
    if (normalized.isEmpty()) {
      return false;
    }
    return normalized.contains("[") || normalized.contains("]") || normalized.contains("/*/");
  }

  private Object resolvePathToken(Object current, String token, String fullKeyField, ProcessorConfig.FieldRule rule) {
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

    if (current instanceof Iterable<?> || (current != null && current.getClass().isArray())) {
      throw new IllegalStateException("FieldMapping rule 'duplicate' with duplicateIdentityMode='xmlNative' keyField '"
          + fullKeyField + "' reached a repeating-node/list segment before token '" + token
          + "'. Repeating-node xmlNative key traversal is not supported by the current runtime.");
    }

    String propertyToken = token.startsWith("@") ? token.substring(1) : token;
    return super.resolveKeyValue(current, propertyToken, rule);
  }
}


