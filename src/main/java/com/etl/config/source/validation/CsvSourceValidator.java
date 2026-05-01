package com.etl.config.source.validation;

import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceConfig;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Validates CSV-specific source configuration and file-level CSV contract rules.
 *
 * <p>This validator stays on the source-validation path rather than the processor rule path. It
 * performs fail-fast checks for CSV archive configuration, file existence/readability, optional
 * exact header matching against configured fields, and empty-file/data-row policies before batch
 * reading begins.</p>
 */
@Component
public class CsvSourceValidator implements SourceValidator {

	@Override
	public boolean supports(SourceConfig sourceConfig) {
		return sourceConfig instanceof CsvSourceConfig;
	}

	@Override
	public void validate(SourceConfig sourceConfig, SourceValidationContext context) {
		CsvSourceConfig csvSourceConfig = (CsvSourceConfig) sourceConfig;
		validateArchive(csvSourceConfig);
		validateFileLevelRules(csvSourceConfig);
	}

	private void validateArchive(CsvSourceConfig csvSourceConfig) {
		CsvSourceConfig.ArchiveConfig archive = csvSourceConfig.getArchive();
		if (archive == null || !archive.isEnabled()) {
			return;
		}

		if (archive.getSuccessPath() == null || archive.getSuccessPath().isBlank()) {
			throw new IllegalArgumentException("archive.enabled=true requires a non-blank successPath.");
		}
	}

	private void validateFileLevelRules(CsvSourceConfig csvSourceConfig) {
		CsvSourceConfig.ValidationConfig validation = csvSourceConfig.getValidation();
		if (validation == null) {
			return;
		}

		validateValidationConfig(validation);

		if (csvSourceConfig.getFilePath() == null || csvSourceConfig.getFilePath().isBlank()) {
			throw new IllegalArgumentException("validation requires a non-blank filePath.");
		}
		if (csvSourceConfig.getDelimiter() == null || csvSourceConfig.getDelimiter().isBlank()) {
			throw new IllegalArgumentException("validation requires a non-blank delimiter.");
		}

		Path filePath = Path.of(csvSourceConfig.getFilePath());
		if (!Files.exists(filePath)) {
			throw new IllegalArgumentException("CSV file must exist for validation: " + filePath);
		}
		if (!Files.isRegularFile(filePath)) {
			throw new IllegalArgumentException("CSV file validation requires a regular file path: " + filePath);
		}
		if (!Files.isReadable(filePath)) {
			throw new IllegalArgumentException("CSV file must be readable for validation: " + filePath);
		}

		validateFileName(filePath, validation);

		validateFileContents(csvSourceConfig, validation, filePath);
	}

	private void validateValidationConfig(CsvSourceConfig.ValidationConfig validation) {
		compileIfConfigured(validation.getFileNamePattern());
		validateFailureAction(validation.getOnFailure(), validation.getRejectPath());
	}

	private void validateFileName(Path filePath, CsvSourceConfig.ValidationConfig validation) {
		String fileNamePattern = validation.getFileNamePattern();
		if (fileNamePattern == null || fileNamePattern.isBlank()) {
			return;
		}

		String fileName = filePath.getFileName() == null ? filePath.toString() : filePath.getFileName().toString();
		if (!Pattern.compile(fileNamePattern.trim()).matcher(fileName).matches()) {
			throw new IllegalArgumentException("CSV file name does not match validation.fileNamePattern. expectedPattern="
					+ fileNamePattern.trim() + " actual=" + fileName);
		}
	}

	private void validateFileContents(CsvSourceConfig csvSourceConfig,
	                                 CsvSourceConfig.ValidationConfig validation,
	                                 Path filePath) {
		try (BufferedReader reader = Files.newBufferedReader(filePath)) {
			String headerLine = reader.readLine();
			if (headerLine == null || headerLine.isBlank()) {
				if (validation.isRequireHeaderMatch()) {
					throw new IllegalArgumentException("CSV header row is required when validation.requireHeaderMatch=true.");
				}
				if (!validation.isAllowEmpty()) {
					throw new IllegalArgumentException("CSV file must contain at least one header row and one data row when validation.allowEmpty=false.");
				}
				return;
			}

			if (validation.isRequireHeaderMatch()) {
				validateHeader(csvSourceConfig, headerLine);
			}

			if (!validation.isAllowEmpty() && !hasDataRows(reader)) {
				throw new IllegalArgumentException("CSV file must contain at least one data row when validation.allowEmpty=false.");
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to read CSV file for validation: " + filePath, e);
		}
	}

	private void validateHeader(CsvSourceConfig csvSourceConfig, String headerLine) {
		List<String> expectedHeaders = csvSourceConfig.getFields().stream()
				.map(field -> normalizeHeaderValue(field.getName()))
				.toList();
		List<String> actualHeaders = Arrays.stream(headerLine.split(Pattern.quote(csvSourceConfig.getDelimiter()), -1))
				.map(this::normalizeHeaderValue)
				.toList();

		if (!actualHeaders.equals(expectedHeaders)) {
			throw new IllegalArgumentException("CSV header does not match configured fields. expected="
					+ expectedHeaders + " actual=" + actualHeaders);
		}
	}

	private boolean hasDataRows(BufferedReader reader) throws IOException {
		String line;
		while ((line = reader.readLine()) != null) {
			if (!line.isBlank()) {
				return true;
			}
		}
		return false;
	}

	private String normalizeHeaderValue(String value) {
		if (value == null) {
			return "";
		}
		return value.replace("\uFEFF", "").trim();
	}

	private void compileIfConfigured(String pattern) {
		if (pattern == null || pattern.isBlank()) {
			return;
		}
		try {
			Pattern.compile(pattern.trim());
		} catch (PatternSyntaxException e) {
			throw new IllegalArgumentException("validation.fileNamePattern must be a valid regex pattern.", e);
		}
	}

	private void validateFailureAction(String onFailure, String rejectPath) {
		if (onFailure == null || onFailure.isBlank()) {
			return;
		}

		String normalizedAction = onFailure.trim();
		if (!"failStep".equalsIgnoreCase(normalizedAction) && !"rejectFile".equalsIgnoreCase(normalizedAction)) {
			throw new IllegalArgumentException("validation.onFailure supports only failStep or rejectFile.");
		}
		if ("rejectFile".equalsIgnoreCase(normalizedAction) && (rejectPath == null || rejectPath.isBlank())) {
			throw new IllegalArgumentException("validation.onFailure=rejectFile requires a non-blank rejectPath.");
		}
	}
}

