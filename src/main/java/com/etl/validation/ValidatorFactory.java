package com.etl.validation;

import com.etl.validation.config.FieldRuleConfig;
import com.etl.validation.config.XmlValidationConfig;
import com.etl.validation.config.CsvValidationConfig;
import com.etl.validation.rules.NotNullRule;
import com.etl.validation.rules.RegexRule;
import com.etl.validation.rules.ValidationRule;
import com.etl.validation.rules.XsdValidationRule;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ValidatorFactory {
    public static Validator<Map<String, Object>> createCsvValidator(String fileName, CsvValidationConfig csvConfig) {
        List<FieldRuleConfig> ruleConfigs = csvConfig.getFiles().get(fileName);
        List<ValidationRule<Map<String, Object>>> rules = new ArrayList<>();
        if (ruleConfigs != null) {
            for (FieldRuleConfig config : ruleConfigs) {
                if (!config.isEnabled()) continue;
                switch (config.getRule()) {
                    case "notNull":
                        rules.add(new NotNullRule(config.getField()));
                        break;
                    case "regex":
                        rules.add(new RegexRule(config.getField(), config.getPattern()));
                        break;
                    // Add more CSV rules as needed
                }
            }
        }
        return new Validator<>(rules);
    }

    public static Validator<File> createXmlValidator(String fileName, XmlValidationConfig xmlConfig) {
        List<FieldRuleConfig> ruleConfigs = xmlConfig.getFiles().get(fileName);
        List<ValidationRule<File>> rules = new ArrayList<>();
        if (ruleConfigs != null) {
            for (FieldRuleConfig config : ruleConfigs) {
                if (!config.isEnabled()) continue;
                if ("xsd".equals(config.getRule())) {
                    rules.add(new XsdValidationRule(config.getXsdPath()));
                }
                // Add more XML rules as needed
            }
        }
        return new Validator<>(rules);
    }
}
