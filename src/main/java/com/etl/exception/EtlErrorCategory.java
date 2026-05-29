package com.etl.exception;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * High-level ETL failure categories used for operator-facing diagnostics and logs.
 */
public enum EtlErrorCategory {
	CONFIG("config", "configuration"),
	VALIDATION("validation"),
	TRANSFORMATION("transformation", "transform"),
	SOURCE_READ("source-read", "read", "source_read"),
	TARGET_WRITE("target-write", "write", "target_write"),
	RUNTIME("runtime", "infrastructure", "infra"),
	FACTORY("factory"),
	LISTENER("listener"),
	RELATIONAL("relational"),
	UNCLASSIFIED("unclassified");

	private final String logValue;
	private final Set<String> tokens;

	EtlErrorCategory(String logValue, String... aliases) {
		this.logValue = logValue;
		Set<String> normalizedTokens = new LinkedHashSet<>();
		normalizedTokens.add(normalizeToken(logValue));
		if (aliases != null) {
			Arrays.stream(aliases)
					.filter(alias -> alias != null && !alias.isBlank())
					.map(EtlErrorCategory::normalizeToken)
					.forEach(normalizedTokens::add);
		}
		this.tokens = Set.copyOf(normalizedTokens);
	}

	public String logValue() {
		return logValue;
	}

	public boolean matchesToken(String token) {
		if (token == null || token.isBlank()) {
			return false;
		}
		return tokens.contains(normalizeToken(token));
	}

	public static Optional<EtlErrorCategory> fromToken(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		for (EtlErrorCategory category : EtlErrorCategory.values()) {
			if (category.matchesToken(token)) {
				return Optional.of(category);
			}
		}
		return Optional.empty();
	}

	private static String normalizeToken(String token) {
		return token.trim().toLowerCase(Locale.ROOT).replace('_', '-');
	}
}

