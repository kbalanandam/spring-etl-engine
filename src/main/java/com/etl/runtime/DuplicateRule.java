package com.etl.runtime;

import com.etl.config.processor.ProcessorConfig;
import com.etl.processor.validation.DuplicateProcessorValidationRule;

import java.util.List;
import java.util.Optional;

public record DuplicateRule(
		String anchorField,
		List<String> keyFields,
		List<DuplicateProcessorValidationRule.OrderSelector> orderSelectors
) {
	public DuplicateRule {
		keyFields = List.copyOf(keyFields);
		orderSelectors = List.copyOf(orderSelectors);
	}

	public static Optional<DuplicateRule> resolveConfiguration(ProcessorConfig.EntityMapping mapping) {
		if (mapping == null || mapping.getFields() == null) {
			return Optional.empty();
		}

		for (ProcessorConfig.FieldMapping fieldMapping : mapping.getFields()) {
			if (fieldMapping.getRules() == null) {
				continue;
			}
			for (ProcessorConfig.FieldRule rule : fieldMapping.getRules()) {
				if (rule == null || !"duplicate".equals(rule.getType())) {
					continue;
				}
				if (!DuplicateProcessorValidationRule.isWinnerSelectionStrategy(rule)) {
					continue;
				}
				return Optional.of(new DuplicateRule(
						fieldMapping.getFrom(),
						DuplicateProcessorValidationRule.configuredKeyFields(fieldMapping.getFrom(), rule),
						DuplicateProcessorValidationRule.configuredWinnerSelectors(rule)
				));
			}
		}
		return Optional.empty();
	}
}


