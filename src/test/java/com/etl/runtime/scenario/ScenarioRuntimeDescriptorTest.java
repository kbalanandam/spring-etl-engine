package com.etl.runtime.scenario;

import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.enums.ModelFormat;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioRuntimeDescriptorTest {

	@Test
	void generatesSelfExplanatoryDefaultsForDisplayAndSummary() {
		ScenarioStepDescriptor step = new ScenarioStepDescriptor(
				"customers-step",
				0,
				"Customers",
				"CustomersXml",
				"default",
				null,
				null,
				ScenarioStepInputDescriptor.fromConfiguredSource("Customers"),
				ScenarioStepOutputDescriptor.configuredTarget("CustomersXml", true),
				new TestSourceConfig("Customers", "com.etl.model.source.customers"),
				new TestTargetConfig("CustomersXml", "com.etl.model.target.customers"),
				new com.etl.config.processor.ProcessorConfig.EntityMapping(),
				new ScenarioStepModelDescriptor(
						"com.etl.model.source.customers.Customers",
						"com.etl.model.target.customers.Customer",
						"com.etl.model.target.customers.CustomersXml",
						false,
						null,
						ScenarioModelResolutionMode.PREGENERATED,
						null
				),
				new ScenarioStepExecutionHints(ScenarioStepExecutionMode.UNRESOLVED, false, null, false, false, false, false, null),
				new ScenarioStepValidationSummary(true, true, true, true, List.of(), List.of(), null)
		);

		ScenarioRuntimeDescriptor descriptor = new ScenarioRuntimeDescriptor(
				"customer-load",
				null,
				null,
				null,
				true,
				ScenarioRecoveryPolicy.RERUN_FROM_START,
				null,
				null,
				null,
				"C:/tmp/job-config.yaml",
				ScenarioRunMode.EXPLICIT_JOB,
				new ScenarioConfigPaths("source-config.yaml", "target-config.yaml", "processor-config.yaml"),
				List.of(step),
				null,
				List.of(),
				new ScenarioValidationSummary(true, true, true, true, List.of(), List.of(), null)
		);

		assertEquals("customer-load", descriptor.displayName());
		assertEquals("customer-load-main-flow", descriptor.mainFlowName());
		assertEquals("default-subflow", descriptor.subFlowName());
		assertTrue(descriptor.implicitSubFlow());
		assertTrue(descriptor.rerunsWholeScenarioOnFailure());
		assertEquals(1, descriptor.subFlowCount());
		assertEquals("customers-step-subflow", descriptor.firstSubFlow().subFlowName());
		assertEquals(ScenarioSubFlowExecutionStatus.READY, descriptor.firstSubFlow().initialStatus());
		assertTrue(descriptor.firstSubFlow().startsAfter(ScenarioSubFlowExecutionStatus.COMPLETED));
		assertTrue(descriptor.firstSubFlow().blocksOn(ScenarioSubFlowExecutionStatus.FAILED));
		assertTrue(descriptor.mainFlowContext().sharedLoggingContext());
		assertTrue(descriptor.mainFlowContext().sharedRecoveryContext());
		assertTrue(descriptor.mainFlowContext().sharedSubFlowStatusRegistry());
		assertTrue(descriptor.mainFlowContext().summary().contains("supportsCrossSubFlowHandshake=false"));
		assertTrue(descriptor.flowSummary().contains("customers-step:Customers->CustomersXml"));
		assertEquals("customers-step", step.displayName());
		assertTrue(step.flowSummary().contains("Customers"));
		assertTrue(step.emitsFinalScenarioOutput());
	}

	@Test
	void rejectsDuplicateStepNamesInRuntimeDescriptor() {
		ScenarioStepDescriptor first = validStep("dup-step", 0);
		ScenarioStepDescriptor second = validStep("dup-step", 1);

		assertThrows(IllegalArgumentException.class, () -> new ScenarioRuntimeDescriptor(
				"dup-scenario",
				null,
				null,
				null,
				true,
				ScenarioRecoveryPolicy.RERUN_FROM_START,
				null,
				null,
				null,
				"C:/tmp/job-config.yaml",
				ScenarioRunMode.EXPLICIT_JOB,
				new ScenarioConfigPaths("source-config.yaml", "target-config.yaml", "processor-config.yaml"),
				List.of(first, second),
				null,
				List.of(),
				new ScenarioValidationSummary(true, true, true, true, List.of(), List.of(), null)
		));
	}

	private ScenarioStepDescriptor validStep(String stepName, int order) {
		return new ScenarioStepDescriptor(
				stepName,
				order,
				"Source" + order,
				"Target" + order,
				"default",
				null,
				null,
				ScenarioStepInputDescriptor.fromConfiguredSource("Source" + order),
				ScenarioStepOutputDescriptor.configuredTarget("Target" + order, order == 1),
				new TestSourceConfig("Source" + order, "com.etl.model.source.pkg"),
				new TestTargetConfig("Target" + order, "com.etl.model.target.pkg"),
				new com.etl.config.processor.ProcessorConfig.EntityMapping(),
				new ScenarioStepModelDescriptor(
						"com.etl.model.source.pkg.Source" + order,
						"com.etl.model.target.pkg.TargetProcessing" + order,
						"com.etl.model.target.pkg.Target" + order,
						false,
						null,
						ScenarioModelResolutionMode.LEGACY_BRIDGE,
						null
				),
				new ScenarioStepExecutionHints(ScenarioStepExecutionMode.UNRESOLVED, false, null, false, false, false, false, null),
				new ScenarioStepValidationSummary(true, true, true, true, List.of(), List.of(), null)
		);
	}

	private static final class TestSourceConfig extends SourceConfig {

		private TestSourceConfig(String sourceName, String packageName) {
			super(sourceName, packageName, List.of());
		}

		@Override
		public ModelFormat getFormat() {
			return ModelFormat.CSV;
		}

		@Override
		public int getRecordCount() {
			return 0;
		}
	}

	private static final class TestTargetConfig extends TargetConfig {

		private TestTargetConfig(String targetName, String packageName) {
			super(targetName, packageName, List.of());
		}

		@Override
		public ModelFormat getFormat() {
			return ModelFormat.CSV;
		}
	}
}


