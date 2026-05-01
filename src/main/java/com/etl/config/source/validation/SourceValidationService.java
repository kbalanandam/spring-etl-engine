package com.etl.config.source.validation;

import com.etl.config.exception.ConfigException;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Dispatches active source configurations to the applicable source validators.
 *
 * <p>This service is the entry point for the source-validation SPI during runtime config loading.
 * It iterates through configured sources, invokes validators that support each source type, and
 * wraps validator {@link IllegalArgumentException} failures in scenario-aware {@link ConfigException}
 * messages so operators see which source config failed and why.</p>
 *
 * <p><strong>Transition status:</strong> REUSE.</p>
 *
 * <p>This remains a valid shared source-validation boundary for the next architecture.
 * Reuse it for source-native validation concerns instead of moving those checks into
 * processor rules or job-specific orchestration code.</p>
 */
@Component
public class SourceValidationService {

	private static final Logger logger = LoggerFactory.getLogger(SourceValidationService.class);
	private static final DateTimeFormatter FILE_REJECT_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final List<SourceValidator> validators;

	public SourceValidationService() {
		this(List.of(
				new CsvSourceValidator(),
				new XmlSourceValidator(),
				new RelationalSourceValidator()
		));
	}

	@Autowired
	public SourceValidationService(List<SourceValidator> validators) {
		this.validators = validators == null ? List.of() : List.copyOf(validators);
	}

	public void validateSelectedSources(SourceWrapper sourceWrapper, SourceValidationContext context) {
		if (sourceWrapper == null || sourceWrapper.getSources() == null) {
			return;
		}

		for (SourceConfig sourceConfig : sourceWrapper.getSources()) {
			validate(sourceConfig, context);
		}
	}

	public void validate(SourceConfig sourceConfig, SourceValidationContext context) {
		for (SourceValidator validator : validators) {
			if (!validator.supports(sourceConfig)) {
				continue;
			}

			try {
				validator.validate(sourceConfig, context);
			} catch (IllegalArgumentException e) {
				RejectedSourceFile rejectedSourceFile = rejectFileIfConfigured(sourceConfig, context, e);
				String rejectionSuffix = rejectedSourceFile == null ? "" : " Source file moved to reject path '" + rejectedSourceFile.rejectedPath() + "'.";
				throw new ConfigException("Invalid source validation configuration for scenario '"
						+ context.scenarioName() + "' in " + context.sourceConfigPath() + " (source='"
						+ defaultName(sourceConfig.getSourceName()) + "'): " + e.getMessage() + rejectionSuffix, e);
			}
		}
	}

	private RejectedSourceFile rejectFileIfConfigured(SourceConfig sourceConfig,
	                                               SourceValidationContext context,
	                                               IllegalArgumentException validationFailure) {
		String onFailure = sourceValidationOnFailure(sourceConfig);
		if (!"rejectFile".equalsIgnoreCase(onFailure)) {
			return null;
		}

		String configuredFilePath = sourceFilePath(sourceConfig);
		String configuredRejectPath = sourceValidationRejectPath(sourceConfig);
		if (configuredFilePath == null || configuredFilePath.isBlank() || configuredRejectPath == null || configuredRejectPath.isBlank()) {
			return null;
		}

		Path sourcePath = Path.of(configuredFilePath.trim());
		if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
			return null;
		}

		Path rejectDirectory = Path.of(configuredRejectPath.trim());
		try {
			Files.createDirectories(rejectDirectory);
			Path rejectedPath = rejectDirectory.resolve(rejectedFileName(sourcePath));
			Files.move(sourcePath, rejectedPath, StandardCopyOption.REPLACE_EXISTING);
			logger.warn("SOURCE_VALIDATION event=file_rejected scenario={} source={} sourceFile={} rejectedFile={} reason={}",
					context.scenarioName(),
					defaultName(sourceConfig.getSourceName()),
					sourcePath,
					rejectedPath,
					validationFailure.getMessage());
			return new RejectedSourceFile(sourcePath, rejectedPath);
		} catch (IOException ioException) {
			throw new ConfigException("Failed to reject source file '" + sourcePath + "' for scenario '"
					+ context.scenarioName() + "'.", ioException);
		}
	}

	private String rejectedFileName(Path sourcePath) {
		String originalName = sourcePath.getFileName() == null ? "source-file" : sourcePath.getFileName().toString();
		return FILE_REJECT_TIMESTAMP_FORMATTER.format(LocalDateTime.now()) + "-" + originalName;
	}

	private String sourceValidationOnFailure(SourceConfig sourceConfig) {
		if (sourceConfig instanceof CsvSourceConfig csvSourceConfig && csvSourceConfig.getValidation() != null) {
			return csvSourceConfig.getValidation().getOnFailure();
		}
		if (sourceConfig instanceof XmlSourceConfig xmlSourceConfig && xmlSourceConfig.getValidation() != null) {
			return xmlSourceConfig.getValidation().getOnFailure();
		}
		return null;
	}

	private String sourceValidationRejectPath(SourceConfig sourceConfig) {
		if (sourceConfig instanceof CsvSourceConfig csvSourceConfig && csvSourceConfig.getValidation() != null) {
			return csvSourceConfig.getValidation().getRejectPath();
		}
		if (sourceConfig instanceof XmlSourceConfig xmlSourceConfig && xmlSourceConfig.getValidation() != null) {
			return xmlSourceConfig.getValidation().getRejectPath();
		}
		return null;
	}

	private String sourceFilePath(SourceConfig sourceConfig) {
		if (sourceConfig instanceof CsvSourceConfig csvSourceConfig) {
			return csvSourceConfig.getFilePath();
		}
		if (sourceConfig instanceof XmlSourceConfig xmlSourceConfig) {
			return xmlSourceConfig.getFilePath();
		}
		return null;
	}

	private String defaultName(String configuredName) {
		return configuredName == null || configuredName.isBlank() ? "unnamed" : configuredName.trim();
	}

	private record RejectedSourceFile(Path sourcePath, Path rejectedPath) {
	}
}


