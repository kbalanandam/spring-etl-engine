package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConditionalProcessorTransform implements ProcessorFieldTransform {

	private static final Logger log = LoggerFactory.getLogger(ConditionalProcessorTransform.class);
	private static final int MAX_EXPRESSION_CACHE_SIZE = 1024;
	private static final int MAX_LOGGED_EXPRESSION_LENGTH = 160;
	private final ExpressionParser expressionParser = new SpelExpressionParser();
	private final Map<String, Expression> expressionCache = Collections.synchronizedMap(
			new LinkedHashMap<>(128, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Expression> eldest) {
					boolean shouldEvict = size() > MAX_EXPRESSION_CACHE_SIZE;
					if (shouldEvict && log.isTraceEnabled()) {
						log.trace("PROCESSOR_TRANSFORM event=conditional_expression_cache_evict expression={} cacheSize={} maxSize={}",
								abbreviateExpression(eldest.getKey()), size(), MAX_EXPRESSION_CACHE_SIZE);
					}
					return shouldEvict;
				}
			}
	);

	@Override
	public String getTransformType() {
		return "conditional";
	}

	@Override
	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                                 ProcessorConfig.FieldMapping fieldMapping,
	                                 ProcessorConfig.FieldTransform transform) {
		List<ProcessorConfig.ConditionalCase> cases = transform == null ? null : transform.getCases();
		if (cases == null || cases.isEmpty()) {
			throw new IllegalStateException("FieldMapping transform 'conditional' requires a non-empty 'cases' list for entity "
					+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + displayField(fieldMapping) + "'.");
		}

		for (int i = 0; i < cases.size(); i++) {
			ProcessorConfig.ConditionalCase conditionalCase = cases.get(i);
			if (conditionalCase == null || conditionalCase.getWhen() == null || conditionalCase.getWhen().isBlank()) {
				throw new IllegalStateException("FieldMapping transform 'conditional' requires non-blank 'cases[" + i + "].when' for entity "
						+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + displayField(fieldMapping) + "'.");
			}
			try {
				resolveExpression(conditionalCase.getWhen());
			} catch (ParseException e) {
				throw new IllegalStateException("FieldMapping transform 'conditional' has invalid SpEL at cases[" + i + "] for entity "
						+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + displayField(fieldMapping)
						+ "': " + e.getMessage(), e);
			}
		}
	}
	/**
	 * Applies conditional transformation using SpEL expressions.
	 * Available SpEL variables:
	 * - #input: the entire source record
	 * - #source: alias for #input
	 * - #value: the current field value
	 * - #resolved: map of previously resolved field values in this mapping
	 */
	@Override
	public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
		return apply(value, transform, null);
	}

	@Override
	public Object apply(Object value, ProcessorConfig.FieldTransform transform, ProcessorTransformContext context) {
		String fieldName = displayField(context == null ? null : context.fieldMapping());
		List<ProcessorConfig.ConditionalCase> conditionalCases = transform.getCases();
		for (int index = 0; index < conditionalCases.size(); index++) {
			ProcessorConfig.ConditionalCase conditionalCase = conditionalCases.get(index);
			if (conditionalCase != null && matches(conditionalCase, value, context)) {
				if (log.isDebugEnabled()) {
					log.debug("PROCESSOR_TRANSFORM event=conditional_case_matched field={} caseIndex={} expression={}", fieldName, index,
							abbreviateExpression(conditionalCase.getWhen()));
				}
				return conditionalCase.getThen();
			}
		}
		if (log.isDebugEnabled()) {
			log.debug("PROCESSOR_TRANSFORM event=conditional_default_applied field={} caseCount={} hasDefault={} ", fieldName,
					conditionalCases.size(), transform.getDefaultValue() != null);
		}
		return transform.getDefaultValue() != null ? transform.getDefaultValue() : value;
	}

	private boolean matches(ProcessorConfig.ConditionalCase conditionalCase, Object value, ProcessorTransformContext context) {
		try {
			Expression expression = resolveExpression(conditionalCase.getWhen());
			Boolean matched = expression.getValue(buildEvaluationContext(value, context), Boolean.class);
			return Boolean.TRUE.equals(matched);
		} catch (ParseException | EvaluationException e) {
			String fieldName = displayField(context == null ? null : context.fieldMapping());
			log.warn("PROCESSOR_TRANSFORM event=conditional_evaluation_failed field={} expression={} error={}", fieldName,
					abbreviateExpression(conditionalCase.getWhen()), e.getMessage());
			throw new ProcessorTransformEvaluationException("Failed to evaluate processor conditional transform for field '"
					+ fieldName + "': " + e.getMessage(), e);
		}
	}

	private Expression resolveExpression(String rawExpression) {
		String expressionText = rawExpression == null ? "" : rawExpression.trim();
		synchronized (expressionCache) {
			Expression cached = expressionCache.get(expressionText);
			if (cached != null) {
				if (log.isTraceEnabled()) {
					log.trace("PROCESSOR_TRANSFORM event=conditional_expression_cache_hit expression={}", abbreviateExpression(expressionText));
				}
				return cached;
			}

			Expression parsedExpression = expressionParser.parseExpression(expressionText);
			expressionCache.put(expressionText, parsedExpression);
			if (log.isTraceEnabled()) {
				log.trace("PROCESSOR_TRANSFORM event=conditional_expression_cache_miss expression={} cacheSize={}",
						abbreviateExpression(expressionText), expressionCache.size());
			}
			return parsedExpression;
		}
	}

	private String abbreviateExpression(String expressionText) {
		if (expressionText == null) {
			return "";
		}
		String trimmed = expressionText.trim();
		if (trimmed.length() <= MAX_LOGGED_EXPRESSION_LENGTH) {
			return trimmed;
		}
		return trimmed.substring(0, MAX_LOGGED_EXPRESSION_LENGTH - 3) + "...";
	}

	private StandardEvaluationContext buildEvaluationContext(Object value, ProcessorTransformContext context) {
		StandardEvaluationContext evaluationContext = new StandardEvaluationContext(context == null ? null : context.input());
		evaluationContext.setVariable("input", context == null ? null : context.input());
		evaluationContext.setVariable("source", context == null ? null : context.input());
		evaluationContext.setVariable("value", value);
		evaluationContext.setVariable("resolved", context == null || context.resolvedValues() == null ? Map.of() : context.resolvedValues());
		return evaluationContext;
	}

	private String displayField(ProcessorConfig.FieldMapping fieldMapping) {
		if (fieldMapping == null) {
			return "unknown";
		}
		if (fieldMapping.getTo() != null && !fieldMapping.getTo().isBlank()) {
			return fieldMapping.getTo().trim();
		}
		if (fieldMapping.getFrom() != null && !fieldMapping.getFrom().isBlank()) {
			return fieldMapping.getFrom().trim();
		}
		return "unknown";
	}
}

