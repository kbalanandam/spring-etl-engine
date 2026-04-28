package com.etl.runtime;

import com.etl.common.util.ReflectionUtils;
import com.etl.processor.validation.DuplicateProcessorValidationRule;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

final class DuplicateSupport {

	private DuplicateSupport() {
	}

	static List<Object> resolveKeyValues(Object input, List<String> keyFields) {
		return keyFields.stream()
				.map(keyField -> ReflectionUtils.getFieldValue(input, keyField))
				.toList();
	}

	static boolean hasIncompleteKey(List<Object> keyValues) {
		for (Object keyValue : keyValues) {
			if (keyValue == null) {
				return true;
			}
			if (keyValue instanceof String stringValue && stringValue.isBlank()) {
				return true;
			}
		}
		return false;
	}

	static String buildKey(List<Object> keyValues) {
		StringBuilder builder = new StringBuilder();
		for (Object keyValue : keyValues) {
			String text = keyValue.toString();
			builder.append(text.length()).append(':').append(text).append('|');
		}
		return builder.toString();
	}

	static List<SortCriterionValue> normalizeSortValues(Object input,
	                                                   List<DuplicateProcessorValidationRule.OrderSelector> orderSelectors) {
		List<SortCriterionValue> sortValues = new ArrayList<>();
		for (DuplicateProcessorValidationRule.OrderSelector selector : orderSelectors) {
			Object rawValue = ReflectionUtils.getFieldValue(input, selector.field());
			SortCriterionValue sortValue = normalizeSortableValue(rawValue);
			if (sortValue == null) {
				return null;
			}
			sortValues.add(sortValue);
		}
		return sortValues;
	}

	static int compare(List<SortCriterionValue> left,
	                  List<SortCriterionValue> right,
	                  List<DuplicateProcessorValidationRule.OrderSelector> orderSelectors,
	                  long leftArrivalSequence,
	                  long rightArrivalSequence) {
		for (int i = 0; i < orderSelectors.size(); i++) {
			DuplicateProcessorValidationRule.OrderSelector selector = orderSelectors.get(i);
			int comparison = left.get(i).compareTo(right.get(i));
			if (comparison == 0) {
				continue;
			}
			return selector.descending() ? comparison : -comparison;
		}
		return Long.compare(rightArrivalSequence, leftArrivalSequence);
	}

	static String describeOrderSelectors(List<DuplicateProcessorValidationRule.OrderSelector> orderSelectors) {
		return orderSelectors.stream()
				.map(DuplicateProcessorValidationRule.OrderSelector::toDisplayString)
				.reduce((left, right) -> left + ", " + right)
				.orElse("");
	}

	private static SortCriterionValue normalizeSortableValue(Object rawValue) {
		if (rawValue == null) {
			return null;
		}
		if (rawValue instanceof String stringValue && stringValue.isBlank()) {
			return null;
		}
		if (rawValue instanceof Integer integerValue) {
			return SortCriterionValue.numeric(BigDecimal.valueOf(integerValue));
		}
		if (rawValue instanceof Long longValue) {
			return SortCriterionValue.numeric(BigDecimal.valueOf(longValue));
		}
		if (rawValue instanceof Short shortValue) {
			return SortCriterionValue.numeric(BigDecimal.valueOf(shortValue.longValue()));
		}
		if (rawValue instanceof Byte byteValue) {
			return SortCriterionValue.numeric(BigDecimal.valueOf(byteValue.longValue()));
		}
		if (rawValue instanceof Float floatValue) {
			return SortCriterionValue.numeric(BigDecimal.valueOf(floatValue.doubleValue()));
		}
		if (rawValue instanceof Double doubleValue) {
			return SortCriterionValue.numeric(BigDecimal.valueOf(doubleValue));
		}
		if (rawValue instanceof BigDecimal decimalValue) {
			return SortCriterionValue.numeric(decimalValue);
		}
		if (rawValue instanceof Number numberValue) {
			return SortCriterionValue.numeric(new BigDecimal(numberValue.toString()));
		}
		if (rawValue instanceof Instant instant) {
			return SortCriterionValue.temporal(instant.toEpochMilli());
		}
		if (rawValue instanceof OffsetDateTime offsetDateTime) {
			return SortCriterionValue.temporal(offsetDateTime.toInstant().toEpochMilli());
		}
		if (rawValue instanceof ZonedDateTime zonedDateTime) {
			return SortCriterionValue.temporal(zonedDateTime.toInstant().toEpochMilli());
		}
		if (rawValue instanceof LocalDateTime localDateTime) {
			return SortCriterionValue.temporal(localDateTime.toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
		}
		if (rawValue instanceof LocalDate localDate) {
			return SortCriterionValue.temporal(localDate.toEpochDay());
		}
		if (rawValue instanceof LocalTime localTime) {
			return SortCriterionValue.temporal(localTime.toNanoOfDay());
		}
		if (rawValue instanceof Boolean booleanValue) {
			return SortCriterionValue.booleanValue(booleanValue);
		}

		String text = rawValue.toString().trim();
		if (text.isEmpty()) {
			return null;
		}
		SortCriterionValue numericValue = parseNumeric(text);
		if (numericValue != null) {
			return numericValue;
		}
		SortCriterionValue temporalValue = parseIsoOrNumeric(text);
		if (temporalValue != null) {
			return temporalValue;
		}
		return SortCriterionValue.text(text);
	}

	private static SortCriterionValue parseNumeric(String text) {
		try {
			return SortCriterionValue.numeric(new BigDecimal(text));
		} catch (NumberFormatException ignored) {
			return null;
		}
	}

	private static SortCriterionValue parseIsoOrNumeric(String text) {
		List<java.util.function.Supplier<SortCriterionValue>> parsers = List.of(
				() -> SortCriterionValue.temporal(Instant.parse(text).toEpochMilli()),
				() -> SortCriterionValue.temporal(OffsetDateTime.parse(text).toInstant().toEpochMilli()),
				() -> SortCriterionValue.temporal(ZonedDateTime.parse(text).toInstant().toEpochMilli()),
				() -> SortCriterionValue.temporal(LocalDateTime.parse(text).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()),
				() -> SortCriterionValue.temporal(LocalDate.parse(text).toEpochDay()),
				() -> SortCriterionValue.temporal(LocalTime.parse(text).toNanoOfDay())
		);
		for (java.util.function.Supplier<SortCriterionValue> parser : parsers) {
			try {
				return parser.get();
			} catch (DateTimeParseException ignored) {
				// try next parser
			}
		}
		return null;
	}

	enum SortValueKind {
		NUMERIC,
		TEMPORAL,
		BOOLEAN,
		TEXT
	}

	record SortCriterionValue(SortValueKind kind, Comparable<?> value) implements Comparable<SortCriterionValue> {
		static SortCriterionValue numeric(BigDecimal value) {
			return new SortCriterionValue(SortValueKind.NUMERIC, value);
		}

		static SortCriterionValue temporal(long value) {
			return new SortCriterionValue(SortValueKind.TEMPORAL, value);
		}

		static SortCriterionValue booleanValue(boolean value) {
			return new SortCriterionValue(SortValueKind.BOOLEAN, value);
		}

		static SortCriterionValue text(String value) {
			return new SortCriterionValue(SortValueKind.TEXT, value);
		}

		@SuppressWarnings({"rawtypes", "unchecked"})
		@Override
		public int compareTo(SortCriterionValue other) {
			if (kind != other.kind) {
				return kind.compareTo(other.kind);
			}
			return ((Comparable) value).compareTo(other.value);
		}
	}
}


