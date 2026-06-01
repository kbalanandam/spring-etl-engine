package com.etl.controlplane.schedules;

import com.etl.controlplane.jobs.JobBundleReadModelService;
import com.etl.controlplane.jobs.JobBundleSummaryView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Validates schedule definitions against selected-job readiness and expression/timezone contracts.
 */
@Component
public class ScheduleDefinitionValidator {

	private final JobBundleReadModelService jobBundleReadModelService;
	private final boolean enforceJobReadiness;

	@Autowired
	public ScheduleDefinitionValidator(JobBundleReadModelService jobBundleReadModelService) {
		this(jobBundleReadModelService, true);
	}

	private ScheduleDefinitionValidator(JobBundleReadModelService jobBundleReadModelService, boolean enforceJobReadiness) {
		this.jobBundleReadModelService = jobBundleReadModelService;
		this.enforceJobReadiness = enforceJobReadiness;
	}

	static ScheduleDefinitionValidator permissive() {
		return new ScheduleDefinitionValidator(null, false);
	}

	public void validateDefinition(String selectedJobKey, String expression, String timezone) {
		try {
			ScheduleExpressionSupport.parseCron(expression);
		} catch (IllegalArgumentException ex) {
			throw new ScheduleValidationException("invalid_expression", "Schedule expression is invalid.");
		}
		try {
			ScheduleExpressionSupport.parseZoneId(timezone);
		} catch (IllegalArgumentException ex) {
			throw new ScheduleValidationException("invalid_timezone", "Schedule timezone is invalid.");
		}

		if (!enforceJobReadiness) {
			return;
		}

		Optional<JobBundleSummaryView> bundle = jobBundleReadModelService.findBundle(selectedJobKey);
		if (bundle.isEmpty()) {
			throw new ScheduleValidationException("unknown_selected_job", "Selected job key does not exist.");
		}

		String readiness = bundle.get().readinessStatus();
		if (!"READY".equalsIgnoreCase(readiness)) {
			throw new ScheduleValidationException("selected_job_not_ready", "Selected job key is not ready for scheduling.");
		}
	}
}


