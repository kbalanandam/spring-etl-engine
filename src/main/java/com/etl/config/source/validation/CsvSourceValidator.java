package com.etl.config.source.validation;

import com.etl.config.source.FileArchiveConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceConfig;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

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
		FileArchiveConfig archive = csvSourceConfig.getArchive();
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

		if (csvSourceConfig.getFilePath() == null || csvSourceConfig.getFilePath().isBlank()) {
			throw new IllegalArgumentException("validation requires a non-blank filePath.");
		}
		if (csvSourceConfig.getDelimiter() == null || csvSourceConfig.getDelimiter().isBlank()) {
			throw new IllegalArgumentException("validation requires a non-blank delimiter.");
		}
		if (!csvSourceConfig.isSkipHeader() && validation.isRequireHeaderMatch()) {
			throw new IllegalArgumentException(
					"validation.requireHeaderMatch=true requires skipHeader=true for CSV sources. "
							+ "Disable header matching or enable header skipping before runtime."
			);
		}

		validateRejectConfiguration(validation);

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
		validateFileNamePattern(validation, filePath);

		validateFileContents(csvSourceConfig, validation, filePath);
	}

	private void validateRejectConfiguration(CsvSourceConfig.ValidationConfig validation) {
		if (!"rejectFile".equalsIgnoreCase(normalize(validation.getOnFailure()))) {
			return;
		}

		if (validation.getRejectPath() == null || validation.getRejectPath().isBlank()) {
			throw new IllegalArgumentException("validation.onFailure=rejectFile requires a non-blank rejectPath.");
		}
	}

	private void validateFileNamePattern(CsvSourceConfig.ValidationConfig validation, Path filePath) {
		if (validation.getFileNamePattern() == null || validation.getFileNamePattern().isBlank()) {
			return;
		}

		String fileName = filePath.getFileName() == null ? "" : filePath.getFileName().toString();
		if (Pattern.compile(validation.getFileNamePattern()).matcher(fileName).matches()) {
			return;
		}

		handleValidationFailure(validation, filePath,
				"CSV fileNamePattern validation failed. pattern=" + validation.getFileNamePattern() + " fileName=" + fileName);
	}

	private void validateFileContents(CsvSourceConfig csvSourceConfig,
	                                 CsvSourceConfig.ValidationConfig validation,
	                                 Path filePath) {
		try (BufferedReader reader = Files.newBufferedReader(filePath)) {
			String firstLine = reader.readLine();
			if (firstLine == null || firstLine.isBlank()) {
				if (csvSourceConfig.isSkipHeader() && validation.isRequireHeaderMatch()) {
					handleValidationFailure(validation, filePath,
							"CSV header row is required when validation.requireHeaderMatch=true.");
				}
				if (!validation.isAllowEmpty()) {
					handleValidationFailure(validation, filePath, csvSourceConfig.isSkipHeader()
							? "CSV file must contain at least one header row and one data row when validation.allowEmpty=false."
							: "CSV file must contain at least one data row when validation.allowEmpty=false.");
				}
				return;
			}

			if (csvSourceConfig.isSkipHeader()) {
				if (validation.isRequireHeaderMatch()) {
					validateHeader(csvSourceConfig, validation, filePath, firstLine);
				}

				if (!validation.isAllowEmpty() && !hasDataRows(reader)) {
					handleValidationFailure(validation, filePath,
							"CSV file must contain at least one data row when validation.allowEmpty=false.");
				}
			}
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to read CSV file for validation: " + filePath, e);
		}
	}

	private void validateHeader(CsvSourceConfig csvSourceConfig,
	                           CsvSourceConfig.ValidationConfig validation,
	                           Path filePath,
	                           String headerLine) {
		List<String> expectedHeaders = csvSourceConfig.getFields().stream()
				.map(field -> normalizeHeaderValue(field.getName()))
				.toList();
		List<String> actualHeaders = Arrays.stream(headerLine.split(Pattern.quote(csvSourceConfig.getDelimiter()), -1))
				.map(this::normalizeHeaderValue)
				.toList();

		if (!actualHeaders.equals(expectedHeaders)) {
			handleValidationFailure(validation, filePath,
					"CSV header does not match configured fields. expected=" + expectedHeaders + " actual=" + actualHeaders);
		}
	}

	private void handleValidationFailure(CsvSourceConfig.ValidationConfig validation, Path filePath, String message) {
		if (!"rejectFile".equalsIgnoreCase(normalize(validation.getOnFailure()))) {
			throw new IllegalArgumentException(message);
		}

		Path rejectedPath = moveToRejectPath(filePath, validation.getRejectPath());
		throw new IllegalArgumentException(message + "; moved to reject path: " + rejectedPath);
	}

	private Path moveToRejectPath(Path filePath, String rejectPathValue) {
		try {
			Path rejectDirectory = Path.of(rejectPathValue.trim()).toAbsolutePath().normalize();
			Files.createDirectories(rejectDirectory);
			Path destination = rejectDirectory.resolve(filePath.getFileName());
			Files.move(filePath, destination, StandardCopyOption.REPLACE_EXISTING);
			return destination;
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to move invalid CSV file to reject path: " + filePath, e);
		}
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
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
}

