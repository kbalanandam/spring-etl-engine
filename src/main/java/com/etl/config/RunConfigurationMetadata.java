package com.etl.config;

import com.etl.config.job.JobConfig;
import com.etl.runtime.job.JobRecoveryPolicy;
import com.etl.runtime.job.JobRunMode;
import com.etl.runtime.job.JobRuntimeDescriptor;
import com.etl.runtime.job.JobStepDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal runtime selection metadata shared with startup and logging concerns.
 *
 * @param scenarioName resolved scenario name for the selected run
 * @param jobConfigPath absolute job-config path when explicit job selection is used
 * @param demoFallbackMode whether the runtime is using the explicit demo fallback path
 * @param mainFlowName normalized main-flow identity for the selected run
 * @param subFlowName normalized subflow identity for the selected run
 * @param recoveryPolicy current recovery policy for failed runs
 * @param steps explicit step definitions in execution order for the selected run
 */
public record RunConfigurationMetadata(
        String scenarioName,
        String jobConfigPath,
        boolean demoFallbackMode,
        String mainFlowName,
        String subFlowName,
        JobRecoveryPolicy recoveryPolicy,
        List<JobConfig.JobStepConfig> steps
) {

  public static RunConfigurationMetadata fromJobRuntimeDescriptor(JobRuntimeDescriptor descriptor) {
    if (descriptor == null) {
      throw new IllegalArgumentException("descriptor must not be null.");
    }
    return new RunConfigurationMetadata(
        descriptor.scenarioName(),
        descriptor.jobConfigPath(),
        descriptor.runMode() == JobRunMode.DEMO_FALLBACK,
        descriptor.mainFlowName(),
        descriptor.subFlowName(),
        descriptor.recoveryPolicy(),
        toJobSteps(descriptor.steps())
    );
  }

  private static List<JobConfig.JobStepConfig> toJobSteps(List<JobStepDescriptor> steps) {
    if (steps == null || steps.isEmpty()) {
      return List.of();
    }
    List<JobConfig.JobStepConfig> projected = new ArrayList<>();
    for (JobStepDescriptor step : steps) {
      JobConfig.JobStepConfig jobStep = new JobConfig.JobStepConfig();
      jobStep.setName(step.stepName());
      jobStep.setSource(step.sourceName());
      jobStep.setTarget(step.targetName());
      projected.add(jobStep);
    }
    return List.copyOf(projected);
  }
}

