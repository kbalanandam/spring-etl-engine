package com.etl.runtime;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.processor.validation.ValidationIssue;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides step-scoped runtime support for file-ingestion hardening concerns.
 *
 * <p>This component manages reject-file output, keep-first duplicate key tracking, and
 * success-only CSV source archiving for the active Spring Batch step. It also exposes execution
 * context keys used by listeners and reporting code to publish reject counts, reject output paths,
 * and archived source paths.</p>
 *
 * <p>Duplicate tracking in this class is the lightweight keep-first path used when a
 * {@code duplicate} rule does not request ordered winner selection. Ordered duplicate winner
 * selection uses separate duplicate resolver implementations.</p>
 */
@Component
public class FileIngestionRuntimeSupport {

	public static final String REJECTED_COUNT_KEY = "rejectedCount";
	public static final String REJECT_OUTPUT_PATH_KEY = "rejectOutputPath";
	public static final String ARCHIVED_SOURCE_PATH_KEY = "archivedSourcePath";

	private static final DateTimeFormatter ARCHIVE_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final String DEFAULT_ARCHIVE_NAME_PATTERN = "{originalName}-{timestamp}";

	private final Map<Long, RejectFileState> rejectStateByStepExecutionId = new ConcurrentHashMap<>();
	private final Map<Long, Map<String, Set<String>>> duplicateValuesByStepExecutionId = new ConcurrentHashMap<>();

	public void initializeStep(StepExecution stepExecution,
	                         SourceConfig sourceConfig,
	                         ProcessorConfig processorConfig,
	                         ProcessorConfig.EntityMapping entityMapping) {
		stepExecution.getExecutionContext().putInt(REJECTED_COUNT_KEY, 0);
		duplicateValuesByStepExecutionId.put(stepExecution.getId(), new ConcurrentHashMap<>());
		ProcessorConfig.RejectHandling rejectHandling = processorConfig.getRejectHandling();
		if (!isRejectHandlingEnabled(rejectHandling)) {
			return;
		}

		Path rejectPath = resolveRejectPath(rejectHandling.getOutputPath(), stepExecution.getStepName(), sourceConfig.getSourceName());
		stepExecution.getExecutionContext().putString(REJECT_OUTPUT_PATH_KEY, rejectPath.toString());
		rejectStateByStepExecutionId.put(stepExecution.getId(), new RejectFileState(rejectPath, entityMapping, rejectHandling.isIncludeReasonColumns()));
	}

	public boolean recordRejected(Object input, List<ValidationIssue> issues) {
		StepExecution stepExecution = currentStepExecution();
		if (stepExecution == null) {
			return false;
		}

		RejectFileState rejectFileState = rejectStateByStepExecutionId.get(stepExecution.getId());
		if (rejectFileState == null) {
			return false;
		}

		try {
			rejectFileState.write(input, issues);
			int currentRejectedCount = stepExecution.getExecutionContext().getInt(REJECTED_COUNT_KEY, 0);
			stepExecution.getExecutionContext().putInt(REJECTED_COUNT_KEY, currentRejectedCount + 1);
			return true;
		} catch (IOException e) {
			throw new IllegalStateException("Failed to write reject output for step '" + stepExecution.getStepName() + "'.", e);
		}
	}

	public ExitStatus completeStep(StepExecution stepExecution, SourceConfig sourceConfig) {
		RejectFileState rejectFileState = rejectStateByStepExecutionId.remove(stepExecution.getId());
		duplicateValuesByStepExecutionId.remove(stepExecution.getId());
		if (rejectFileState != null) {
			try {
				rejectFileState.close();
			} catch (IOException e) {
				throw new IllegalStateException("Failed to close reject output for step '" + stepExecution.getStepName() + "'.", e);
			}
		}

		if (ExitStatus.COMPLETED.equals(stepExecution.getExitStatus()) && sourceConfig instanceof CsvSourceConfig csvSourceConfig) {
			archiveSourceIfConfigured(stepExecution, csvSourceConfig);
		}

		return stepExecution.getExitStatus();
	}

	public boolean isRejectHandlingEnabled(ProcessorConfig.RejectHandling rejectHandling) {
		return rejectHandling != null && rejectHandling.isEnabled();
	}

	public boolean isDuplicateValue(String fieldName, Object value) {
		return isDuplicateValues(fieldName, List.of(value));
	}

	public boolean isDuplicateValues(String keyName, List<?> values) {
		StepExecution stepExecution = currentStepExecution();
		if (stepExecution == null || keyName == null || keyName.isBlank()) {
			return false;
		}

		String normalizedValue = normalizeDuplicateValues(values);
		if (normalizedValue == null) {
			return false;
		}

		Map<String, Set<String>> duplicateValuesByField = duplicateValuesByStepExecutionId.computeIfAbsent(
				stepExecution.getId(),
				ignored -> new ConcurrentHashMap<>()
		);
		Set<String> seenValues = duplicateValuesByField.computeIfAbsent(keyName, ignored -> ConcurrentHashMap.newKeySet());
		return !seenValues.add(normalizedValue);
	}

