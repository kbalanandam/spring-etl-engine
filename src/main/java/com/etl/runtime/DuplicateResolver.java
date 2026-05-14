package com.etl.runtime;

/**
 * Consumes one step's candidate records for ordered duplicate winner selection.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This is the runtime seam used only when a processor {@code duplicate} rule includes
 * {@code orderBy}. Callers stream every candidate record through {@link #accept(Object)}, then
 * request the final retained/discarded outcome once the full input has been seen via
 * {@link #complete()}.</p>
 */
public interface DuplicateResolver extends AutoCloseable {

	/**
	 * Adds one input record to the in-flight duplicate resolution state for the current step.
	 */
	void accept(Object input);

	/**
	 * Finalizes winner selection and returns the records that should continue to write plus the
	 * discarded duplicate evidence that should be surfaced through the processor validation path.
	 */
	DuplicateResolution complete();

	@Override
	default void close() {
		// no-op by default
	}
}


