package com.etl.step;

import com.etl.config.job.JobConfig;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.JobInstance;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CustomStepOutcomeMapperTest {

    private final CustomStepOutcomeMapper mapper = new CustomStepOutcomeMapper();

    @Test
    void resolveActionReturnsMappedActionForExitStatus() {
        StepContribution contribution = contributionWithExitStatus("ok");
        JobConfig.CustomStepConfig custom = customConfig(Map.of("ok", "CONTINUE"));

        CustomStepOutcomeMapper.OutcomeAction action = mapper.resolveAction(custom, contribution);

        assertEquals(CustomStepOutcomeMapper.OutcomeAction.CONTINUE, action);
    }

    @Test
    void resolveActionMatchesResultCodeCaseInsensitively() {
        StepContribution contribution = contributionWithExitStatus("OK");
        JobConfig.CustomStepConfig custom = customConfig(Map.of("ok", "STOP"));

        CustomStepOutcomeMapper.OutcomeAction action = mapper.resolveAction(custom, contribution);

        assertEquals(CustomStepOutcomeMapper.OutcomeAction.STOP, action);
    }

    @Test
    void resolveActionReturnsNullWhenNoMappingExists() {
        StepContribution contribution = contributionWithExitStatus("other");
        JobConfig.CustomStepConfig custom = customConfig(Map.of("ok", "FAIL"));

        CustomStepOutcomeMapper.OutcomeAction action = mapper.resolveAction(custom, contribution);

        assertNull(action);
    }

    @Test
    void resolveActionReturnsNullWhenMetadataIsMissing() {
        StepContribution contribution = contributionWithExitStatus("ok");

        assertNull(mapper.resolveAction(null, contribution));
        assertNull(mapper.resolveAction(new JobConfig.CustomStepConfig(), contribution));
    }

    private StepContribution contributionWithExitStatus(String exitCode) {
        JobInstance jobInstance = new JobInstance(1L, "job");
        JobExecution jobExecution = new JobExecution(jobInstance, 1L, null);
        StepExecution stepExecution = new StepExecution("step", jobExecution, 1L);
        StepContribution contribution = new StepContribution(stepExecution);
        contribution.setExitStatus(new ExitStatus(exitCode));
        return contribution;
    }

    private JobConfig.CustomStepConfig customConfig(Map<String, String> onResult) {
        JobConfig.CustomStepConfig custom = new JobConfig.CustomStepConfig();
        custom.setOnResult(onResult);
        return custom;
    }
}



