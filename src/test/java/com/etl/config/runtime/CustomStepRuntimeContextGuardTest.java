package com.etl.config.runtime;

import com.etl.config.job.JobConfig;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ExecutionContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomStepRuntimeContextGuardTest {

    private final CustomStepRuntimeContextGuard guard = new CustomStepRuntimeContextGuard();

    @Test
    void validateBeforeExecutionFailsWhenConsumedKeyMissing() {
        JobConfig.CustomStepConfig custom = customConfig(
                null,
                Map.of("runId", "header.runId:long")
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> guard.validateBeforeExecution("header-complete", custom, new ExecutionContext())
        );

        assertTrue(exception.getMessage().contains("is missing from execution context"));
    }

    @Test
    void validateBeforeExecutionFailsWhenConsumedKeyTypeDoesNotMatch() {
        JobConfig.CustomStepConfig custom = customConfig(
                null,
                Map.of("runId", "header.runId:long")
        );
        ExecutionContext context = new ExecutionContext();
        context.putString("header.runId", "abc");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> guard.validateBeforeExecution("header-complete", custom, context)
        );

        assertTrue(exception.getMessage().contains("with type 'long'"));
    }

    @Test
    void validateBeforeExecutionFailsWhenPublishKeyAlreadyOwnedByAnotherStep() {
        JobConfig.CustomStepConfig custom = customConfig(
                Map.of("runId", "header.runId"),
                null
        );
        ExecutionContext context = new ExecutionContext();
        context.putLong("header.runId", 10L);
        context.putString("custom.context.owner.header.runId", "header-start");

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> guard.validateBeforeExecution("header-complete", custom, context)
        );

        assertTrue(exception.getMessage().contains("already owned by step 'header-start'"));
    }

    @Test
    void validateAfterExecutionFailsWhenDeclaredPublishKeyWasNotWritten() {
        JobConfig.CustomStepConfig custom = customConfig(
                Map.of("runId", "header.runId"),
                null
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> guard.validateAfterExecution("header-start", custom, new ExecutionContext())
        );

        assertTrue(exception.getMessage().contains("did not publish that key"));
    }

    @Test
    void validateBeforeAndAfterExecutionAcceptsValidContextContracts() {
        JobConfig.CustomStepConfig custom = customConfig(
                Map.of("runId", "header.runId"),
                Map.of("auditId", "audit.id:string")
        );
        ExecutionContext context = new ExecutionContext();
        context.putString("audit.id", "A-1");

        assertDoesNotThrow(() -> guard.validateBeforeExecution("header-start", custom, context));

        context.putLong("header.runId", 10L);
        assertDoesNotThrow(() -> guard.validateAfterExecution("header-start", custom, context));
        assertTrue(context.containsKey("custom.context.owner.header.runId"));
    }

    private static JobConfig.CustomStepConfig customConfig(Map<String, String> publish, Map<String, String> consume) {
        JobConfig.CustomStepConfig custom = new JobConfig.CustomStepConfig();
        custom.setType("auditNoop");
        custom.setPublish(publish);
        custom.setConsume(consume);
        return custom;
    }
}

