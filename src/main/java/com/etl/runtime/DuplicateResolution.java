package com.etl.runtime;

import java.util.List;

public record DuplicateResolution(
		List<Object> retainedRecords,
		List<DuplicateDiscard> discardedRecords
) {
	public DuplicateResolution {
		retainedRecords = retainedRecords == null ? List.of() : List.copyOf(retainedRecords);
		discardedRecords = discardedRecords == null ? List.of() : List.copyOf(discardedRecords);
	}
}


