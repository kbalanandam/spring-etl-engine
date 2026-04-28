package com.etl.runtime;

import org.springframework.stereotype.Component;

/**
 * Creates the ordered-duplicate resolver implementation chosen for the current step.
 *
 * <p>The factory is used only for winner-selection duplicate handling, where the runtime must
 * retain multiple candidates per duplicate key until a final winner is chosen. It selects either
 * the in-memory resolver or the embedded-database resolver based on the caller's volume-based
 * decision.</p>
 */
@Component
public class DuplicateResolverFactory {

	public DuplicateResolver create(DuplicateRule rule, boolean useEmbeddedDb) {
		if (useEmbeddedDb) {
			return new EmbeddedDbDuplicateResolver(rule);
		}
		return new InMemoryDuplicateResolver(rule);
	}
}


