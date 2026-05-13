package com.etl.runtime;

import com.etl.processor.validation.ValidationIssue;

/**
 * Describes one record that did not survive ordered duplicate resolution.
 *
 * <p>The discard may represent either a true duplicate loser or a record whose configured
 * ordering values could not be compared. The paired {@link ValidationIssue} is the canonical
 * evidence surfaced to the rest of the processor pipeline.</p>
 */
public record DuplicateDiscard(
		Object discardedRecord,
		ValidationIssue issue,
		boolean invalidOrderingValue
) {
}


