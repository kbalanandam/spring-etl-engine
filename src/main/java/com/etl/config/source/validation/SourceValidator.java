package com.etl.config.source.validation;

import com.etl.config.source.SourceConfig;

public interface SourceValidator {

	boolean supports(SourceConfig sourceConfig);

	void validate(SourceConfig sourceConfig, SourceValidationContext context);
}

