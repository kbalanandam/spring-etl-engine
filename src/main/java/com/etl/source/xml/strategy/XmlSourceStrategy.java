package com.etl.source.xml.strategy;

import com.etl.source.xml.runtime.XmlFlatteningResult;
import com.etl.source.xml.runtime.XmlSourceRuntimeContext;

/**
 * Strategy contract for turning XML source structures into flat runtime rows.
 *
 * <p><strong>Transition status:</strong> REUSE.</p>
 *
 * <p>Implementations sit behind the XML reader/selector seam. They receive an
 * {@link XmlSourceRuntimeContext} plus either a root wrapper object or a fragment object depending
 * on the active reader path, then return the flattened rows that should continue through the shared
 * processor pipeline.</p>
 */
public interface XmlSourceStrategy {

    /**
     * Returns the configuration strategy name claimed by this implementation.
     */
    String getStrategyName();

    /**
     * Returns whether this strategy supports the supplied XML runtime context.
     */
    boolean supports(XmlSourceRuntimeContext context);

    /**
     * Flattens the supplied XML root or fragment object into runtime rows.
     */
    XmlFlatteningResult flatten(XmlSourceRuntimeContext context, Object xmlRoot);
}

