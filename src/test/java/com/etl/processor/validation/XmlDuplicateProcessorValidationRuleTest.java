package com.etl.processor.validation;

import com.etl.enums.ModelFormat;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.util.List;
import java.util.Map;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XmlDuplicateProcessorValidationRuleTest {

    @Test
    void scopesDuplicateRuleRegistrationToXmlSourceFormat() {
        XmlDuplicateProcessorValidationRule rule = new XmlDuplicateProcessorValidationRule(new FileIngestionRuntimeSupport());

        assertEquals(Set.of(ModelFormat.XML), rule.supportedSourceFormats());
    }

  @Test
  void resolvesNestedXmlPathKeysWhenEvaluatingDuplicates() {
    FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
    XmlDuplicateProcessorValidationRule rule = new XmlDuplicateProcessorValidationRule(runtimeSupport);
    ProcessorConfig.FieldRule duplicate = new ProcessorConfig.FieldRule();
    duplicate.setType("duplicate");
    duplicate.setDuplicateIdentityMode("xmlNative");
    duplicate.setKeyFields(List.of("/event/customer/id", "/event/tag/@code"));

    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    CsvSourceConfig sourceConfig = sourceConfig();
    runtimeSupport.initializeStep(stepExecution, sourceConfig, new ProcessorConfig(), xmlEntityMapping());
    StepSynchronizationManager.register(stepExecution);
    try {
      ValidationIssue first = rule.evaluate(xmlRecord("100", "A"), "id", null, duplicate);
      ValidationIssue second = rule.evaluate(xmlRecord("100", "A"), "id", null, duplicate);

      assertNull(first);
      assertNotNull(second);
      assertEquals("duplicate", second.rule());
    } finally {
      StepSynchronizationManager.close();
      runtimeSupport.completeStep(stepExecution, sourceConfig);
    }
  }

  @Test
  void rejectsPathLikeKeyFieldsWhenXmlIdentityModeIsFlatMapped() {
    XmlDuplicateProcessorValidationRule rule = new XmlDuplicateProcessorValidationRule(new FileIngestionRuntimeSupport());
    ProcessorConfig.EntityMapping mapping = xmlEntityMapping();
    ProcessorConfig.FieldMapping idField = mapping.getFields().get(0);
    ProcessorConfig.FieldRule duplicate = new ProcessorConfig.FieldRule();
    duplicate.setType("duplicate");
    duplicate.setKeyFields(List.of("/event/customer/id"));
    idField.setRules(List.of(duplicate));

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> rule.validateConfiguration(mapping, idField, duplicate)
    );

    assertTrue(exception.getMessage().contains("duplicateIdentityMode"));
    assertTrue(exception.getMessage().contains("xmlNative"));
  }

  @Test
  void allowsLiteralAtSymbolKeyFieldWhenXmlIdentityModeIsFlatMapped() {
    XmlDuplicateProcessorValidationRule rule = new XmlDuplicateProcessorValidationRule(new FileIngestionRuntimeSupport());
    ProcessorConfig.EntityMapping mapping = xmlEntityMapping("tag@code");
    ProcessorConfig.FieldMapping idField = mapping.getFields().get(0);
    ProcessorConfig.FieldRule duplicate = new ProcessorConfig.FieldRule();
    duplicate.setType("duplicate");
    duplicate.setKeyFields(List.of("tag@code"));
    idField.setRules(List.of(duplicate));

    assertDoesNotThrow(() -> rule.validateConfiguration(mapping, idField, duplicate));
  }

  @Test
  void rejectsAttributeSelectorKeyFieldWhenXmlIdentityModeIsFlatMapped() {
    XmlDuplicateProcessorValidationRule rule = new XmlDuplicateProcessorValidationRule(new FileIngestionRuntimeSupport());
    ProcessorConfig.EntityMapping mapping = xmlEntityMapping();
    ProcessorConfig.FieldMapping idField = mapping.getFields().get(0);
    ProcessorConfig.FieldRule duplicate = new ProcessorConfig.FieldRule();
    duplicate.setType("duplicate");
    duplicate.setKeyFields(List.of("@code"));
    idField.setRules(List.of(duplicate));

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> rule.validateConfiguration(mapping, idField, duplicate)
    );

    assertTrue(exception.getMessage().contains("duplicateIdentityMode"));
    assertTrue(exception.getMessage().contains("xmlNative"));
    assertTrue(exception.getMessage().contains("@code"));
  }

  @Test
  void xmlNativeListPathKeysFailFastWithControlledError() {
    FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
    XmlDuplicateProcessorValidationRule rule = new XmlDuplicateProcessorValidationRule(runtimeSupport);
    ProcessorConfig.FieldRule duplicate = new ProcessorConfig.FieldRule();
    duplicate.setType("duplicate");
    duplicate.setDuplicateIdentityMode("xmlNative");
    duplicate.setKeyFields(List.of("/event/tags/@code"));

    StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
    CsvSourceConfig sourceConfig = sourceConfig();
    runtimeSupport.initializeStep(stepExecution, sourceConfig, new ProcessorConfig(), xmlEntityMapping());
    StepSynchronizationManager.register(stepExecution);
    try {
      IllegalStateException exception = assertThrows(
          IllegalStateException.class,
          () -> rule.evaluate(xmlRecordWithTagList(List.of("A", "B")), "id", null, duplicate)
      );

      assertTrue(exception.getMessage().contains("duplicateIdentityMode='xmlNative'"));
      assertTrue(exception.getMessage().contains("repeating-node/list segment"));
    } finally {
      StepSynchronizationManager.close();
      runtimeSupport.completeStep(stepExecution, sourceConfig);
    }
  }

  private CsvSourceConfig sourceConfig() {
    CsvSourceConfig sourceConfig = new CsvSourceConfig();
    sourceConfig.setSourceName("XmlEvents");
    return sourceConfig;
  }

  private Map<String, Object> xmlRecord(String customerId, String tagCode) {
    return Map.of(
        "event", Map.of(
            "customer", Map.of("id", customerId),
            "tag", Map.of("@code", tagCode)
        )
    );
  }

  private Map<String, Object> xmlRecordWithTagList(List<String> tagCodes) {
    List<Map<String, Object>> tags = tagCodes.stream()
        .map(tagCode -> Map.<String, Object>of("@code", tagCode, "value", "VIP"))
        .toList();
    return Map.of(
        "event", Map.of(
            "customer", Map.of("id", "100"),
            "tags", tags
        )
    );
  }

  private ProcessorConfig.EntityMapping xmlEntityMapping() {
    return xmlEntityMapping("id");
  }

  private ProcessorConfig.EntityMapping xmlEntityMapping(String fromField) {
    ProcessorConfig.FieldMapping id = new ProcessorConfig.FieldMapping();
    id.setFrom(fromField);
    id.setTo(fromField);

    ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
    mapping.setSource("XmlEvents");
    mapping.setTarget("XmlEventsOut");
    mapping.setFields(List.of(id));
    return mapping;
  }
}



