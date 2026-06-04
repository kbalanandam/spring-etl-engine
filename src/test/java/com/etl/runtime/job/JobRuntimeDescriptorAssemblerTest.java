package com.etl.runtime.job;

import com.etl.config.ColumnConfig;
import com.etl.config.job.JobConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.TargetWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobRuntimeDescriptorAssemblerTest {

	@Test
	void assemblesSelfExplanatoryDescriptorForMultiStepScenario() {
		SourceWrapper sourceWrapper = new SourceWrapper();
		sourceWrapper.setSources(List.of(
				csvSource("OrdersIn", "com.etl.model.source.orders", "input/orders.csv"),
				csvSource("OrdersValidated", "com.etl.model.source.validated", "target/orders-validated.csv")
		));

		TargetWrapper targetWrapper = new TargetWrapper();
		targetWrapper.setTargets(List.of(
				csvTarget("OrdersValidated", "com.etl.model.target.validated", "target/orders-validated.csv"),
				csvTarget("OrdersFinal", "com.etl.model.target.finalout", "target/orders-final.csv")
		));

		ProcessorConfig processorConfig = new ProcessorConfig();
		processorConfig.setType("default");
		processorConfig.setMappings(List.of(
				mapping("OrdersIn", "OrdersValidated", false),
				mapping("OrdersValidated", "OrdersFinal", true)
		));
		processorConfig.setRejectHandling(rejectHandling());

		JobConfig.JobStepConfig first = step("normalize-orders", "OrdersIn", "OrdersValidated");
		JobConfig.JobStepConfig second = step("publish-orders", "OrdersValidated", "OrdersFinal");

		JobRuntimeDescriptorAssembler assembler = new JobRuntimeDescriptorAssembler();
		JobRuntimeDescriptor descriptor = assembler.assemble(
				"orders-flow",
				"C:/scenarios/orders/job-config.yaml",
				JobRunMode.EXPLICIT_JOB,
				JobRecoveryPolicy.RERUN_FROM_START,
				new JobConfigPaths("source-config.yaml", "target-config.yaml", "processor-config.yaml"),
				List.of(first, second),
				sourceWrapper,
				targetWrapper,
				processorConfig
		);

		assertEquals("orders-flow", descriptor.scenarioName());
		assertEquals("orders-flow", descriptor.mainFlowName());
		assertEquals("default-subflow", descriptor.subFlowName());
		assertTrue(descriptor.implicitSubFlow());
		assertEquals(JobRecoveryPolicy.RERUN_FROM_START, descriptor.recoveryPolicy());
		assertTrue(descriptor.rerunsWholeScenarioOnFailure());
		assertTrue(descriptor.mainFlowContext().sharedLoggingContext());
		assertTrue(descriptor.mainFlowContext().sharedRecoveryContext());
		assertTrue(descriptor.supportsCrossSubFlowHandshake());
		assertTrue(descriptor.mainFlowContext().sharedSubFlowStatusRegistry());
		assertTrue(descriptor.mainFlowContext().supportsBlockingOnUpstreamFailure());
		assertEquals(List.of("normalize-orders-subflow", "publish-orders-subflow"), descriptor.mainFlowContext().visibleSubFlowNames());
		assertTrue(descriptor.mainFlowContext().hasHandoffs());
		assertTrue(descriptor.mainFlowContext().hasHandoffAlias("OrdersValidated"));
		assertEquals(2, descriptor.subFlowCount());
		JobSubFlowDescriptor firstSubFlow = descriptor.firstSubFlow();
		JobSubFlowDescriptor finalSubFlow = descriptor.finalSubFlow();
		assertNotNull(firstSubFlow);
		assertNotNull(finalSubFlow);
		assertEquals("normalize-orders-subflow", firstSubFlow.subFlowName());
		assertEquals(JobSubFlowExecutionStatus.READY, firstSubFlow.initialStatus());
		assertTrue(firstSubFlow.startsAfter(JobSubFlowExecutionStatus.COMPLETED));
		assertTrue(firstSubFlow.blocksOn(JobSubFlowExecutionStatus.FAILED));
		assertEquals("publish-orders-subflow", finalSubFlow.subFlowName());
		assertTrue(finalSubFlow.dependsOnSubFlow("normalize-orders-subflow"));
		assertTrue(finalSubFlow.control().requiresHandoffReady());
		assertTrue(finalSubFlow.consumesHandoffAlias("OrdersValidated"));
		assertTrue(finalSubFlow.producesHandoffAlias("OrdersFinal"));
		assertEquals(2, descriptor.stepCount());
		assertEquals(List.of("normalize-orders", "publish-orders"), descriptor.orderedStepNames());
		assertEquals(1, descriptor.stepLinks().size());
		assertEquals(JobStepLinkType.DATA_HANDOFF, descriptor.stepLinks().get(0).linkType());
		assertTrue(descriptor.stepLinks().get(0).control().requiresUpstreamStatus(JobSubFlowExecutionStatus.COMPLETED));
		assertTrue(descriptor.stepLinks().get(0).control().blocksOnUpstreamStatus(JobSubFlowExecutionStatus.FAILED));
		assertTrue(descriptor.stepLinks().get(0).control().requiresHandoffReady());
		JobStepDescriptor descriptorFirstStep = descriptor.firstStep();
		JobStepDescriptor descriptorFinalStep = descriptor.finalStep();
		assertNotNull(descriptorFirstStep);
		assertNotNull(descriptorFinalStep);
		assertEquals("normalize-orders", descriptorFirstStep.stepName());
		assertEquals("publish-orders", descriptorFinalStep.stepName());
		assertTrue(descriptor.flowSummary().contains("normalize-orders:OrdersIn->OrdersValidated"));

		JobStepDescriptor firstStep = descriptor.steps().get(0);
		assertEquals("OrdersIn", firstStep.sourceName());
		assertEquals("OrdersValidated", firstStep.targetName());
		assertEquals("default", firstStep.processorType());
		assertEquals(JobStepInputType.CONFIG_SOURCE, firstStep.input().type());
		assertEquals(JobStepOutputType.INTERMEDIATE_DATASET, firstStep.output().type());
		assertFalse(firstStep.output().finalScenarioOutput());
		assertEquals("com.etl.model.source.orders.OrdersIn", firstStep.modelDescriptor().sourceClassName());
		assertEquals("com.etl.model.target.validated.OrdersValidated", firstStep.modelDescriptor().targetWriteClassName());
		assertTrue(firstStep.validationSummary().passed());
		assertTrue(firstStep.flowSummary().contains("OrdersIn"));

		JobStepDescriptor secondStep = descriptor.steps().get(1);
		assertEquals(JobStepInputType.UPSTREAM_STEP_OUTPUT, secondStep.input().type());
		assertEquals("normalize-orders", secondStep.input().upstreamStepName());
		assertEquals("OrdersValidated", secondStep.input().inputAlias());
		assertEquals(JobStepOutputType.FINAL_OUTPUT, secondStep.output().type());
		assertTrue(secondStep.output().finalScenarioOutput());
		assertTrue(secondStep.executionHints().rejectHandlingEnabled());
		assertTrue(secondStep.executionHints().duplicateHandlingEnabled());
		assertTrue(secondStep.executionHints().orderedDuplicateSelection());
		assertNotNull(secondStep.executionHints().summary());
	}

	@Test
	void failsFastWhenResumeFromCheckpointPolicyIsAssembled() {
		SourceWrapper sourceWrapper = new SourceWrapper();
		sourceWrapper.setSources(List.of(csvSource("OrdersIn", "com.etl.model.source.orders", "input/orders.csv")));

		TargetWrapper targetWrapper = new TargetWrapper();
		targetWrapper.setTargets(List.of(csvTarget("OrdersOut", "com.etl.model.target.orders", "target/orders.csv")));

		ProcessorConfig processorConfig = new ProcessorConfig();
		processorConfig.setType("default");
		processorConfig.setMappings(List.of(mapping("OrdersIn", "OrdersOut", false)));

		JobRuntimeDescriptorAssembler assembler = new JobRuntimeDescriptorAssembler();
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> assembler.assemble(
				"orders-flow",
				"C:/scenarios/orders/job-config.yaml",
				JobRunMode.EXPLICIT_JOB,
				JobRecoveryPolicy.RESUME_FROM_CHECKPOINT,
				new JobConfigPaths("source-config.yaml", "target-config.yaml", "processor-config.yaml"),
				List.of(step("orders-step", "OrdersIn", "OrdersOut")),
				sourceWrapper,
				targetWrapper,
				processorConfig
		));

		assertTrue(exception.getMessage().contains("resume-from-checkpoint"));
	}

	private CsvSourceConfig csvSource(String sourceName, String packageName, String filePath) {
		return new CsvSourceConfig(sourceName, packageName, List.of(column("id"), column("status")), filePath, ",");
	}

	private CsvTargetConfig csvTarget(String targetName, String packageName, String filePath) {
		return new CsvTargetConfig(targetName, packageName, List.of(column("id"), column("status")), filePath, ",");
	}

	private ColumnConfig column(String name) {
		ColumnConfig column = new ColumnConfig();
		column.setName(name);
		column.setType("String");
		return column;
	}

	private ProcessorConfig.EntityMapping mapping(String sourceName, String targetName, boolean includeOrderedDuplicateRule) {
		ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
		mapping.setSource(sourceName);
		mapping.setTarget(targetName);
		ProcessorConfig.FieldMapping field = new ProcessorConfig.FieldMapping();
		field.setFrom("id");
		field.setTo("id");
		field.setRules(includeOrderedDuplicateRule ? List.of(duplicateRule()) : List.of());
		mapping.setFields(List.of(field));
		return mapping;
	}

	private ProcessorConfig.FieldRule duplicateRule() {
		ProcessorConfig.FieldRule rule = new ProcessorConfig.FieldRule();
		rule.setType("duplicate");
		ProcessorConfig.OrderByField orderByField = new ProcessorConfig.OrderByField();
		orderByField.setField("status");
		orderByField.setDirection("DESC");
		rule.setOrderBy(List.of(orderByField));
		return rule;
	}

	private ProcessorConfig.RejectHandling rejectHandling() {
		ProcessorConfig.RejectHandling rejectHandling = new ProcessorConfig.RejectHandling();
		rejectHandling.setEnabled(true);
		rejectHandling.setOutputPath("target/rejects");
		return rejectHandling;
	}

	private JobConfig.JobStepConfig step(String name, String source, String target) {
		JobConfig.JobStepConfig step = new JobConfig.JobStepConfig();
		step.setName(name);
		step.setSource(source);
		step.setTarget(target);
		return step;
	}
}


