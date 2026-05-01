package com.etl.source.xml.runtime;

import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.source.XmlSourceConfig;
import com.etl.source.xml.strategy.XmlFlatteningStrategyNames;

import java.util.Map;

/**
 * Immutable runtime metadata needed by XML source flattening strategies.
 */
public final class XmlSourceRuntimeContext {

    private final String jobName;
    private final String sourceName;
    private final String flatteningStrategy;
    private final String jobSpecificStrategyBean;
    private final XmlSourceConfig xmlSourceConfig;
    private final ResolvedModelMetadata resolvedModelMetadata;
    private final Class<?> rootClass;
    private final Class<?> recordClass;
    private final Map<String, String> fieldMappings;
    private final Map<String, Object> runtimeOptions;

    private XmlSourceRuntimeContext(Builder builder) {
        this.jobName = builder.jobName;
        this.sourceName = builder.sourceName;
        this.flatteningStrategy = builder.flatteningStrategy;
        this.jobSpecificStrategyBean = builder.jobSpecificStrategyBean;
        this.xmlSourceConfig = builder.xmlSourceConfig;
        this.resolvedModelMetadata = builder.resolvedModelMetadata;
        this.rootClass = builder.rootClass;
        this.recordClass = builder.recordClass;
        this.fieldMappings = builder.fieldMappings == null ? Map.of() : Map.copyOf(builder.fieldMappings);
        this.runtimeOptions = builder.runtimeOptions == null ? Map.of() : Map.copyOf(builder.runtimeOptions);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getJobName() {
        return jobName;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getFlatteningStrategy() {
        return flatteningStrategy;
    }

    public String getJobSpecificStrategyBean() {
        return jobSpecificStrategyBean;
    }

    public XmlSourceConfig getXmlSourceConfig() {
        return xmlSourceConfig;
    }

    public ResolvedModelMetadata getResolvedModelMetadata() {
        return resolvedModelMetadata;
    }

    public Class<?> getRootClass() {
        return rootClass;
    }

    public Class<?> getRecordClass() {
        return recordClass;
    }

    public Map<String, String> getFieldMappings() {
        return fieldMappings;
    }

    public Map<String, Object> getRuntimeOptions() {
        return runtimeOptions;
    }

    public String getEffectiveFlatteningStrategy() {
        if (flatteningStrategy != null && !flatteningStrategy.isBlank()) {
            return flatteningStrategy;
        }
        if (xmlSourceConfig != null) {
            String configured = xmlSourceConfig.getFlatteningStrategy();
            if (configured != null && !configured.isBlank()) {
                return configured;
            }
        }
        return XmlFlatteningStrategyNames.DIRECT_XML;
    }

    public boolean isJobSpecific() {
        return XmlFlatteningStrategyNames.JOB_SPECIFIC_XML.equalsIgnoreCase(getEffectiveFlatteningStrategy());
    }

    public String getRequiredJobSpecificStrategyBean() {
        if (jobSpecificStrategyBean != null && !jobSpecificStrategyBean.isBlank()) {
            return jobSpecificStrategyBean;
        }
        if (xmlSourceConfig != null) {
            String configured = xmlSourceConfig.getJobSpecificStrategyBean();
            if (configured != null && !configured.isBlank()) {
                return configured;
            }
        }
        throw new IllegalStateException("No job-specific XML strategy bean configured for source '" + sourceName + "'.");
    }

    public static final class Builder {
        private String jobName;
        private String sourceName;
        private String flatteningStrategy;
        private String jobSpecificStrategyBean;
        private XmlSourceConfig xmlSourceConfig;
        private ResolvedModelMetadata resolvedModelMetadata;
        private Class<?> rootClass;
        private Class<?> recordClass;
        private Map<String, String> fieldMappings;
        private Map<String, Object> runtimeOptions;

        private Builder() {
        }

        public Builder jobName(String jobName) {
            this.jobName = jobName;
            return this;
        }

        public Builder sourceName(String sourceName) {
            this.sourceName = sourceName;
            return this;
        }

        public Builder flatteningStrategy(String flatteningStrategy) {
            this.flatteningStrategy = flatteningStrategy;
            return this;
        }

        public Builder jobSpecificStrategyBean(String jobSpecificStrategyBean) {
            this.jobSpecificStrategyBean = jobSpecificStrategyBean;
            return this;
        }

        public Builder xmlSourceConfig(XmlSourceConfig xmlSourceConfig) {
            this.xmlSourceConfig = xmlSourceConfig;
            return this;
        }

        public Builder resolvedModelMetadata(ResolvedModelMetadata resolvedModelMetadata) {
            this.resolvedModelMetadata = resolvedModelMetadata;
            return this;
        }

        public Builder rootClass(Class<?> rootClass) {
            this.rootClass = rootClass;
            return this;
        }

        public Builder recordClass(Class<?> recordClass) {
            this.recordClass = recordClass;
            return this;
        }

        public Builder fieldMappings(Map<String, String> fieldMappings) {
            this.fieldMappings = fieldMappings;
            return this;
        }

        public Builder runtimeOptions(Map<String, Object> runtimeOptions) {
            this.runtimeOptions = runtimeOptions;
            return this;
        }

        public XmlSourceRuntimeContext build() {
            return new XmlSourceRuntimeContext(this);
        }
    }
}

