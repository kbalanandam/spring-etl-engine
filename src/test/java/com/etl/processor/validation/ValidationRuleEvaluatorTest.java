package com.etl.processor.validation;

import com.etl.config.processor.ProcessorConfig;
import org.junit.jupiter.api.Test;

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

    private static final class EventRecord {
        private final String id;
        private final String eventTime;
        private final String description;

        private EventRecord(String id, String eventTime, String description) {
            this.id = id;
            this.eventTime = eventTime;
            this.description = description;
        }
    }
}

