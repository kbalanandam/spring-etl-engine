package com.etl.runtime;

import com.etl.processor.validation.ValidationIssue;

public record DuplicateDiscard(
		Object discardedRecord,
		ValidationIssue issue,
		boolean invalidOrderingValue
) {
}


