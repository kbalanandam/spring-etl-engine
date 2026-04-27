package com.etl.config.source.validation;

import com.etl.config.exception.ConfigException;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SourceValidationService {

	private final List<SourceValidator> validators;

	public SourceValidationService() {
		this(List.of(
				new CsvSourceValidator(),
				new XmlSourceValidator(),
				new RelationalSourceValidator()
		));
	}

	@Autowired
	public SourceValidationService(List<SourceValidator> validators) {
		this.validators = validators == null ? List.of() : List.copyOf(validators);
	}

	public void validateSelectedSources(SourceWrapper sourceWrapper, SourceValidationContext context) {
		if (sourceWrapper == null || sourceWrapper.getSources() == null) {
			return;
		}

		for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
			validate(sourceConfig, context);
		}
	}

	public void validate(SourceConfig sourceConfig, SourceValidationContext context) {
		for (SourceValidator validator : validators) {
			if (!validator.supports(sourceConfig)) {
				continue;
			}

			try {
				validator.validate(sourceConfig, context);
			} catch (IllegalArgumentException e) {
				throw new ConfigException("Invalid source validation configuration for scenario '"
						+ context.scenarioName() + "' in " + context.sourceConfigPath() + " (source='"
						+ defaultName(sourceConfig.getSourceName()) + "'): " + e.getMessage(), e);
			}
		}
	}

	private String defaultName(String configuredName) {
		return configuredName == null || configuredName.isBlank() ? "unnamed" : configuredName.trim();
	}
}


