package com.etl.config.source.validation;

import com.etl.config.source.FileArchiveConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.XmlSourceConfig;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Component
public class XmlSourceValidator implements SourceValidator {

	@Override
	public boolean supports(SourceConfig sourceConfig) {
		return sourceConfig instanceof XmlSourceConfig;
	}

	@Override
	public void validate(SourceConfig sourceConfig, SourceValidationContext context) {
		XmlSourceConfig xmlSourceConfig = (XmlSourceConfig) sourceConfig;
		validateArchive(xmlSourceConfig);
		String filePathValue = requireNonBlank(xmlSourceConfig.getFilePath(), "filePath");
		String expectedRootElement = requireNonBlank(xmlSourceConfig.getRootElement(), "rootElement");
		String expectedRecordElement = requireNonBlank(xmlSourceConfig.getRecordElement(), "recordElement");

		Path filePath = Path.of(filePathValue.trim());
		validateFile(filePath);
		validateRejectConfiguration(xmlSourceConfig.getValidation());
		validateFileNamePattern(xmlSourceConfig.getValidation(), filePath);
		validateXmlStructure(filePath, expectedRootElement.trim(), expectedRecordElement.trim());
	}

	private void validateArchive(XmlSourceConfig xmlSourceConfig) {
		FileArchiveConfig archive = xmlSourceConfig.getArchive();
		if (archive == null || !archive.isEnabled()) {
			return;
		}

		if (archive.getSuccessPath() == null || archive.getSuccessPath().isBlank()) {
			throw new IllegalArgumentException("archive.enabled=true requires a non-blank successPath.");
		}
	}

	private void validateFile(Path filePath) {
		if (!Files.exists(filePath)) {
			throw new IllegalArgumentException("XML file must exist for validation: " + filePath);
		}
		if (!Files.isRegularFile(filePath)) {
			throw new IllegalArgumentException("XML file validation requires a regular file path: " + filePath);
		}
		if (!Files.isReadable(filePath)) {
			throw new IllegalArgumentException("XML file must be readable for validation: " + filePath);
		}
	}

	private void validateRejectConfiguration(XmlSourceConfig.ValidationConfig validation) {
		if (validation == null || !"rejectFile".equalsIgnoreCase(normalize(validation.getOnFailure()))) {
			return;
		}

		if (validation.getRejectPath() == null || validation.getRejectPath().isBlank()) {
			throw new IllegalArgumentException("validation.onFailure=rejectFile requires a non-blank rejectPath.");
		}
	}

	private void validateFileNamePattern(XmlSourceConfig.ValidationConfig validation, Path filePath) {
		if (validation == null || validation.getFileNamePattern() == null || validation.getFileNamePattern().isBlank()) {
			return;
		}

		String fileName = filePath.getFileName() == null ? "" : filePath.getFileName().toString();
		if (Pattern.compile(validation.getFileNamePattern()).matcher(fileName).matches()) {
			return;
		}

		handleValidationFailure(validation, filePath,
				"XML fileNamePattern validation failed. pattern=" + validation.getFileNamePattern() + " fileName=" + fileName);
	}

	private void validateXmlStructure(Path filePath, String expectedRootElement, String expectedRecordElement) {
		XMLInputFactory factory = XMLInputFactory.newFactory();
		disableExternalEntityResolution(factory);

		boolean rootSeen = false;
		boolean recordSeen = false;

		try (InputStream inputStream = Files.newInputStream(filePath)) {
			XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
			try {
				while (reader.hasNext()) {
					int event = reader.next();
					if (event != XMLStreamConstants.START_ELEMENT) {
						continue;
					}

					String elementName = normalizeElementName(reader.getLocalName());
					if (!rootSeen) {
						rootSeen = true;
						if (!expectedRootElement.equals(elementName)) {
							throw new IllegalArgumentException("XML root element does not match configured rootElement. expected="
									+ expectedRootElement + " actual=" + elementName);
						}
						if (expectedRecordElement.equals(elementName)) {
							recordSeen = true;
						}
						continue;
					}

					if (expectedRecordElement.equals(elementName)) {
						recordSeen = true;
						break;
					}
				}
			} finally {
				reader.close();
			}
		} catch (XMLStreamException e) {
			throw new IllegalArgumentException("XML source must be well-formed and readable: " + filePath, e);
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to read XML file for validation: " + filePath, e);
		}

		if (!rootSeen) {
			throw new IllegalArgumentException("XML document must contain a root element: " + filePath);
		}
		if (!recordSeen) {
			throw new IllegalArgumentException("XML record element '" + expectedRecordElement
					+ "' was not found inside root '" + expectedRootElement + "'.");
		}
	}

	private void disableExternalEntityResolution(XMLInputFactory factory) {
		setIfSupported(factory, XMLInputFactory.SUPPORT_DTD, false);
		setIfSupported(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
	}

	private void setIfSupported(XMLInputFactory factory, String propertyName, boolean value) {
		try {
			factory.setProperty(propertyName, value);
		} catch (IllegalArgumentException ignored) {
			// Keep compatibility with XMLInputFactory implementations that do not expose the property.
		}
	}

	private String requireNonBlank(String value, String propertyName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("XML validation requires a non-blank " + propertyName + ".");
		}
		return value;
	}

	private String normalizeElementName(String value) {
		return value == null ? "" : value.trim();
	}

	private void handleValidationFailure(XmlSourceConfig.ValidationConfig validation, Path filePath, String message) {
		if (validation == null || !"rejectFile".equalsIgnoreCase(normalize(validation.getOnFailure()))) {
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
			throw new IllegalArgumentException("Unable to move invalid XML file to reject path: " + filePath, e);
		}
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
