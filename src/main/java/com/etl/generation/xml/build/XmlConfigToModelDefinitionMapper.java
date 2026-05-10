package com.etl.generation.xml.build;

import com.etl.common.util.ValidationUtils;
import com.etl.config.FieldDefinition;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.XmlTargetConfig;
import com.etl.generation.xml.XmlFieldDefinition;
import com.etl.generation.xml.XmlModelDefinition;

import java.util.List;

/**
 * Maps flat XML source/target configs to structural XML model definitions.
 * Nested XML contracts should use an external model definition path instead.
 */
public class XmlConfigToModelDefinitionMapper {

    public XmlModelDefinition fromSourceConfig(XmlSourceConfig config) {
        ValidationUtils.requireNonNull(config, "XmlSourceConfig must not be null.");
        return toDefinition(config.getPackageName(), config.getRootElement(), config.getRecordElement(), config.getFields());
    }

    public XmlModelDefinition fromTargetConfig(XmlTargetConfig config) {
        ValidationUtils.requireNonNull(config, "XmlTargetConfig must not be null.");
        return toDefinition(config.getPackageName(), config.getRootElement(), config.getRecordElement(), config.getFields());
    }

    private XmlModelDefinition toDefinition(String packageName,
                                            String rootElement,
                                            String recordElement,
                                            List<? extends FieldDefinition> fields) {
        ValidationUtils.requireNonBlank("Invalid XML config for model generation", packageName, rootElement, recordElement);
        ValidationUtils.requireNonEmpty(fields, "XML config must define at least one field for model generation.");

        XmlModelDefinition definition = new XmlModelDefinition();
        definition.setPackageName(packageName);
        definition.setRootElement(rootElement);
        definition.setRecordElement(recordElement);
        definition.setFields(fields.stream().map(this::toFieldDefinition).toList());
        return definition;
    }

    private XmlFieldDefinition toFieldDefinition(FieldDefinition field) {
        XmlFieldDefinition definition = new XmlFieldDefinition();
        definition.setName(field.getName());
        definition.setType(field.getType());
        return definition;
    }
}

