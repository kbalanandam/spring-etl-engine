package com.etl.mapping;

import com.etl.config.ColumnConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.processor.validation.DuplicateProcessorValidationRule;
import com.etl.processor.validation.ValidationRuleEvaluator;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.test.MetaDataInstanceFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationAwareDynamicMappingTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsLaterDuplicateValuesAndWritesRejectArtifact() throws Exception {
        Path sourceFile = tempDir.resolve("events.csv");
        Files.writeString(sourceFile, "id,description\nEVT-1001,first\nEVT-1001,duplicate\n");

        ProcessorConfig.FieldRule duplicate = new ProcessorConfig.FieldRule();
        duplicate.setType("duplicate");

        ProcessorConfig.FieldMapping id = new ProcessorConfig.FieldMapping();
        id.setFrom("id");
        id.setTo("id");
        id.setRules(List.of(duplicate));

        ProcessorConfig.FieldMapping description = new ProcessorConfig.FieldMapping();
        description.setFrom("description");
        description.setTo("description");

        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Events");
        mapping.setTarget("EventsCsv");
        mapping.setFields(List.of(id, description));

        ProcessorConfig processorConfig = new ProcessorConfig();
        ProcessorConfig.RejectHandling rejectHandling = new ProcessorConfig.RejectHandling();
        rejectHandling.setEnabled(true);
            rejectHandling.setOutputPath(tempDir.resolve("rejects") + "\\");
        rejectHandling.setIncludeReasonColumns(true);
        processorConfig.setRejectHandling(rejectHandling);
        processorConfig.setMappings(List.of(mapping));

        CsvSourceConfig sourceConfig = new CsvSourceConfig(
                "Events",
                "com.etl.model.source",
                List.of(column("id"), column("description")),
                sourceFile.toString(),
                ",",
                null
        );

        FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
        ValidationRuleEvaluator evaluator = new ValidationRuleEvaluator(List.of(
                new DuplicateProcessorValidationRule(runtimeSupport)
        ));
        ValidationAwareDynamicMapping<EventRecord, TargetRecord> processor = new ValidationAwareDynamicMapping<>(
                mapping,
                TargetRecord.class,
                evaluator,
                runtimeSupport,
                true
        );

        StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
        runtimeSupport.initializeStep(stepExecution, sourceConfig, processorConfig, mapping);

        StepSynchronizationManager.register(stepExecution);
        try {
            TargetRecord accepted = processor.process(new EventRecord("EVT-1001", "first"));
            assertNotNull(accepted);
            assertEquals("EVT-1001", accepted.getId());
            assertEquals("first", accepted.getDescription());

            TargetRecord duplicateRecord = processor.process(new EventRecord("EVT-1001", "duplicate"));
            assertNull(duplicateRecord);
        } finally {
            StepSynchronizationManager.close();
        }

        stepExecution.setExitStatus(ExitStatus.COMPLETED);
        runtimeSupport.completeStep(stepExecution, sourceConfig);

        Path rejectFile = Path.of(stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.REJECT_OUTPUT_PATH_KEY));
        assertTrue(Files.exists(rejectFile));
        List<String> rejectLines = Files.readAllLines(rejectFile);
        assertEquals("id,description,_rejectField,_rejectRule,_rejectMessage", rejectLines.get(0));
        assertTrue(rejectLines.get(1).contains("duplicate"));
        assertEquals(1, stepExecution.getExecutionContext().getInt(FileIngestionRuntimeSupport.REJECTED_COUNT_KEY));
    }

  @Test
  void failsFastWhenConfiguredImportantFieldIsMissing() {
    ProcessorConfig.FieldRule notNull = new ProcessorConfig.FieldRule();
    notNull.setType("notNull");
    notNull.setOnFailure("failStep");

    ProcessorConfig.FieldMapping id = new ProcessorConfig.FieldMapping();
    id.setFrom("id");
    id.setTo("id");
    id.setRules(List.of(notNull));

    ProcessorConfig.FieldMapping description = new ProcessorConfig.FieldMapping();
    description.setFrom("description");
    description.setTo("description");

    ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
    mapping.setSource("Events");
    mapping.setTarget("EventsCsv");
    mapping.setFields(List.of(id, description));

    ValidationRuleEvaluator evaluator = new ValidationRuleEvaluator(List.of(
        new com.etl.processor.validation.NotNullProcessorValidationRule()
    ));
    ValidationAwareDynamicMapping<EventRecord, TargetRecord> processor = new ValidationAwareDynamicMapping<>(
        mapping,
        TargetRecord.class,
        evaluator,
        new FileIngestionRuntimeSupport(),
        true
    );

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> processor.process(new EventRecord(" ", "missing id"))
    );
    assertTrue(exception.getMessage().contains("Processor validation failed"));
    assertTrue(exception.getMessage().contains("id[notNull]"));
    assertTrue(exception.getMessage().contains("Events -> EventsCsv"));
  }

  @Test
  void appliesTransformsBeforeValidationAndWritesTransformedValue() throws Exception {
    ProcessorConfig.FieldTransform statusTransform = new ProcessorConfig.FieldTransform();
    statusTransform.setType("valueMap");
    statusTransform.setMappings(new LinkedHashMap<>(Map.of("1", "Success", "2", "Fail")));
    statusTransform.setDefaultValue("Unknown");

    ProcessorConfig.FieldRule startsWith = new ProcessorConfig.FieldRule();
    startsWith.setType("startsWith");
    startsWith.setPattern("Suc");

    ProcessorConfig.FieldMapping status = new ProcessorConfig.FieldMapping();
    status.setFrom("statusCode");
    status.setTo("status");
    status.setTransforms(List.of(statusTransform));
    status.setRules(List.of(startsWith));

    ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
    mapping.setSource("Events");
    mapping.setTarget("EventsCsv");
    mapping.setFields(List.of(status));

    ValidationRuleEvaluator evaluator = new ValidationRuleEvaluator(List.of(new StartsWithProcessorValidationRule()));
    ValidationAwareDynamicMapping<StatusRecord, StatusTargetRecord> processor = new ValidationAwareDynamicMapping<>(
        mapping,
        StatusTargetRecord.class,
        new com.etl.processor.transform.TransformEvaluator(),
        evaluator,
        new FileIngestionRuntimeSupport(),
        true
    );

    StatusTargetRecord accepted = processor.process(new StatusRecord("1"));

    assertNotNull(accepted);
    assertEquals("Success", accepted.getStatus());
  }

  @Test
  void validatesAndWritesDerivedExpressionFieldWithoutSourceProperty() throws Exception {
    ProcessorConfig.FieldTransform fullNameTransform = new ProcessorConfig.FieldTransform();
    fullNameTransform.setType("expression");
    fullNameTransform.setExpression("#input.firstName + ' ' + #input.lastName");

    ProcessorConfig.FieldRule startsWith = new ProcessorConfig.FieldRule();
    startsWith.setType("startsWith");
    startsWith.setPattern("Ada");

    ProcessorConfig.FieldMapping fullName = new ProcessorConfig.FieldMapping();
    fullName.setTo("fullName");
    fullName.setTransforms(List.of(fullNameTransform));
    fullName.setRules(List.of(startsWith));

    ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
    mapping.setSource("Customers");
    mapping.setTarget("CustomersOut");
    mapping.setFields(List.of(fullName));

    ValidationRuleEvaluator evaluator = new ValidationRuleEvaluator(List.of(new StartsWithProcessorValidationRule()));
    ValidationAwareDynamicMapping<NameRecord, NameTargetRecord> processor = new ValidationAwareDynamicMapping<>(
        mapping,
        NameTargetRecord.class,
        new com.etl.processor.transform.TransformEvaluator(),
        evaluator,
        new FileIngestionRuntimeSupport(),
        true
    );

    NameTargetRecord accepted = processor.process(new NameRecord("Ada", "Lovelace"));

    assertNotNull(accepted);
    assertEquals("Ada Lovelace", accepted.getFullName());
  }

      @Test
      void rejectsLaterDuplicateCompositeKeysAndKeepsDifferentKeyCombinations() throws Exception {
          Path sourceFile = tempDir.resolve("events-composite.csv");
          Files.writeString(sourceFile, "id,eventTime,description\nEVT-1001,08:30:00,first\nEVT-1001,09:45:00,keep\nEVT-1001,08:30:00,duplicate\n");

          ProcessorConfig.FieldRule duplicate = new ProcessorConfig.FieldRule();
          duplicate.setType("duplicate");
          duplicate.setKeyFields(List.of("id", "eventTime"));

          ProcessorConfig.FieldMapping id = new ProcessorConfig.FieldMapping();
          id.setFrom("id");
          id.setTo("id");
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

          ProcessorConfig processorConfig = new ProcessorConfig();
          ProcessorConfig.RejectHandling rejectHandling = new ProcessorConfig.RejectHandling();
          rejectHandling.setEnabled(true);
          rejectHandling.setOutputPath(tempDir.resolve("rejects-composite") + "\\");
          rejectHandling.setIncludeReasonColumns(true);
          processorConfig.setRejectHandling(rejectHandling);
          processorConfig.setMappings(List.of(mapping));

          CsvSourceConfig sourceConfig = new CsvSourceConfig(
                  "Events",
                  "com.etl.model.source",
                  List.of(column("id"), column("eventTime"), column("description")),
                  sourceFile.toString(),
                  ",",
                  null
          );

          FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
          ValidationRuleEvaluator evaluator = new ValidationRuleEvaluator(List.of(
                  new DuplicateProcessorValidationRule(runtimeSupport)
          ));
          ValidationAwareDynamicMapping<CompositeEventRecord, CompositeTargetRecord> processor = new ValidationAwareDynamicMapping<>(
                  mapping,
                  CompositeTargetRecord.class,
                  evaluator,
                  runtimeSupport,
                  true
          );

          StepExecution stepExecution = MetaDataInstanceFactory.createStepExecution();
          runtimeSupport.initializeStep(stepExecution, sourceConfig, processorConfig, mapping);

          StepSynchronizationManager.register(stepExecution);
          try {
              CompositeTargetRecord firstAccepted = processor.process(new CompositeEventRecord("EVT-1001", "08:30:00", "first"));
              assertNotNull(firstAccepted);
              assertEquals("08:30:00", firstAccepted.getEventTime());

              CompositeTargetRecord secondAccepted = processor.process(new CompositeEventRecord("EVT-1001", "09:45:00", "keep"));
              assertNotNull(secondAccepted);

              CompositeTargetRecord duplicateRecord = processor.process(new CompositeEventRecord("EVT-1001", "08:30:00", "duplicate"));
              assertNull(duplicateRecord);
          } finally {
              StepSynchronizationManager.close();
          }

          stepExecution.setExitStatus(ExitStatus.COMPLETED);
          runtimeSupport.completeStep(stepExecution, sourceConfig);

          Path rejectFile = Path.of(stepExecution.getExecutionContext().getString(FileIngestionRuntimeSupport.REJECT_OUTPUT_PATH_KEY));
          assertTrue(Files.exists(rejectFile));
          List<String> rejectLines = Files.readAllLines(rejectFile);
          assertEquals("id,eventTime,description,_rejectField,_rejectRule,_rejectMessage", rejectLines.get(0));
          assertTrue(rejectLines.get(1).contains("duplicate composite key"));
          assertEquals(1, stepExecution.getExecutionContext().getInt(FileIngestionRuntimeSupport.REJECTED_COUNT_KEY));
      }

    private ColumnConfig column(String name) {
        ColumnConfig column = new ColumnConfig();
        column.setName(name);
        column.setType("String");
        return column;
    }

      private record EventRecord(String id, String description) {
      }

      private record CompositeEventRecord(String id, String eventTime, String description) {
      }

	private record StatusRecord(String statusCode) {
	}

  private record NameRecord(String firstName, String lastName) {
  }

    public static final class TargetRecord {
        public String id;
        public String description;

        public String getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }
    }

      public static final class CompositeTargetRecord {
          public String id;
          public String eventTime;
          public String description;

          public String getEventTime() {
              return eventTime;
          }
      }

  public static final class StatusTargetRecord {
    public String status;

    public String getStatus() {
      return status;
    }
  }

  public static final class NameTargetRecord {
    public String fullName;

    public String getFullName() {
      return fullName;
    }
  }

  private static final class StartsWithProcessorValidationRule implements com.etl.processor.validation.ProcessorValidationRule {

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
    public com.etl.processor.validation.ValidationIssue evaluate(String fieldName, Object value, ProcessorConfig.FieldRule rule) {
      if (value == null || value.toString().startsWith(rule.getPattern())) {
        return null;
      }
      return new com.etl.processor.validation.ValidationIssue(fieldName, getRuleType(), fieldName + " must start with " + rule.getPattern());
    }
  }
}


