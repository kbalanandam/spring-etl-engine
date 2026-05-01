package com.etl.source.xml.strategy;

import com.etl.source.xml.runtime.XmlFlatteningResult;
import com.etl.source.xml.runtime.XmlSourceRuntimeContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared flattening strategy for nested XML structures that still follow generic traversal rules.
 */
@Component
public class NestedXmlSourceStrategy extends AbstractXmlSourceStrategy {

    @Override
    public String getStrategyName() {
        return XmlFlatteningStrategyNames.NESTED_XML;
    }

    @Override
    public boolean supports(XmlSourceRuntimeContext context) {
        return XmlFlatteningStrategyNames.NESTED_XML.equalsIgnoreCase(context.getEffectiveFlatteningStrategy());
    }

    @Override
    public XmlFlatteningResult flatten(XmlSourceRuntimeContext context, Object xmlRoot) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object record : extractRecords(context, xmlRoot)) {
            Map<String, Object> row = context.getFieldMappings().isEmpty()
                    ? new LinkedHashMap<>()
                    : extractMappedValues(record, context.getFieldMappings());
            if (context.getFieldMappings().isEmpty()) {
                flattenRecursively(row, "", record);
            }
            rows.add(row);
        }
        return XmlFlatteningResult.ofRows(rows);
    }
}

