package com.etl.source.xml.strategy;

import com.etl.source.xml.runtime.XmlSourceRuntimeContext;

/**
 * Base class for job-specific XML flattening implementations.
 *
 * <p>Custom strategies that are selected through the {@code JOB_SPECIFIC_XML} mode can extend this
 * class to inherit the standard support check plus convenience accessors for job and source names.
 * The actual flattening logic remains entirely job-owned.</p>
 */
public abstract class JobSpecificXmlSourceStrategy extends AbstractXmlSourceStrategy {

    @Override
    public boolean supports(XmlSourceRuntimeContext context) {
        return XmlFlatteningStrategyNames.JOB_SPECIFIC_XML.equalsIgnoreCase(context.getEffectiveFlatteningStrategy());
    }

    /**
     * Returns the current selected job name for job-specific diagnostics or branching.
     */
    protected String jobName(XmlSourceRuntimeContext context) {
        return context.getJobName();
    }

    /**
     * Returns the active XML source name for job-specific diagnostics or branching.
     */
    protected String sourceName(XmlSourceRuntimeContext context) {
        return context.getSourceName();
    }
}

