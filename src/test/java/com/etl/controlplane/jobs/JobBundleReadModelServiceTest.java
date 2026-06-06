package com.etl.controlplane.jobs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobBundleReadModelServiceTest {

	@TempDir
	Path tempDir;

	@Test
	void listsBundlesWithReadinessMetadata() throws IOException {
		writeJobConfig(tempDir.resolve("customer-load/job-config.yaml"), """
				name: customer-load
				sourceConfigPath: source-config.yaml
				targetConfigPath: target-config.yaml
				processorConfigPath: processor-config.yaml
				steps:
				  - name: step-1
				    source: Customers
				    target: CustomersOut
				""");
		writeJobConfig(tempDir.resolve("inactive-job/job-config.yaml"), """
				name: inactive-job
				isActive: false
				sourceConfigPath: source-config.yaml
				targetConfigPath: target-config.yaml
				processorConfigPath: processor-config.yaml
				steps:
				  - name: step-1
				    source: Customers
				    target: CustomersOut
				""");
		writeJobConfig(tempDir.resolve("broken-job/job-config.yaml"), "bad: [yaml");

		JobBundleReadModelService service = new JobBundleReadModelService(tempDir, new ObjectMapper(new YAMLFactory()));
		List<JobBundleSummaryView> bundles = service.listBundles();

		assertEquals(3, bundles.size());
		assertEquals("INVALID", bundles.get(0).readinessStatus());
		assertEquals("INACTIVE", bundles.get(2).readinessStatus());
	}

	@Test
	void findsBundleByJobKeyCaseInsensitive() throws IOException {
		writeJobConfig(tempDir.resolve("customer-load/job-config.yaml"), """
				name: Customer Load
				sourceConfigPath: source-config.yaml
				targetConfigPath: target-config.yaml
				processorConfigPath: processor-config.yaml
				steps:
				  - name: step-1
				    source: Customers
				    target: CustomersOut
				""");

		JobBundleReadModelService service = new JobBundleReadModelService(tempDir, new ObjectMapper(new YAMLFactory()));
		assertTrue(service.findBundle("CUSTOMER-LOAD").isPresent());
		assertEquals("Customer Load", service.findBundle("customer-load").orElseThrow().displayName());
	}

	@Test
	void findBundleConfigIncludesCompanionYamlPayloads() throws IOException {
		Path bundleDir = tempDir.resolve("csv-to-nested-xml");
		writeJobConfig(bundleDir.resolve("job-config.yaml"), """
				name: csv-to-nested-xml
				sourceConfigPath: source-config.yaml
				targetConfigPath: target-config.yaml
				processorConfigPath: processor-config.yaml
				steps:
				  - name: customers-step
				    source: CustomersCsv
				    target: CustomersNestedXml
				""");
		Files.writeString(bundleDir.resolve("source-config.yaml"), "sources:\n  - sourceName: CustomersCsv\n");
		Files.writeString(bundleDir.resolve("target-config.yaml"), "targets:\n  - targetName: CustomersNestedXml\n");
		Files.writeString(bundleDir.resolve("processor-config.yaml"), "mappings:\n  - source: CustomersCsv\n    target: CustomersNestedXml\n");

		JobBundleReadModelService service = new JobBundleReadModelService(tempDir, new ObjectMapper(new YAMLFactory()));
		JobBundleConfigView config = service.findBundleConfig("csv-to-nested-xml").orElseThrow();

		assertNotNull(config.sourceConfigPath());
		assertNotNull(config.targetConfigPath());
		assertNotNull(config.processorConfigPath());
		assertTrue(config.sourceConfigPath().endsWith("source-config.yaml"));
		assertTrue(config.targetConfigPath().endsWith("target-config.yaml"));
		assertTrue(config.processorConfigPath().endsWith("processor-config.yaml"));
		assertTrue(config.sourceRawYaml().contains("CustomersCsv"));
		assertTrue(config.targetRawYaml().contains("CustomersNestedXml"));
		assertTrue(config.processorRawYaml().contains("mappings"));
	}

	private void writeJobConfig(Path path, String content) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, content);
	}
}


