package com.etl.source.xml.strategy;

import com.etl.source.xml.runtime.XmlSourceRuntimeContext;
import org.springframework.stereotype.Service;

/**
 * Selects the effective XML flattening strategy for the active source/job context.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This selector sits on the XML reader path between runtime-context assembly and actual XML
 * flattening. Its responsibility is intentionally narrow: decide whether the current source should
 * use one of the shipped registry strategies or a job-specific strategy bean, then enforce that
 * the selected strategy explicitly supports the active runtime context.</p>
 *
 * <p>The selector does not perform flattening itself and does not own strategy registration. Those
 * responsibilities stay with {@link XmlSourceStrategy}, {@link XmlSourceStrategyRegistry}, and
 * {@link JobSpecificXmlStrategyResolver} so the XML reader can stay focused on reader construction
 * while strategy-specific XML shaping remains behind the strategy seam.</p>
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

    /**
     * Resolves the XML strategy that should flatten data for the supplied runtime context.
     *
     * <p>When the effective flattening strategy is {@link XmlFlatteningStrategyNames#JOB_SPECIFIC_XML},
     * selection is delegated to the job-specific bean resolver. All other values resolve through the
     * shipped strategy registry. After resolution, the selector performs one final
     * {@link XmlSourceStrategy#supports(XmlSourceRuntimeContext)} check so unsupported combinations
     * fail early with source-aware diagnostics.</p>
     *
     * @throws IllegalArgumentException when no matching strategy exists or the resolved strategy does
     * not support the active source context
     */
    public XmlSourceStrategy select(XmlSourceRuntimeContext context) {
        // Job-specific strategy resolution is only used when the selected config
        // explicitly requests that mode; all other cases stay on the shipped registry path.
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

