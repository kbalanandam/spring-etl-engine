package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ExpressionProcessorTransform implements ProcessorFieldTransform {

	private final ExpressionParser expressionParser = new SpelExpressionParser();

	@Override
	public String getTransformType() {
		return "expression";
	}

	@Override
	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                                 ProcessorConfig.FieldMapping fieldMapping,
	                                 ProcessorConfig.FieldTransform transform) {
		String expression = normalizedExpression(transform);
		try {
			expressionParser.parseExpression(expression);
		} catch (ParseException e) {
			throw new IllegalStateException("FieldMapping transform 'expression' has invalid SpEL for entity "
					+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + displayField(fieldMapping)
					+ "': " + e.getMessage(), e);
		}
	}

	@Override
	public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
		return apply(value, transform, null);
	}

	@Override
	public Object apply(Object value, ProcessorConfig.FieldTransform transform, ProcessorTransformContext context) {
		String expression = normalizedExpression(transform);
		try {
			Expression compiledExpression = expressionParser.parseExpression(expression);
			StandardEvaluationContext evaluationContext = new StandardEvaluationContext(context == null ? null : context.input());
			evaluationContext.setVariable("input", context == null ? null : context.input());
			evaluationContext.setVariable("source", context == null ? null : context.input());
			evaluationContext.setVariable("value", value);
			evaluationContext.setVariable("resolved", context == null || context.resolvedValues() == null ? Map.of() : context.resolvedValues());
			return compiledExpression.getValue(evaluationContext);
		} catch (ParseException | EvaluationException e) {
			throw new IllegalStateException("Failed to evaluate processor expression transform for field '"
					+ displayField(context == null ? null : context.fieldMapping()) + "': " + e.getMessage(), e);
		}
	}

	private String normalizedExpression(ProcessorConfig.FieldTransform transform) {
		if (transform == null || transform.getExpression() == null || transform.getExpression().isBlank()) {
			throw new IllegalStateException("FieldMapping transform 'expression' requires a non-blank 'expression' value.");
		}
		return transform.getExpression().trim();
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


