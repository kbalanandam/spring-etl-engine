package com.etl.runtime;

import com.etl.common.util.ZipFileUtility;
import com.etl.common.util.ReflectionUtils;
import com.etl.config.source.FileArchiveConfig;
import com.etl.config.source.FileSourceConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.exception.RuntimeEtlException;
import com.etl.exception.ZipPackagingException;
import com.etl.processor.validation.ValidationIssue;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides step-scoped runtime support for file-ingestion hardening concerns.
 *
	 * <p>This component manages reject-file output, keep-first duplicate key tracking, and
	 * success-only file-source archiving for the active Spring Batch step. It also exposes execution
 * context keys used by listeners and reporting code to publish reject counts, reject output paths,
 * and archived source paths.</p>
 *
 * <p>Duplicate tracking in this class is the lightweight keep-first path used when a
 * {@code duplicate} rule does not request ordered winner selection. Ordered duplicate winner
 * selection uses separate duplicate resolver implementations.</p>
 *
 * <p><strong>Transition status:</strong> REUSE.</p>
 *
 * <p>This remains a shared runtime support component for reject handling, duplicate support,
 * and file-ingestion hardening concerns. Reuse it where possible instead of copying these
 * behaviors into job-specific flows.</p>
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
	private final FileSourceArtifactSupport fileSourceArtifactSupport;

	public FileIngestionRuntimeSupport() {
		this(new FileSourceArtifactSupport());
	}

	public FileIngestionRuntimeSupport(FileSourceArtifactSupport fileSourceArtifactSupport) {
		this.fileSourceArtifactSupport = fileSourceArtifactSupport;
	}

	/**
	 * Initializes step-scoped reject handling and duplicate tracking state.
	 *
	 * <p>This method is called once per step before processing begins. It creates the execution
	 * context evidence slots used later by listeners and summaries, then prepares any optional
	 * reject-file state for the active mapping.</p>
	 */
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
		Path writableRejectPath = resolveWritableZipSourcePath(rejectPath, ".csv");
		Path publishedRejectPath = resolvePublishedZipPath(rejectPath, rejectHandling.isPackageAsZip());
		stepExecution.getExecutionContext().putString(REJECT_OUTPUT_PATH_KEY, publishedRejectPath.toString());
		rejectStateByStepExecutionId.put(
				stepExecution.getId(),
				new RejectFileState(
						writableRejectPath,
						publishedRejectPath,
						entityMapping,
						rejectHandling.isIncludeReasonColumns(),
						rejectHandling.isPackageAsZip()
				)
		);
	}

	/**
	 * Records one rejected item for the current step when reject handling is enabled.
	 *
	 * <p>The method writes the rejected row to the active reject file, increments the step-level
	 * rejected count, and returns {@code true}. When no step context or reject-file state exists,
	 * it returns {@code false} so the caller can continue without treating that as an error.</p>
	 */
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
			throw new RuntimeEtlException("Failed to write reject output for step '" + stepExecution.getStepName() + "'.", e);
		}
	}

	/**
	 * Completes file-ingestion support for one step.
	 *
	 * <p>This closes any active reject output, clears duplicate tracking state, and archives the
	 * source file only when the step completed successfully and the source config opted into
	 * archive-on-success behavior.</p>
	 */
	public ExitStatus completeStep(StepExecution stepExecution, SourceConfig sourceConfig) {
		RejectFileState rejectFileState = rejectStateByStepExecutionId.remove(stepExecution.getId());
		duplicateValuesByStepExecutionId.remove(stepExecution.getId());
		if (rejectFileState != null) {
			try {
				rejectFileState.close();
				rejectFileState.packageAsZipIfConfigured();
			} catch (IOException e) {
				throw new RuntimeEtlException("Failed to close reject output for step '" + stepExecution.getStepName() + "'.", e);
			} catch (ZipPackagingException e) {
				throw new RuntimeEtlException("Failed to package reject output for step '" + stepExecution.getStepName() + "'.", e);
			}
		}

		if (ExitStatus.COMPLETED.equals(stepExecution.getExitStatus()) && sourceConfig instanceof FileSourceConfig fileSourceConfig) {
			archiveSourceIfConfigured(stepExecution, fileSourceConfig);
		}
		if (sourceConfig instanceof FileSourceConfig fileSourceConfig) {
			cleanupPreparedFileIfConfigured(fileSourceConfig);
		}

		return stepExecution.getExitStatus();
	}

	public boolean isRejectHandlingEnabled(ProcessorConfig.RejectHandling rejectHandling) {
		return rejectHandling != null && rejectHandling.isEnabled();
	}

	public boolean isDuplicateValue(String fieldName, Object value) {
		return isDuplicateValues(fieldName, List.of(value));
	}

	/**
	 * Performs keep-first duplicate detection for the current step.
	 *
	 * <p>This is the lightweight duplicate path used when processor rules request duplicate
	 * checking without ordered winner selection. Ordered winner selection uses the dedicated
	 * duplicate resolver implementations instead.</p>
	 */
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

	private void archiveSourceIfConfigured(StepExecution stepExecution, FileSourceConfig fileSourceConfig) {
		fileSourceArtifactSupport.validateArchiveConfiguration(fileSourceConfig);
		FileArchiveConfig archiveConfig = fileSourceConfig.getArchiveConfig();
		if (archiveConfig == null || !archiveConfig.isEnabled()) {
			return;
		}

		Path sourcePath = Path.of(fileSourceConfig.getFilePath()).toAbsolutePath().normalize();
		Path archiveDirectory = Path.of(archiveConfig.getSuccessPath());
		String originalName = sourcePath.getFileName().toString();
		String namePattern = archiveConfig.getNamePattern() == null || archiveConfig.getNamePattern().isBlank()
				? DEFAULT_ARCHIVE_NAME_PATTERN
				: archiveConfig.getNamePattern().trim();
		String resolvedName = namePattern
				.replace("{originalName}", originalName)
				.replace("{timestamp}", ARCHIVE_TIMESTAMP_FORMATTER.format(LocalDateTime.now()));
		Path archivedPath = fileSourceArtifactSupport.resolveArchivedPath(archiveDirectory, resolvedName, archiveConfig.isPackageAsZip());

		try {
			Files.createDirectories(archiveDirectory);
			if (archiveConfig.isPackageAsZip()) {
				archiveSourceAsZip(sourcePath, archivedPath);
			} else {
				Files.move(sourcePath, archivedPath, StandardCopyOption.REPLACE_EXISTING);
			}
			stepExecution.getExecutionContext().putString(ARCHIVED_SOURCE_PATH_KEY, archivedPath.toString());
		} catch (IOException | ZipPackagingException e) {
			throw new RuntimeEtlException("Failed to archive source file '" + sourcePath + "' for step '" + stepExecution.getStepName() + "'.", e);
		}
	}

	private void archiveSourceAsZip(Path sourcePath, Path archivedZipPath) throws IOException {
		Path stagedZipPath = archivedZipPath.resolveSibling(archivedZipPath.getFileName().toString() + ".part");
		String entryName = sourcePath.getFileName() == null ? "source" : sourcePath.getFileName().toString();

		try {
			Files.deleteIfExists(stagedZipPath);
			ZipFileUtility.packageSingleFile(sourcePath, stagedZipPath, entryName);
			promoteStagedArchive(stagedZipPath, archivedZipPath);
			try {
				Files.delete(sourcePath);
			} catch (IOException deleteException) {
				rollbackArchivedZipQuietly(archivedZipPath, deleteException);
				throw deleteException;
			}
		} finally {
			Files.deleteIfExists(stagedZipPath);
		}
	}

	private static void promoteStagedArchive(Path stagedZipPath, Path archivedZipPath) throws IOException {
		try {
			Files.move(stagedZipPath, archivedZipPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException e) {
			Files.move(stagedZipPath, archivedZipPath, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static void rollbackArchivedZipQuietly(Path archivedZipPath, IOException originalException) {
		try {
			Files.deleteIfExists(archivedZipPath);
		} catch (IOException rollbackException) {
			originalException.addSuppressed(rollbackException);
		}
	}

	private void cleanupPreparedFileIfConfigured(FileSourceConfig fileSourceConfig) {
		try {
			fileSourceArtifactSupport.cleanupPreparedFile(fileSourceConfig);
		} catch (IllegalArgumentException e) {
			throw new RuntimeEtlException("Failed to clean prepared source file for source '"
					+ fileSourceConfig.getFilePath() + "'.", e);
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

	private Path resolvePublishedZipPath(Path configuredPath, boolean packageAsZip) {
		if (!packageAsZip) {
			return configuredPath.normalize();
		}
		String fileName = configuredPath.getFileName() == null ? "rejects.csv" : configuredPath.getFileName().toString();
		if (fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
			return configuredPath.normalize();
		}
		return configuredPath.resolveSibling(fileName + ".zip").normalize();
	}

	private Path resolveWritableZipSourcePath(Path configuredPath, String defaultExtension) {
		String fileName = configuredPath.getFileName() == null ? "rejects" : configuredPath.getFileName().toString();
		if (!fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
			return configuredPath.normalize();
		}

		String writableFileName = fileName.substring(0, fileName.length() - 4);
		String normalizedExtension = defaultExtension == null ? "" : defaultExtension.toLowerCase(Locale.ROOT);
		if (!normalizedExtension.isBlank() && !writableFileName.toLowerCase(Locale.ROOT).endsWith(normalizedExtension)) {
			writableFileName = writableFileName + normalizedExtension;
		}
		return configuredPath.resolveSibling(writableFileName).normalize();
	}

	private static final class RejectFileState {

		/**
		 * Per-step reject-file writer state.
		 *
		 * <p>This helper lazily opens the reject output, writes one header row, and then appends one
		 * CSV row per rejected item using the active entity mapping as the output column contract.</p>
		 */

		private final Path rejectPath;
		private final Path publishedRejectPath;
		private final ProcessorConfig.EntityMapping entityMapping;
		private final boolean includeReasonColumns;
		private final boolean packageAsZip;
		private BufferedWriter writer;
		private boolean headerWritten;

		private RejectFileState(Path rejectPath,
		                       Path publishedRejectPath,
		                       ProcessorConfig.EntityMapping entityMapping,
		                       boolean includeReasonColumns,
		                       boolean packageAsZip) {
			this.rejectPath = rejectPath;
			this.publishedRejectPath = publishedRejectPath;
			this.entityMapping = entityMapping;
			this.includeReasonColumns = includeReasonColumns;
			this.packageAsZip = packageAsZip;
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

		private void packageAsZipIfConfigured() throws IOException {
			if (!packageAsZip || !Files.exists(rejectPath)) {
				return;
			}

			Path stagedZipPath = publishedRejectPath.resolveSibling(publishedRejectPath.getFileName().toString() + ".part");
			String entryName = rejectPath.getFileName() == null ? "rejects.csv" : rejectPath.getFileName().toString();
			try {
				Files.deleteIfExists(stagedZipPath);
				ZipFileUtility.packageSingleFile(rejectPath, stagedZipPath, entryName);
				promoteStagedArchive(stagedZipPath, publishedRejectPath);
				try {
					Files.delete(rejectPath);
				} catch (IOException deleteException) {
					rollbackArchivedZipQuietly(publishedRejectPath, deleteException);
					throw deleteException;
				}
			} finally {
				Files.deleteIfExists(stagedZipPath);
			}
		}
	}
}
