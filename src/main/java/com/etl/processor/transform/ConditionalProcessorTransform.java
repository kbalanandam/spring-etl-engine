package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ConditionalProcessorTransform implements ProcessorFieldTransform {

	private final ExpressionParser expressionParser = new SpelExpressionParser();

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
				expressionParser.parseExpression(conditionalCase.getWhen().trim());
			} catch (ParseException e) {
				throw new IllegalStateException("FieldMapping transform 'conditional' has invalid SpEL at cases[" + i + "] for entity "
						+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + displayField(fieldMapping)
						+ "': " + e.getMessage(), e);
			}
		}
	}

	@Override
	public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
		return apply(value, transform, null);
	}

	@Override
	public Object apply(Object value, ProcessorConfig.FieldTransform transform, ProcessorTransformContext context) {
		for (ProcessorConfig.ConditionalCase conditionalCase : transform.getCases()) {
			if (conditionalCase != null && matches(conditionalCase, value, context)) {
				return conditionalCase.getThen();
			}
		}
		return transform.getDefaultValue() != null ? transform.getDefaultValue() : value;
	}

	private boolean matches(ProcessorConfig.ConditionalCase conditionalCase, Object value, ProcessorTransformContext context) {
		try {
			Expression expression = expressionParser.parseExpression(conditionalCase.getWhen().trim());
			Boolean matched = expression.getValue(buildEvaluationContext(value, context), Boolean.class);
			return Boolean.TRUE.equals(matched);
		} catch (ParseException | EvaluationException e) {
			throw new IllegalStateException("Failed to evaluate processor conditional transform for field '"
					+ displayField(context == null ? null : context.fieldMapping()) + "': " + e.getMessage(), e);
		}
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

