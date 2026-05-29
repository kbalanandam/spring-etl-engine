package com.etl.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtlErrorCategoryTest {

	@Test
	void resolvesStableAndAliasTokens() {
		assertEquals(EtlErrorCategory.SOURCE_READ, EtlErrorCategory.fromToken("source-read").orElseThrow());
		assertEquals(EtlErrorCategory.SOURCE_READ, EtlErrorCategory.fromToken("read").orElseThrow());
		assertEquals(EtlErrorCategory.TARGET_WRITE, EtlErrorCategory.fromToken("write").orElseThrow());
		assertEquals(EtlErrorCategory.TRANSFORMATION, EtlErrorCategory.fromToken("transform").orElseThrow());
		assertEquals(EtlErrorCategory.RUNTIME, EtlErrorCategory.fromToken("infra").orElseThrow());
		assertEquals(EtlErrorCategory.CONFIG, EtlErrorCategory.fromToken("configuration").orElseThrow());
	}

	@Test
	void returnsEmptyForUnknownToken() {
		assertTrue(EtlErrorCategory.fromToken("not-a-category").isEmpty());
	}
}

