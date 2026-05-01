package com.etl.source.xml.strategy;

import com.etl.source.xml.runtime.XmlSourceRuntimeContext;

/**
 * Base class for job-specific XML flattening implementations.
 */
public abstract class JobSpecificXmlSourceStrategy extends AbstractXmlSourceStrategy {

    @Override
    public boolean supports(XmlSourceRuntimeContext context) {
        return XmlFlatteningStrategyNames.JOB_SPECIFIC_XML.equalsIgnoreCase(context.getEffectiveFlatteningStrategy());
    }

    protected String jobName(XmlSourceRuntimeContext context) {
        return context.getJobName();
    }

    protected String sourceName(XmlSourceRuntimeContext context) {
        return context.getSourceName();
    }
}

