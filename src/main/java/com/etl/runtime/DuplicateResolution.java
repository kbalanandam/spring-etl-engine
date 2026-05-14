package com.etl.runtime;

import java.util.List;

/**
 * Immutable result of ordered duplicate winner selection for one processor mapping.
 *
 * <p>{@code retainedRecords} keeps the records that should continue through the write path in
 * their final stable output order. {@code discardedRecords} carries the loser records and their
 * duplicate validation evidence so reject/archive handling and logging can report what happened.</p>
 */
public record DuplicateResolution(
		List<Object> retainedRecords,
		List<DuplicateDiscard> discardedRecords
) {
	public DuplicateResolution {
		retainedRecords = retainedRecords == null ? List.of() : List.copyOf(retainedRecords);
		discardedRecords = discardedRecords == null ? List.of() : List.copyOf(discardedRecords);
	}
}


