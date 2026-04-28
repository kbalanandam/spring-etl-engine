package com.etl.processor.validation;

import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationRuleEvaluatorTest {

    private final ValidationRuleEvaluator evaluator = new ValidationRuleEvaluator();

    @Test
    void returnsNoIssuesForValidRecord() {
        List<ValidationIssue> issues = evaluator.evaluate(new EventRecord("EVT-1001", "08:30:00", "ok"), mapping());

        assertTrue(issues.isEmpty());
    }

    @Test
    void returnsIssuesForBlankRequiredFieldAndInvalidTimeFormat() {
        List<ValidationIssue> issues = evaluator.evaluate(new EventRecord(" ", "25:99:00", "bad"), mapping());

        assertEquals(2, issues.size());
        assertEquals("id", issues.get(0).field());
        assertEquals("notNull", issues.get(0).rule());
        assertEquals("eventTime", issues.get(1).field());
        assertEquals("timeFormat", issues.get(1).rule());
    }

          @Test
          void returnsIssueForDuplicateFieldValueWithinCurrentStep() {
            FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
            ValidationRuleEvaluator duplicateEvaluator = new ValidationRuleEvaluator(List.of(
                new DuplicateProcessorValidationRule(runtimeSupport)
            ));
            ProcessorConfig.EntityMapping duplicateMapping = duplicateOnlyMapping();
            StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();

            runtimeSupport.initializeStep(stepExecution, sourceConfig(), new ProcessorConfig(), duplicateMapping);
            StepSynchronizationManager.register(stepExecution);
            try {
              assertTrue(duplicateEvaluator.evaluate(new EventRecord("EVT-1001", "08:30:00", "first"), duplicateMapping).isEmpty());

              List<ValidationIssue> issues = duplicateEvaluator.evaluate(new EventRecord("EVT-1001", "09:45:00", "duplicate"), duplicateMapping);
              assertEquals(1, issues.size());
              assertEquals("id", issues.get(0).field());
              assertEquals("duplicate", issues.get(0).rule());
            } finally {
              StepSynchronizationManager.close();
              runtimeSupport.completeStep(stepExecution, sourceConfig());
            }
          }

        @Test
        void returnsIssueForDuplicateCompositeKeyWithinCurrentStep() {
          FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
          ValidationRuleEvaluator duplicateEvaluator = new ValidationRuleEvaluator(List.of(
              new DuplicateProcessorValidationRule(runtimeSupport)
          ));
          ProcessorConfig.EntityMapping duplicateMapping = duplicateMapping(List.of("id", "eventTime"));
          StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();

          runtimeSupport.initializeStep(stepExecution, sourceConfig(), new ProcessorConfig(), duplicateMapping);
          StepSynchronizationManager.register(stepExecution);
          try {
            assertTrue(duplicateEvaluator.evaluate(new EventRecord("EVT-1001", "08:30:00", "first"), duplicateMapping).isEmpty());
            assertTrue(duplicateEvaluator.evaluate(new EventRecord("EVT-1001", "09:45:00", "different-time"), duplicateMapping).isEmpty());

            List<ValidationIssue> issues = duplicateEvaluator.evaluate(new EventRecord("EVT-1001", "08:30:00", "duplicate"), duplicateMapping);
            assertEquals(1, issues.size());
            assertEquals("id", issues.get(0).field());
            assertEquals("duplicate", issues.get(0).rule());
            assertTrue(issues.get(0).message().contains("[id, eventTime]"));
          } finally {
            StepSynchronizationManager.close();
            runtimeSupport.completeStep(stepExecution, sourceConfig());
          }
        }

        @Test
            void doesNotApplyDuplicateWinnerSelectionDuringRowLevelEvaluation() {
          FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
          ValidationRuleEvaluator duplicateEvaluator = new ValidationRuleEvaluator(List.of(
              new DuplicateProcessorValidationRule(runtimeSupport)
          ));
              ProcessorConfig.EntityMapping duplicateMapping = duplicateMapping(List.of("id"), List.of(orderBy("eventTime", "DESC")));
          StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();

          runtimeSupport.initializeStep(stepExecution, sourceConfig(), new ProcessorConfig(), duplicateMapping);
          StepSynchronizationManager.register(stepExecution);
          try {
            assertTrue(duplicateEvaluator.evaluate(new EventRecord("EVT-1001", "08:30:00", "first"), duplicateMapping).isEmpty());
            assertTrue(duplicateEvaluator.evaluate(new EventRecord("EVT-1001", "09:45:00", "latest"), duplicateMapping).isEmpty());
          } finally {
            StepSynchronizationManager.close();
            runtimeSupport.completeStep(stepExecution, sourceConfig());
          }
        }

  @Test
  void supportsCustomProcessorRuleExtensions() {
    ValidationRuleEvaluator customEvaluator = new ValidationRuleEvaluator(List.of(
        new NotNullProcessorValidationRule(),
        new TimeFormatProcessorValidationRule(),
        new StartsWithProcessorValidationRule()
    ));

    ProcessorConfig.EntityMapping mapping = mapping();
    ProcessorConfig.FieldRule startsWith = new ProcessorConfig.FieldRule();
    startsWith.setType("startsWith");
    startsWith.setPattern("EVT-");
    mapping.getFields().get(0).setRules(List.of(startsWith));

    customEvaluator.validateConfiguration(mapping, mapping.getFields().get(0), startsWith);

    assertTrue(customEvaluator.evaluate(new EventRecord("EVT-1001", "08:30:00", "ok"), mapping).isEmpty());
    List<ValidationIssue> issues = customEvaluator.evaluate(new EventRecord("BAD-1001", "08:30:00", "bad"), mapping);
    assertEquals(1, issues.size());
    assertEquals("startsWith", issues.get(0).rule());
  }

    private ProcessorConfig.EntityMapping mapping() {
        ProcessorConfig.FieldMapping id = new ProcessorConfig.FieldMapping();
        id.setFrom("id");
        id.setTo("id");
        ProcessorConfig.FieldRule notNull = new ProcessorConfig.FieldRule();
        notNull.setType("notNull");
        id.setRules(List.of(notNull));

        ProcessorConfig.FieldMapping eventTime = new ProcessorConfig.FieldMapping();
        eventTime.setFrom("eventTime");
        eventTime.setTo("eventTime");
        ProcessorConfig.FieldRule timeFormat = new ProcessorConfig.FieldRule();
        timeFormat.setType("timeFormat");
        timeFormat.setPattern("HH:mm:ss");
        eventTime.setRules(List.of(timeFormat));

        ProcessorConfig.FieldMapping description = new ProcessorConfig.FieldMapping();
        description.setFrom("description");
        description.setTo("description");

        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Events");
        mapping.setTarget("EventsCsv");
        mapping.setFields(List.of(id, eventTime, description));
        return mapping;
    }

              private ProcessorConfig.EntityMapping duplicateOnlyMapping() {
                return duplicateMapping(null, null);
            }

            private ProcessorConfig.EntityMapping duplicateMapping(List<String> keyFields) {
                return duplicateMapping(keyFields, null);
            }

              private ProcessorConfig.EntityMapping duplicateMapping(List<String> keyFields,
                                                                   List<ProcessorConfig.OrderByField> orderBy) {
            ProcessorConfig.FieldMapping id = new ProcessorConfig.FieldMapping();
            id.setFrom("id");
            id.setTo("id");
            ProcessorConfig.FieldRule duplicate = new ProcessorConfig.FieldRule();
            duplicate.setType("duplicate");
              duplicate.setKeyFields(keyFields);
              duplicate.setOrderBy(orderBy);
            id.setRules(List.of(duplicate));

              ProcessorConfig.FieldMapping eventTime = new ProcessorConfig.FieldMapping();
              eventTime.setFrom("eventTime");
              eventTime.setTo("eventTime");

            ProcessorConfig.FieldMapping description = new ProcessorConfig.FieldMapping();
            description.setFrom("description");
            description.setTo("description");

            ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
            mapping.setSource("Events");
            mapping.setTarget("EventsCsv");
              mapping.setFields(List.of(id, eventTime, description));
            return mapping;
          }

            private ProcessorConfig.OrderByField orderBy(String field, String direction) {
              ProcessorConfig.OrderByField orderByField = new ProcessorConfig.OrderByField();
              orderByField.setField(field);
              orderByField.setDirection(direction);
              return orderByField;
            }

          private CsvSourceConfig sourceConfig() {
            CsvSourceConfig sourceConfig = new CsvSourceConfig();
            sourceConfig.setSourceName("Events");
            return sourceConfig;
          }

      private record EventRecord(String id, String eventTime, String description) {
      }

  private static final class StartsWithProcessorValidationRule implements ProcessorValidationRule {

    @Override
    public String getRuleType() {
      return "startsWith";
    }

    @Override
    public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
                                     ProcessorConfig.FieldMapping fieldMapping,
                                     ProcessorConfig.FieldRule rule) {
      if (rule.getPattern() == null || rule.getPattern().isBlank()) {
        throw new IllegalStateException("startsWith requires pattern");
      }
    }

    @Override
    public ValidationIssue evaluate(String fieldName, Object value, ProcessorConfig.FieldRule rule) {
      if (value == null || value.toString().startsWith(rule.getPattern())) {
        return null;
      }
      return new ValidationIssue(fieldName, getRuleType(), fieldName + " must start with " + rule.getPattern());
    }
  }
}

