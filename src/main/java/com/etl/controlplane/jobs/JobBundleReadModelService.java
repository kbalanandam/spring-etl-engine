package com.etl.controlplane.jobs;

import com.etl.config.job.JobConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
@Service
public class JobBundleReadModelService {

	private final Path jobsRoot;
	private final ObjectMapper yamlMapper;

	@Autowired
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

	public Optional<JobBundleConfigView> findBundleConfig(String jobKey) {
		return findBundle(jobKey)
				.flatMap(bundle -> {
					try {
						Path jobConfigPath = Path.of(bundle.jobConfigPath());
						String rawYaml = Files.readString(jobConfigPath);
						JobConfig jobConfig = yamlMapper.readValue(jobConfigPath.toFile(), JobConfig.class);

						String sourceConfigPath = resolveConfigPath(jobConfigPath,
								firstNonBlank(jobConfig.getSourceConfigPath(), extractPathHint(rawYaml, "sourceConfigPath")));
						String targetConfigPath = resolveConfigPath(jobConfigPath,
								firstNonBlank(jobConfig.getTargetConfigPath(), extractPathHint(rawYaml, "targetConfigPath")));
						String processorConfigPath = resolveConfigPath(jobConfigPath,
								firstNonBlank(jobConfig.getProcessorConfigPath(), extractPathHint(rawYaml, "processorConfigPath")));

						return Optional.of(new JobBundleConfigView(
								bundle.jobKey(),
								bundle.displayName(),
								bundle.jobConfigPath(),
								rawYaml,
								sourceConfigPath,
								readOptionalFile(sourceConfigPath),
								targetConfigPath,
								readOptionalFile(targetConfigPath),
								processorConfigPath,
								readOptionalFile(processorConfigPath)
						));
					} catch (Exception ignored) {
						return Optional.empty();
					}
				});
	}

	private String firstNonBlank(String primary, String secondary) {
		if (primary != null && !primary.isBlank()) {
			return primary;
		}
		if (secondary != null && !secondary.isBlank()) {
			return secondary;
		}
		return null;
	}

	private String extractPathHint(String rawYaml, String key) {
		if (rawYaml == null || rawYaml.isBlank()) {
			return null;
		}
		String[] lines = rawYaml.split("\\r?\\n");
		for (String line : lines) {
			String trimmed = line == null ? "" : line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			String prefix = key + ":";
			if (!trimmed.startsWith(prefix)) {
				continue;
			}
			String value = trimmed.substring(prefix.length()).trim();
			if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
				value = value.substring(1, value.length() - 1).trim();
			} else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
				value = value.substring(1, value.length() - 1).trim();
			}
			return value.isBlank() ? null : value;
		}
		return null;
	}

	private String resolveConfigPath(Path jobConfigPath, String configuredPath) {
		if (configuredPath == null || configuredPath.isBlank()) {
			return null;
		}
		Path configured = Path.of(configuredPath.trim());
		if (configured.isAbsolute()) {
			return configured.normalize().toString();
		}
		Path parent = jobConfigPath.getParent();
		if (parent == null) {
			return configured.normalize().toString();
		}
		return parent.resolve(configured).normalize().toString();
	}

	private String readOptionalFile(String absoluteOrRelativePath) {
		if (absoluteOrRelativePath == null || absoluteOrRelativePath.isBlank()) {
			return null;
		}
		try {
			Path path = Path.of(absoluteOrRelativePath);
			if (!Files.exists(path) || !Files.isRegularFile(path)) {
				return null;
			}
			return Files.readString(path);
		} catch (Exception ignored) {
			return null;
		}
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