	private void archiveSourceIfConfigured(StepExecution stepExecution, CsvSourceConfig csvSourceConfig) {
		CsvSourceConfig.ArchiveConfig archiveConfig = csvSourceConfig.getArchive();
		if (archiveConfig == null || !archiveConfig.isEnabled()) {
			return;
		}

		Path sourcePath = Path.of(csvSourceConfig.getFilePath());
		Path archiveDirectory = Path.of(archiveConfig.getSuccessPath());
		String originalName = sourcePath.getFileName().toString();
		String namePattern = archiveConfig.getNamePattern() == null || archiveConfig.getNamePattern().isBlank()
				? DEFAULT_ARCHIVE_NAME_PATTERN
				: archiveConfig.getNamePattern().trim();
		String resolvedName = namePattern
				.replace("{originalName}", originalName)
				.replace("{timestamp}", ARCHIVE_TIMESTAMP_FORMATTER.format(LocalDateTime.now()));

		try {
			Files.createDirectories(archiveDirectory);
			Path archivedPath = archiveDirectory.resolve(resolvedName);
			Files.move(sourcePath, archivedPath, StandardCopyOption.REPLACE_EXISTING);
			stepExecution.getExecutionContext().putString(ARCHIVED_SOURCE_PATH_KEY, archivedPath.toString());
		} catch (IOException e) {
			throw new IllegalStateException("Failed to archive source file '" + sourcePath + "' for step '" + stepExecution.getStepName() + "'.", e);
		}
	}

	private StepExecution currentStepExecution() {
		var stepContext = StepSynchronizationManager.getContext();
		return stepContext == null ? null : stepContext.getStepExecution();
	}

	private String normalizeDuplicateValue(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof String stringValue && stringValue.isBlank()) {
			return null;
		}
		return value.toString();
	}

	private String normalizeDuplicateValues(List<?> values) {
		if (values == null || values.isEmpty()) {
			return null;
		}

		StringBuilder builder = new StringBuilder();
		for (Object value : values) {
			String normalizedPart = normalizeDuplicateValue(value);
			if (normalizedPart == null) {
				return null;
			}
			builder.append(normalizedPart.length())
					.append(':')
					.append(normalizedPart)
					.append('|');
		}
		return builder.toString();
	}

	private Path resolveRejectPath(String configuredPath, String stepName, String sourceName) {
		Path configured = Path.of(configuredPath);
		if (configuredPath.endsWith("/") || configuredPath.endsWith("\\") || Files.isDirectory(configured)) {
			String fileName = sanitize(stepName == null || stepName.isBlank() ? sourceName : stepName) + "-rejects.csv";
			return configured.resolve(fileName).normalize();
		}
		return configured.normalize();
	}

	private String sanitize(String value) {
		return Objects.requireNonNullElse(value, "step")
				.trim()
				.replaceAll("[^a-zA-Z0-9._-]", "-");
	}

	private static final class RejectFileState {

		private final Path rejectPath;
		private final ProcessorConfig.EntityMapping entityMapping;
		private final boolean includeReasonColumns;
		private BufferedWriter writer;
		private boolean headerWritten;

		private RejectFileState(Path rejectPath,
		                       ProcessorConfig.EntityMapping entityMapping,
		                       boolean includeReasonColumns) {
			this.rejectPath = rejectPath;
			this.entityMapping = entityMapping;
			this.includeReasonColumns = includeReasonColumns;
		}

		private void write(Object input, List<ValidationIssue> issues) throws IOException {
			if (writer == null) {
				Path parent = rejectPath.getParent();
				if (parent != null) {
					Files.createDirectories(parent);
				}
				writer = Files.newBufferedWriter(rejectPath, StandardCharsets.UTF_8);
			}
			if (!headerWritten) {
				writer.write(buildHeader());
				writer.newLine();
				headerWritten = true;
			}

			writer.write(buildRow(input, issues));
			writer.newLine();
			writer.flush();
		}

		private String buildHeader() {
			List<String> columns = new ArrayList<>();
			for (ProcessorConfig.FieldMapping fieldMapping : entityMapping.getFields()) {
				columns.add(fieldMapping.getTo());
			}
			if (includeReasonColumns) {
				columns.add("_rejectField");
				columns.add("_rejectRule");
				columns.add("_rejectMessage");
			}
			return String.join(",", columns);
		}

		private String buildRow(Object input, List<ValidationIssue> issues) {
			List<String> columns = new ArrayList<>();
			for (ProcessorConfig.FieldMapping fieldMapping : entityMapping.getFields()) {
				Object value = ReflectionUtils.getFieldValue(input, fieldMapping.getFrom());
				columns.add(escape(value));
			}
			if (includeReasonColumns) {
				columns.add(escape(joinIssues(issues, ValidationIssue::field)));
				columns.add(escape(joinIssues(issues, ValidationIssue::rule)));
				columns.add(escape(joinIssues(issues, ValidationIssue::message)));
			}
			return String.join(",", columns);
		}

		private String joinIssues(List<ValidationIssue> issues,
		                         java.util.function.Function<ValidationIssue, String> extractor) {
			return issues.stream().map(extractor).reduce((left, right) -> left + "|" + right).orElse("");
		}

		private String escape(Object value) {
			if (value == null) {
				return "";
			}
			String text = value.toString();
			if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
				return "\"" + text.replace("\"", "\"\"") + "\"";
			}
			return text;
		}

		private void close() throws IOException {
			if (writer != null) {
				writer.close();
			}
		}
	}
}
