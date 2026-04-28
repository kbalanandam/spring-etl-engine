package com.etl.runtime;

import com.etl.processor.validation.DuplicateProcessorValidationRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DuplicateResolverFactoryTest {

	private final DuplicateResolverFactory factory = new DuplicateResolverFactory();

	@Test
	void returnsInMemoryResolverWhenEmbeddedDbIsNotRequested() {
		DuplicateResolver resolver = factory.create(rule(), false);
		assertInstanceOf(InMemoryDuplicateResolver.class, resolver);
	}

	@Test
	void returnsEmbeddedDbResolverWhenRequested() {
		DuplicateResolver resolver = factory.create(rule(), true);
		try {
			assertInstanceOf(EmbeddedDbDuplicateResolver.class, resolver);
		} finally {
			resolver.close();
		}
	}

	private DuplicateRule rule() {
		return new DuplicateRule(
				"id",
				List.of("id"),
				List.of(new DuplicateProcessorValidationRule.OrderSelector("eventTime", true))
		);
	}
}


