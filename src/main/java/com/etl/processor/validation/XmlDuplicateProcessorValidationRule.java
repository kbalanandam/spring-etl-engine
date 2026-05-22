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
                    .filter(keyField -> keyField != null && (keyField.contains("/") || keyField.contains("@")))
                    .toList();
            if (!xmlPathLikeKeyFields.isEmpty()) {
            throw new IllegalStateException("FieldMapping rule 'duplicate' for entity "
                    + entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '"
                    + fieldMapping.getFrom() + "' uses XML path-like keyFields " + xmlPathLikeKeyFields
                    + " while duplicateIdentityMode is 'flatMapped'. Set duplicateIdentityMode: xmlNative for nested XML identity keys.");
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
      current = resolvePathToken(current, normalized, rule);
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

  private Object resolvePathToken(Object current, String token, ProcessorConfig.FieldRule rule) {
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
    return super.resolveKeyValue(current, propertyToken, rule);
  }
}


