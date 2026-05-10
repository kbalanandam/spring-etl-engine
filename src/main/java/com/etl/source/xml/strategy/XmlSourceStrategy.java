package com.etl.source.xml.strategy;

import com.etl.source.xml.runtime.XmlFlatteningResult;
import com.etl.source.xml.runtime.XmlSourceRuntimeContext;

/**
 * Strategy contract for turning unmarshalled XML models into flat runtime rows.
 */
public interface XmlSourceStrategy {

    String getStrategyName();

    boolean supports(XmlSourceRuntimeContext context);

    XmlFlatteningResult flatten(XmlSourceRuntimeContext context, Object xmlRoot);
}

