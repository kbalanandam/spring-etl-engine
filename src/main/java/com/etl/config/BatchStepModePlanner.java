package com.etl.config;

import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.runtime.DuplicateRule;
import com.etl.runtime.job.JobSubFlowDescriptor;
import org.slf4j.Logger;

/**
 * Bridge seam for step mode planning extracted from BatchConfig.
 *
 * <p>This planner keeps mode-selection behavior stable while reducing orchestration weight.
 * It decides chunk/tasklet mode and duplicate resolver strategy for a standard step.</p>
 */
final class BatchStepModePlanner {

    private final Logger logger;
    private final RunConfigurationMetadata runConfigurationMetadata;

    BatchStepModePlanner(Logger logger,
                         RunConfigurationMetadata runConfigurationMetadata) {
        this.logger = logger;
        this.runConfigurationMetadata = runConfigurationMetadata;
    }

    StepModePlan plan(String stepName,
                      SourceConfig sourceConfig,
                      TargetConfig targetConfig,
                      ProcessorConfig.EntityMapping mapping,
                      JobConfig.SkipPolicyConfig configuredSkipPolicy,
                      JobConfig.RetryPolicyConfig configuredRetryPolicy,
                      JobSubFlowDescriptor stepSubFlow,
                      int chunkThreshold) {
        boolean useChunk;
        int recordCount;
        boolean recordCountUnknown = false;
        try {
            recordCount = sourceConfig.getRecordCount();
        } catch (Exception e) {
            logger.warn("Could not count records for source: {}. Defaulting to chunk mode.", sourceConfig.getSourceName(), e);
            recordCountUnknown = true;
            recordCount = chunkThreshold + 1;
        }
        if (recordCount < 0) {
            logger.info("Record count is unknown for source: {}. Defaulting to chunk mode.", sourceConfig.getSourceName());
            recordCountUnknown = true;
            recordCount = chunkThreshold + 1;
        }
        useChunk = recordCount > chunkThreshold;

        if (configuredSkipPolicy != null && configuredSkipPolicy.isEnabled() && !useChunk) {
            logger.info("STEP_READY event=step_mode_override mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} originalMode=tasklet overriddenMode=chunk reason=skip-policy-requires-fault-tolerant-chunk",
                    runConfigurationMetadata.mainFlowName(),
                    stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                    runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                    stepName,
                    sourceConfig.getSourceName(),
                    targetConfig.getTargetName());
            useChunk = true;
        }
        if (configuredRetryPolicy != null && configuredRetryPolicy.isEnabled() && !useChunk) {
            logger.info("STEP_READY event=step_mode_override mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} originalMode=tasklet overriddenMode=chunk reason=retry-policy-requires-fault-tolerant-chunk",
                    runConfigurationMetadata.mainFlowName(),
                    stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                    runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                    stepName,
                    sourceConfig.getSourceName(),
                    targetConfig.getTargetName());
            useChunk = true;
        }

        DuplicateRule duplicateRule = DuplicateRule.resolveConfiguration(mapping).orElse(null);
        DuplicateRule.StorageMode duplicateStorageMode = duplicateRule == null
                ? DuplicateRule.StorageMode.AUTO
                : duplicateRule.storageMode();
        boolean useEmbeddedDbDuplicateResolver = duplicateRule != null && switch (duplicateStorageMode) {
            case AUTO -> recordCount > chunkThreshold;
            case MEMORY -> false;
            case EMBEDDED_DB -> true;
        };

        String orderedDuplicateResolverMode = null;
        String orderedDuplicateResolverReason = null;
        if (duplicateRule != null) {
            orderedDuplicateResolverMode = useEmbeddedDbDuplicateResolver ? "embeddedDb" : "inMemory";
            orderedDuplicateResolverReason = switch (duplicateStorageMode) {
                case MEMORY -> "configured_storage_mode_memory";
                case EMBEDDED_DB -> "configured_storage_mode_embeddedDb";
                case AUTO -> useEmbeddedDbDuplicateResolver
                        ? (recordCountUnknown ? "record_count_unknown_defaults_to_large_input_path" : "record_count_exceeds_chunk_threshold")
                        : "record_count_within_chunk_threshold";
            };
        }

        if (duplicateRule != null && useChunk) {
            if (configuredSkipPolicy != null && configuredSkipPolicy.isEnabled()) {
                throw new IllegalStateException("Step '" + stepName + "' configures both ordered duplicate winner selection and skipPolicy. This first slice does not support combining those modes.");
            }
            if (configuredRetryPolicy != null && configuredRetryPolicy.isEnabled()) {
                throw new IllegalStateException("Step '" + stepName + "' configures both ordered duplicate winner selection and retryPolicy. This first slice does not support combining those modes.");
            }
            logger.info("STEP_READY event=step_mode_override mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} duplicateStrategy=orderBy duplicateIdentityMode={} duplicateIdentityModeReason={} originalMode=chunk overriddenMode=tasklet reason=ordered-duplicate-winner-selection-requires-final-buffering",
                    runConfigurationMetadata.mainFlowName(),
                    stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                    runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                    stepName,
                    sourceConfig.getSourceName(),
                    targetConfig.getTargetName(),
                    duplicateRule.identityMode().configValue(),
                    duplicateRule.identityModeReason());
            useChunk = false;
        }

        if (duplicateRule != null) {
            logger.info("STEP_READY event=duplicate_resolver_plan mainFlow={} subFlow={} recoveryPolicy={} stepName={} source={} target={} duplicateStrategy=orderBy duplicateIdentityMode={} duplicateIdentityModeReason={} resolverMode={} resolverReason={} recordCount={} threshold={}",
                    runConfigurationMetadata.mainFlowName(),
                    stepSubFlow == null ? runConfigurationMetadata.subFlowName() : stepSubFlow.subFlowName(),
                    runConfigurationMetadata.recoveryPolicy() == null ? "" : runConfigurationMetadata.recoveryPolicy().logValue(),
                    stepName,
                    sourceConfig.getSourceName(),
                    targetConfig.getTargetName(),
                    duplicateRule.identityMode().configValue(),
                    duplicateRule.identityModeReason(),
                    orderedDuplicateResolverMode,
                    orderedDuplicateResolverReason,
                    recordCount,
                    chunkThreshold);
        }

        return new StepModePlan(
                useChunk,
                recordCount,
                duplicateRule,
                useEmbeddedDbDuplicateResolver,
                orderedDuplicateResolverMode,
                orderedDuplicateResolverReason
        );
    }

    record StepModePlan(boolean useChunk,
                        int recordCount,
                        DuplicateRule duplicateRule,
                        boolean useEmbeddedDbDuplicateResolver,
                        String orderedDuplicateResolverMode,
                        String orderedDuplicateResolverReason) {
    }
}


