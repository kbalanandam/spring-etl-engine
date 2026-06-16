package com.etl.config.batch.bridge;

import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridge seam for step source/target/mapping resolution extracted from BatchConfig.
 *
 * <p>This helper keeps explicit-step resolution behavior stable while reducing
 * orchestration weight in BatchConfig.</p>
 */
public final class BatchConfigStepResolutionSupport {

    private final ProcessorConfig processorConfig;

    public BatchConfigStepResolutionSupport(ProcessorConfig processorConfig) {
        this.processorConfig = processorConfig;
    }

    public Map<String, SourceConfig> mapSourcesByName(List<? extends SourceConfig> sources) {
        Map<String, SourceConfig> sourceByName = new LinkedHashMap<>();
        for (SourceConfig source : sources) {
            if (source.getSourceName() == null || source.getSourceName().isBlank()) {
                throw new IllegalStateException("Source configuration contains a blank sourceName.");
            }
            if (sourceByName.putIfAbsent(source.getSourceName(), source) != null) {
                throw new IllegalStateException("Duplicate sourceName found in source configuration: " + source.getSourceName());
            }
        }
        return sourceByName;
    }

    public Map<String, TargetConfig> mapTargetsByName(List<TargetConfig> targets) {
        Map<String, TargetConfig> targetByName = new LinkedHashMap<>();
        for (TargetConfig target : targets) {
            if (target.getTargetName() == null || target.getTargetName().isBlank()) {
                throw new IllegalStateException("Target configuration contains a blank targetName.");
            }
            if (targetByName.putIfAbsent(target.getTargetName(), target) != null) {
                throw new IllegalStateException("Duplicate targetName found in target configuration: " + target.getTargetName());
            }
        }
        return targetByName;
    }

    public SourceConfig requireSource(JobConfig.JobStepConfig configuredStep, Map<String, SourceConfig> sourceByName) {
        SourceConfig sourceConfig = sourceByName.get(configuredStep.getSource());
        if (sourceConfig == null) {
            throw new IllegalStateException("Step '" + configuredStep.getName() + "' references unknown source '" + configuredStep.getSource() + "'.");
        }
        return sourceConfig;
    }

    public TargetConfig requireTarget(JobConfig.JobStepConfig configuredStep, Map<String, TargetConfig> targetByName) {
        TargetConfig targetConfig = targetByName.get(configuredStep.getTarget());
        if (targetConfig == null) {
            throw new IllegalStateException("Step '" + configuredStep.getName() + "' references unknown target '" + configuredStep.getTarget() + "'.");
        }
        return targetConfig;
    }

    public ProcessorConfig.EntityMapping requireProcessorMapping(JobConfig.JobStepConfig configuredStep) {
        ProcessorConfig.EntityMapping mapping = processorConfig.getMappings() == null ? null : processorConfig.getMappings().stream()
                .filter(candidate -> configuredStep.getSource().equals(candidate.getSource())
                        && configuredStep.getTarget().equals(candidate.getTarget()))
                .findFirst()
                .orElse(null);
        if (mapping == null) {
            throw new IllegalStateException("Step '" + configuredStep.getName() + "' does not have a matching processor mapping for source '"
                    + configuredStep.getSource() + "' and target '" + configuredStep.getTarget() + "'.");
        }
        return mapping;
    }
}


