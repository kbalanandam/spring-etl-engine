package com.etl.config.source.validation;

import com.etl.config.source.RelationalSourceConfig;
import com.etl.config.source.SourceConfig;
import org.springframework.stereotype.Component;

@Component
public class RelationalSourceValidator implements SourceValidator {

	@Override
	public boolean supports(SourceConfig sourceConfig) {
		return sourceConfig instanceof RelationalSourceConfig;
	}

	@Override
	public void validate(SourceConfig sourceConfig, SourceValidationContext context) {
		((RelationalSourceConfig) sourceConfig).validate();
	}
}

