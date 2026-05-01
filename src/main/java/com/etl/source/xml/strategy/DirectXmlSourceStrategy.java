package com.etl.source.xml.strategy;

import com.etl.source.xml.runtime.XmlFlatteningResult;
import com.etl.source.xml.runtime.XmlSourceRuntimeContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared flattening strategy for simple one-record-to-one-row XML payloads.
 */
@Component
public class DirectXmlSourceStrategy extends AbstractXmlSourceStrategy {

    @Override
    public String getStrategyName() {
        return XmlFlatteningStrategyNames.DIRECT_XML;
    }

    @Override
    public boolean supports(XmlSourceRuntimeContext context) {
        return XmlFlatteningStrategyNames.DIRECT_XML.equalsIgnoreCase(context.getEffectiveFlatteningStrategy());
    }

    @Override
    public XmlFlatteningResult flatten(XmlSourceRuntimeContext context, Object xmlRoot) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object record : extractRecords(context, xmlRoot)) {
            rows.add(context.getFieldMappings().isEmpty()
                    ? flattenDirectRecord(record)
                    : extractMappedValues(record, context.getFieldMappings()));
        }
        return XmlFlatteningResult.ofRows(rows);
    }

    private Map<String, Object> flattenDirectRecord(Object record) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (Field field : readableFields(record.getClass())) {
            Object value = readField(record, field);
            if (value == null) {
                row.put(resolveFieldName(field), null);
                continue;
            }
            if (isSimpleValueType(value.getClass())) {
                row.put(resolveFieldName(field), value);
                continue;
            }
            if (value instanceof Collection<?> collection && collection.stream().allMatch(this::isSimpleValue)) {
                row.put(resolveFieldName(field), List.copyOf(collection));
                continue;
            }
            throw new IllegalArgumentException(
                    "DirectXml strategy cannot flatten nested field '" + resolveFieldName(field) + "' of type "
                            + value.getClass().getName() + "."
            );
        }
        return row;
    }
}

