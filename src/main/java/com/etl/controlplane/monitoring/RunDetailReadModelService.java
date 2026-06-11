package com.etl.controlplane.monitoring;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds a richer run drill-down view from structured scenario log evidence.
 */
@Service
public class RunDetailReadModelService {

	private final RunSummaryReadModelService runSummaryReadModelService;
	private final RunSummaryRegistry runSummaryRegistry;
	private final StructuredLogEventParser parser;
	private final RunDetailLogEvidenceAssembler logEvidenceAssembler;
	private final RunDetailStepReconciler stepReconciler;
	private final RunDetailArtifactReconciler artifactReconciler;

	@Autowired
	public RunDetailReadModelService(RunSummaryReadModelService runSummaryReadModelService,
	                                 RunSummaryRegistry runSummaryRegistry) {
		this(runSummaryReadModelService, runSummaryRegistry, new StructuredLogEventParser());
	}

	RunDetailReadModelService(RunSummaryReadModelService runSummaryReadModelService,
	                         StructuredLogEventParser parser) {
		this(runSummaryReadModelService, new InMemoryRunSummaryRegistry(), parser);
	}

	RunDetailReadModelService(RunSummaryReadModelService runSummaryReadModelService,
	                         RunSummaryRegistry runSummaryRegistry,
	                         StructuredLogEventParser parser) {
		this.runSummaryReadModelService = runSummaryReadModelService;
		this.runSummaryRegistry = runSummaryRegistry;
		this.parser = parser;
		this.logEvidenceAssembler = new RunDetailLogEvidenceAssembler(parser);
		this.stepReconciler = new RunDetailStepReconciler(runSummaryRegistry);
		this.artifactReconciler = new RunDetailArtifactReconciler(runSummaryRegistry);
	}

	public Optional<RunDetailView> findRunDetailByJobExecutionId(long jobExecutionId) {
		return runSummaryReadModelService.findRunByJobExecutionId(jobExecutionId)
				.map(summary -> buildDetail(summary, jobExecutionId));
	}

	private RunDetailView buildDetail(RunSummaryView runSummary, long jobExecutionId) {
		Path logPath = Path.of(runSummary.logPath());
		if (!Files.exists(logPath)) {
			List<StepRecordView> mergedSteps = reconcileWithPersistedStepRecords(jobExecutionId, List.of());
			List<ArtifactRecordView> mergedArtifacts = reconcileWithPersistedArtifactRecords(jobExecutionId, List.of(), mergedSteps);
			return new RunDetailView(
					runSummary,
					mergedSteps,
					mergedArtifacts,
					null,
					defaultEvidenceLinks(runSummary.logPath())
			);
		}

		RunDetailLogEvidenceAssembler.RunDetailLogEvidence logEvidence = logEvidenceAssembler.assemble(logPath, jobExecutionId);

		List<StepRecordView> mergedSteps = reconcileWithPersistedStepRecords(
				jobExecutionId,
				logEvidence.steps()
		);
		List<ArtifactRecordView> mergedArtifacts = reconcileWithPersistedArtifactRecords(
				jobExecutionId,
				logEvidence.artifacts(),
				mergedSteps
		);

		return new RunDetailView(
				runSummary,
				mergedSteps,
				mergedArtifacts,
				logEvidence.failureSummary(),
				buildEvidenceLinks(runSummary.logPath(),
						logEvidence.sawRunSummary(),
						logEvidence.sawStepEvent(),
						logEvidence.sawJobFailure(),
						logEvidence.runSummaryLine(),
						logEvidence.firstStepEventLine(),
						logEvidence.jobFailureLine())
		);
	}

	private List<StepRecordView> reconcileWithPersistedStepRecords(long jobExecutionId, List<StepRecordView> detailSteps) {
		return stepReconciler.reconcile(jobExecutionId, detailSteps);
	}

	private List<ArtifactRecordView> reconcileWithPersistedArtifactRecords(long jobExecutionId,
	                                                                     List<ArtifactRecordView> detailArtifacts,
	                                                                     List<StepRecordView> mergedSteps) {
		return artifactReconciler.reconcile(jobExecutionId, detailArtifacts, mergedSteps);
	}

	private List<EvidenceLinkView> defaultEvidenceLinks(String logPath) {
		List<EvidenceLinkView> links = new ArrayList<>();
		links.add(new EvidenceLinkView("Scenario log", logPath, "log-file"));
		links.add(new EvidenceLinkView("Scenario log unavailable", logPath, "log-file-missing"));
		return List.copyOf(links);
	}

	private List<EvidenceLinkView> buildEvidenceLinks(String logPath,
	                                                boolean sawRunSummary,
	                                                boolean sawStepEvent,
	                                                boolean sawJobFailure,
	                                                Integer runSummaryLine,
	                                                Integer stepEventLine,
	                                                Integer jobFailureLine) {
		List<EvidenceLinkView> links = new ArrayList<>();
		links.add(new EvidenceLinkView("Scenario log", logPath, "log-file"));
		if (sawRunSummary) {
			links.add(new EvidenceLinkView("Run summary", withLineAnchor(logPath, runSummaryLine), "run-summary"));
		}
		if (sawStepEvent) {
			links.add(new EvidenceLinkView("Step events", withLineAnchor(logPath, stepEventLine), "step-event"));
		}
		if (sawJobFailure) {
			links.add(new EvidenceLinkView("Job failure", withLineAnchor(logPath, jobFailureLine), "job-failure"));
		}
		return List.copyOf(links);
	}

	private String withLineAnchor(String logPath, Integer lineNumber) {
		if (lineNumber == null || lineNumber <= 0) {
			return logPath;
		}
		return logPath + "#L" + lineNumber;
	}

}


