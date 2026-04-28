package com.etl.runtime;

public interface DuplicateResolver extends AutoCloseable {

	void accept(Object input);

	DuplicateResolution complete();

	@Override
	default void close() {
		// no-op by default
	}
}


