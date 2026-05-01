package com.etl.source.xml.strategy;

import com.etl.source.xml.runtime.XmlSourceRuntimeContext;
import org.springframework.stereotype.Service;

/**
 * Selects the effective XML flattening strategy for the active source/job context.
 */
@Service
public class XmlSourceStrategySelector {

    private final XmlSourceStrategyRegistry registry;
    private final JobSpecificXmlStrategyResolver jobSpecificResolver;

    public XmlSourceStrategySelector(XmlSourceStrategyRegistry registry,
                                     JobSpecificXmlStrategyResolver jobSpecificResolver) {
        this.registry = registry;
        this.jobSpecificResolver = jobSpecificResolver;
    }

    public XmlSourceStrategy select(XmlSourceRuntimeContext context) {
        String strategyName = context.getEffectiveFlatteningStrategy();
        XmlSourceStrategy strategy = XmlFlatteningStrategyNames.JOB_SPECIFIC_XML.equalsIgnoreCase(strategyName)
                ? jobSpecificResolver.resolve(context)
                : registry.getRequired(strategyName);
        if (!strategy.supports(context)) {
            throw new IllegalArgumentException(
                    "XML source strategy '" + strategy.getStrategyName() + "' does not support source '"
                            + context.getSourceName() + "'."
            );
        }
        return strategy;
    }
}

