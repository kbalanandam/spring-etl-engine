package com.etl.runtime;

import org.springframework.stereotype.Component;

@Component
public class DuplicateResolverFactory {

	public DuplicateResolver create(DuplicateRule rule, boolean useEmbeddedDb) {
		if (useEmbeddedDb) {
			return new EmbeddedDbDuplicateResolver(rule);
		}
		return new InMemoryDuplicateResolver(rule);
	}
}


