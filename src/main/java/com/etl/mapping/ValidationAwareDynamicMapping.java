package com.etl.mapping;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.processor.ProcessorConfig;
import com.etl.processor.validation.ValidationIssue;
import com.etl.processor.validation.ValidationRuleEvaluator;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;

import java.util.List;
import java.util.Objects;

public class ValidationAwareDynamicMapping<I, O> implements ItemProcessor<I, O> {

	private static final Logger logger = LoggerFactory.getLogger(ValidationAwareDynamicMapping.class);

	private final ProcessorConfig.EntityMapping mapping;
	private final Class<O> targetClass;
	private final ValidationRuleEvaluator validationRuleEvaluator;
	private final FileIngestionRuntimeSupport fileIngestionRuntimeSupport;
	private final boolean rejectHandlingEnabled;

	public ValidationAwareDynamicMapping(ProcessorConfig.EntityMapping mapping,
	                                   Class<O> targetClass,
	                                   ValidationRuleEvaluator validationRuleEvaluator,
	                                   FileIngestionRuntimeSupport fileIngestionRuntimeSupport,
	                                   boolean rejectHandlingEnabled) {
		this.mapping = mapping;
		this.targetClass = targetClass;
		this.validationRuleEvaluator = validationRuleEvaluator;
		this.fileIngestionRuntimeSupport = fileIngestionRuntimeSupport;
		this.rejectHandlingEnabled = rejectHandlingEnabled;
	}

	@Override
	public O process(I input) throws Exception {
		List<ValidationIssue> issues = validationRuleEvaluator.evaluate(input, mapping);
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

		O output = ReflectionUtils.createInstance(targetClass);
		for (ProcessorConfig.FieldMapping fieldMapping : mapping.getFields()) {
			Object value = ReflectionUtils.getFieldValue(input, fieldMapping.getFrom());
			ReflectionUtils.setFieldValue(output, fieldMapping.getTo(), value);
		}
		return output;
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
		if (context == null || context.getStepExecution() == null || context.getStepExecution().getStepName() == null) {
			return "unknown-step";
		}
		return context.getStepExecution().getStepName();
	}

	private String normalize(String value) {
		return value == null ? null : value.trim();
	}
}
