package com.etl.controlplane.jobs;

import com.etl.config.job.JobConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads preserved job bundles and projects summary/readiness metadata.
 */
@Component
public class JobBundleReadModelService {

	private final Path jobsRoot;
	private final ObjectMapper yamlMapper;

	public JobBundleReadModelService(@Value("${controlplane.jobs.root:src/main/resources/config-jobs}") String jobsRoot) {
		this(Path.of(jobsRoot), new ObjectMapper(new YAMLFactory()));
	}

	JobBundleReadModelService(Path jobsRoot, ObjectMapper yamlMapper) {
		this.jobsRoot = jobsRoot;
		this.yamlMapper = yamlMapper;
	}

	public List<JobBundleSummaryView> listBundles() {
		if (!Files.exists(jobsRoot)) {
			return List.of();
		}

		List<JobBundleSummaryView> bundles = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(jobsRoot, 4)) {
			paths
					.filter(Files::isRegularFile)
					.filter(path -> "job-config.yaml".equalsIgnoreCase(path.getFileName().toString()))
					.forEach(path -> bundles.add(readBundle(path)));
		} catch (IOException ignored) {
			return List.of();
		}

		return bundles.stream()
				.sorted(Comparator.comparing(JobBundleSummaryView::jobKey, String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	public Optional<JobBundleSummaryView> findBundle(String jobKey) {
		if (jobKey == null || jobKey.isBlank()) {
			return Optional.empty();
		}
		return listBundles().stream()
				.filter(bundle -> bundle.jobKey().equalsIgnoreCase(jobKey.trim()))
				.findFirst();
	}

	private JobBundleSummaryView readBundle(Path jobConfigPath) {
		String key = jobConfigPath.getParent() == null ? "unknown" : jobConfigPath.getParent().getFileName().toString();
		List<String> messages = new ArrayList<>();
		String displayName = key;
		String readinessStatus = "READY";
		try {
			JobConfig jobConfig = yamlMapper.readValue(jobConfigPath.toFile(), JobConfig.class);
			if (jobConfig.getName() == null || jobConfig.getName().isBlank()) {
				readinessStatus = "INVALID";
				messages.add("job-config.yaml -> name is required");
			} else {
				displayName = jobConfig.getName().trim();
			}
			if (Boolean.FALSE.equals(jobConfig.getIsActive())) {
				readinessStatus = "INACTIVE";
				messages.add("job-config.yaml marks this bundle inactive");
			}
			if (jobConfig.getSteps() == null || jobConfig.getSteps().isEmpty()) {
				readinessStatus = "INVALID";
				messages.add("job-config.yaml -> steps must be non-empty");
			}
		} catch (Exception ex) {
			readinessStatus = "INVALID";
			messages.clear();
			messages.add("job-config.yaml parse error: " + ex.getMessage());
		}

		return new JobBundleSummaryView(
				key,
				displayName,
				jobConfigPath.toString(),
				readinessStatus,
				List.copyOf(messages)
		);
	}
}

