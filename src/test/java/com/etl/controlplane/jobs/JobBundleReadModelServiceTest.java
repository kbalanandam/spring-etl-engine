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

	private void writeJobConfig(Path path, String content) throws IOException {
		Files.createDirectories(path.getParent());
		Files.writeString(path, content);
	}
}


