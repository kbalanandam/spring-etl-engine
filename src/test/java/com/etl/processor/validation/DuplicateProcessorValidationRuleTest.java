package com.etl.processor.validation;

import com.etl.config.processor.ProcessorConfig;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateProcessorValidationRuleTest {

    private final DuplicateProcessorValidationRule rule =
            new DuplicateProcessorValidationRule(new FileIngestionRuntimeSupport());

    @Test
    void configuredKeyFieldsDefaultsToAnchorFieldWhenNotProvided() {
        assertEquals(List.of("id"), DuplicateProcessorValidationRule.configuredKeyFields("id", null));

        ProcessorConfig.FieldRule duplicateRule = duplicateRule();
        assertEquals(List.of("id"), DuplicateProcessorValidationRule.configuredKeyFields("id", duplicateRule));
    }

    @Test
    void configuredKeyFieldsTrimsValuesAndRejectsDuplicates() {
        ProcessorConfig.FieldRule duplicateRule = duplicateRule();
        duplicateRule.setKeyFields(List.of(" id ", "eventTime", "id"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DuplicateProcessorValidationRule.configuredKeyFields("id", duplicateRule)
        );

        assertTrue(exception.getMessage().contains("keyFields"));
        assertTrue(exception.getMessage().contains("unique"));
    }

    @Test
    void configuredKeyFieldsRejectsBlankValues() {
        ProcessorConfig.FieldRule duplicateRule = duplicateRule();
        duplicateRule.setKeyFields(List.of("id", "  "));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DuplicateProcessorValidationRule.configuredKeyFields("id", duplicateRule)
        );

        assertTrue(exception.getMessage().contains("non-blank"));
        assertTrue(exception.getMessage().contains("keyFields"));
    }

    @Test
    void configuredWinnerSelectorsTrimsValuesAndBuildsDescendingFlags() {
        ProcessorConfig.FieldRule duplicateRule = duplicateRule();
        duplicateRule.setOrderBy(List.of(orderBy(" eventTime ", " desc "), orderBy("sequenceNo", "ASC")));

        List<DuplicateProcessorValidationRule.OrderSelector> selectors =
                DuplicateProcessorValidationRule.configuredWinnerSelectors(duplicateRule);

        assertEquals(2, selectors.size());
        assertEquals("eventTime", selectors.get(0).field());
        assertTrue(selectors.get(0).descending());
        assertEquals("sequenceNo", selectors.get(1).field());
        assertFalse(selectors.get(1).descending());
    }

    @Test
    void configuredWinnerSelectorsRejectsRepeatedFields() {
        ProcessorConfig.FieldRule duplicateRule = duplicateRule();
        duplicateRule.setOrderBy(List.of(orderBy("eventTime", "DESC"), orderBy(" eventTime ", "ASC")));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> DuplicateProcessorValidationRule.configuredWinnerSelectors(duplicateRule)
        );

        assertTrue(exception.getMessage().contains("orderBy[].field"));
        assertTrue(exception.getMessage().contains("unique"));
    }

    @Test
    void validateConfigurationRejectsUnknownCompositeKeyField() {
        ProcessorConfig.EntityMapping mapping = entityMapping();
        ProcessorConfig.FieldMapping idField = mapping.getFields().get(0);
        ProcessorConfig.FieldRule duplicateRule = duplicateRule();
        duplicateRule.setKeyFields(List.of("id", "missingField"));
        idField.setRules(List.of(duplicateRule));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> rule.validateConfiguration(mapping, idField, duplicateRule)
        );

        assertTrue(exception.getMessage().contains("unknown keyFields"));
        assertTrue(exception.getMessage().contains("missingField"));
    }

    @Test
    void validateConfigurationRejectsUnknownWinnerOrderField() {
        ProcessorConfig.EntityMapping mapping = entityMapping();
        ProcessorConfig.FieldMapping idField = mapping.getFields().get(0);
        ProcessorConfig.FieldRule duplicateRule = duplicateRule();
        duplicateRule.setOrderBy(List.of(orderBy("missingOrderField", "DESC")));
        idField.setRules(List.of(duplicateRule));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> rule.validateConfiguration(mapping, idField, duplicateRule)
        );

        assertTrue(exception.getMessage().contains("winner-order field"));
        assertTrue(exception.getMessage().contains("missingOrderField"));
    }

    @Test
    void validateConfigurationRejectsMoreThanOneWinnerSelectingDuplicateRulePerMapping() {
        ProcessorConfig.FieldRule firstDuplicateRule = duplicateRule();
        firstDuplicateRule.setOrderBy(List.of(orderBy("eventTime", "DESC")));

        ProcessorConfig.FieldRule secondDuplicateRule = duplicateRule();
        secondDuplicateRule.setKeyFields(List.of("description"));
        secondDuplicateRule.setOrderBy(List.of(orderBy("description", "ASC")));

        ProcessorConfig.FieldMapping idField = fieldMapping("id", firstDuplicateRule);
        ProcessorConfig.FieldMapping eventTimeField = fieldMapping("eventTime");
        ProcessorConfig.FieldMapping descriptionField = fieldMapping("description", secondDuplicateRule);

        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Events");
        mapping.setTarget("EventsCsv");
        mapping.setFields(List.of(idField, eventTimeField, descriptionField));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> rule.validateConfiguration(mapping, idField, firstDuplicateRule)
        );

        assertTrue(exception.getMessage().contains("more than one winner-selecting 'duplicate' rule"));
    }

    private ProcessorConfig.EntityMapping entityMapping() {
        ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
        mapping.setSource("Events");
        mapping.setTarget("EventsCsv");
        mapping.setFields(List.of(
                fieldMapping("id"),
                fieldMapping("eventTime"),
                fieldMapping("description")
        ));
        return mapping;
    }

    private ProcessorConfig.FieldMapping fieldMapping(String from, ProcessorConfig.FieldRule... rules) {
        ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
        fieldMapping.setFrom(from);
        fieldMapping.setTo(from);
        if (rules.length > 0) {
            fieldMapping.setRules(List.of(rules));
        }
        return fieldMapping;
    }

    private ProcessorConfig.FieldRule duplicateRule() {
        ProcessorConfig.FieldRule duplicateRule = new ProcessorConfig.FieldRule();
        duplicateRule.setType("duplicate");
        return duplicateRule;
    }

    private ProcessorConfig.OrderByField orderBy(String field, String direction) {
        ProcessorConfig.OrderByField orderByField = new ProcessorConfig.OrderByField();
        orderByField.setField(field);
        orderByField.setDirection(direction);
        return orderByField;
    }
}

