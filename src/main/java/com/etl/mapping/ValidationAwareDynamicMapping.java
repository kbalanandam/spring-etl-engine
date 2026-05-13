package com.etl.mapping;

import com.etl.config.processor.ProcessorConfig;
import com.etl.processor.transform.TransformEvaluator;
import com.etl.processor.validation.ValidationIssue;
import com.etl.processor.validation.ValidationRuleEvaluator;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Processor mapping adapter that combines transforms, processor validation rules, and optional
 * reject-file handling.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This is the primary processor path for steps that need rule evaluation. It first resolves
 * mapped field values, evaluates processor rules against the original or transformed view as
 * required, then either emits a generated target model, records a reject, or fails the step based
 * on the configured rule behavior.</p>
 *
 * @param <I> runtime input type
 * @param <O> generated target output type
 */
public class ValidationAwareDynamicMapping<I, O> implements ItemProcessor<I, O> {

	private static final Logger logger = LoggerFactory.getLogger(ValidationAwareDynamicMapping.class);

	private final ProcessorConfig.EntityMapping mapping;
	private final Class<O> targetClass;
	private final ValidationRuleEvaluator validationRuleEvaluator;
	private final FileIngestionRuntimeSupport fileIngestionRuntimeSupport;
	private final boolean rejectHandlingEnabled;
	private final MappedFieldValueResolver mappedFieldValueResolver;

	public ValidationAwareDynamicMapping(ProcessorConfig.EntityMapping mapping,
	                                   Class<O> targetClass,
	                                   TransformEvaluator transformEvaluator,
	                                   ValidationRuleEvaluator validationRuleEvaluator,
	                                   FileIngestionRuntimeSupport fileIngestionRuntimeSupport,
	                                   boolean rejectHandlingEnabled) {
		this.mapping = mapping;
		this.targetClass = targetClass;
		this.validationRuleEvaluator = validationRuleEvaluator;
		this.fileIngestionRuntimeSupport = fileIngestionRuntimeSupport;
		this.rejectHandlingEnabled = rejectHandlingEnabled;
		this.mappedFieldValueResolver = new MappedFieldValueResolver(transformEvaluator);
	}

	public ValidationAwareDynamicMapping(ProcessorConfig.EntityMapping mapping,
	                                   Class<O> targetClass,
	                                   ValidationRuleEvaluator validationRuleEvaluator,
	                                   FileIngestionRuntimeSupport fileIngestionRuntimeSupport,
	                                   boolean rejectHandlingEnabled) {
		this(mapping, targetClass, new TransformEvaluator(), validationRuleEvaluator, fileIngestionRuntimeSupport, rejectHandlingEnabled);
	}

	@Override
	public O process(@NonNull I input) throws Exception {
		Map<String, Object> resolvedValues = mappedFieldValueResolver.resolve(input, mapping);
		Object validationInput = hasTransforms() ? resolvedValues : input;
		List<ValidationIssue> issues = validationRuleEvaluator.evaluate(validationInput, mapping);
		if (!issues.isEmpty()) {
			String issueSummary = summarizeIssues(issues);
			if (shouldFailStep(issues) || !rejectHandlingEnabled) {
				String message = "Processor validation failed for mapping '" + mapping.getSource() + " -> "
						+ mapping.getTarget() + "' in step '" + currentStepName() + "': " + issueSummary;
				logger.error("PROCESS_VALIDATION event=record_rejected mode=failStep stepName={} source={} target={} issues={} inputType={}",
						currentStepName(),
						mapping.getSource(),
						mapping.getTarget(),
						issueSummary,
						input == null ? "null" : input.getClass().getName());
				throw new IllegalStateException(message);
			}

			logger.warn("PROCESS_VALIDATION event=record_rejected mode=rejectRecord stepName={} source={} target={} issues={} inputType={}",
					currentStepName(),
					mapping.getSource(),
					mapping.getTarget(),
					issueSummary,
					input == null ? "null" : input.getClass().getName());
			boolean recorded = fileIngestionRuntimeSupport.recordRejected(input, issues);
			if (!recorded) {
				throw new IllegalStateException("Processor validation rejected a record for mapping '"
						+ mapping.getSource() + " -> " + mapping.getTarget()
						+ "' but reject handling was not initialized for step '" + currentStepName() + "'. Issues: " + issueSummary);
			}
			return null;
		}

		return mappedFieldValueResolver.createOutput(targetClass, mapping, resolvedValues);
	}

	private boolean hasTransforms() {
		return mapping.getFields() != null && mapping.getFields().stream()
				.anyMatch(fieldMapping -> fieldMapping.getTransforms() != null && !fieldMapping.getTransforms().isEmpty());
	}

	private boolean shouldFailStep(List<ValidationIssue> issues) {
		for (ValidationIssue issue : issues) {
			ProcessorConfig.FieldRule matchingRule = findMatchingRule(issue);
			if (matchingRule != null && "failStep".equalsIgnoreCase(normalize(matchingRule.getOnFailure()))) {
				return true;
			}
		}
		return false;
	}

	private ProcessorConfig.FieldRule findMatchingRule(ValidationIssue issue) {
		if (issue == null || mapping.getFields() == null) {
			return null;
		}

		for (ProcessorConfig.FieldMapping fieldMapping : mapping.getFields()) {
			if (!matchesField(fieldMapping, issue.field()) || fieldMapping.getRules() == null) {
				continue;
			}
			for (ProcessorConfig.FieldRule rule : fieldMapping.getRules()) {
				if (matchesRule(rule, issue.rule())) {
					return rule;
				}
			}
		}
		return null;
	}

	private boolean matchesField(ProcessorConfig.FieldMapping fieldMapping, String issueField) {
		return Objects.equals(normalize(fieldMapping.getFrom()), normalize(issueField))
				|| Objects.equals(normalize(fieldMapping.getTo()), normalize(issueField));
	}

	private boolean matchesRule(ProcessorConfig.FieldRule rule, String issueRule) {
		return Objects.equals(normalize(rule == null ? null : rule.getType()), normalize(issueRule));
	}

	private String summarizeIssues(List<ValidationIssue> issues) {
		return issues.stream()
				.map(issue -> issue.field() + "[" + issue.rule() + "]: " + issue.message())
				.reduce((left, right) -> left + "; " + right)
				.orElse("unknown validation issue");
	}

	private String currentStepName() {
		var context = StepSynchronizationManager.getContext();
		if (context == null) {
			return "unknown-step";
		}
		return context.getStepExecution().getStepName();
	}

	private String normalize(String value) {
		return value == null ? null : value.trim();
	}
}
