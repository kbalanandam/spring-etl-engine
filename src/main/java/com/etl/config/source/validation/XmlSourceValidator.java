package com.etl.config.source.validation;

import com.etl.config.source.SourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.runtime.FileSourceArtifactSupport;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.SAXException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Component
public class XmlSourceValidator implements SourceValidator {

	private final FileSourceArtifactSupport fileSourceArtifactSupport = new FileSourceArtifactSupport();

	@Override
	public boolean supports(SourceConfig sourceConfig) {
		return sourceConfig instanceof XmlSourceConfig;
	}

	@Override
	public void validate(SourceConfig sourceConfig, SourceValidationContext context) {
		XmlSourceConfig xmlSourceConfig = (XmlSourceConfig) sourceConfig;
		fileSourceArtifactSupport.validateUnzipConfiguration(xmlSourceConfig);
		fileSourceArtifactSupport.validateArchiveConfiguration(xmlSourceConfig);
		String filePathValue = requireNonBlank(xmlSourceConfig.getFilePath(), "filePath");
		String expectedRootElement = requireNonBlank(xmlSourceConfig.getRootElement(), "rootElement");
		String expectedRecordElement = requireNonBlank(xmlSourceConfig.getRecordElement(), "recordElement");

		Path sourceArtifactPath = Path.of(filePathValue.trim());
		validateFile(sourceArtifactPath);
		Path readableFilePath = fileSourceArtifactSupport.resolveReadablePath(xmlSourceConfig);
		fileSourceArtifactSupport.validateReadableRegularFile(readableFilePath, "XML file");
		validateRejectConfiguration(xmlSourceConfig.getValidation());
		validateFileNamePattern(xmlSourceConfig.getValidation(), readableFilePath, xmlSourceConfig);
		validateSchema(xmlSourceConfig.getValidation(), readableFilePath, xmlSourceConfig);
		validateXmlStructure(xmlSourceConfig.getValidation(), readableFilePath, expectedRootElement.trim(), expectedRecordElement.trim(), xmlSourceConfig);
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

	private void validateFileNamePattern(XmlSourceConfig.ValidationConfig validation, Path filePath, XmlSourceConfig xmlSourceConfig) {
		if (validation == null || validation.getFileNamePattern() == null || validation.getFileNamePattern().isBlank()) {
			return;
		}

		String fileName = filePath.getFileName() == null ? "" : filePath.getFileName().toString();
		if (Pattern.compile(validation.getFileNamePattern()).matcher(fileName).matches()) {
			return;
		}

		handleValidationFailure(validation, filePath, xmlSourceConfig,
				"XML fileNamePattern validation failed. pattern=" + validation.getFileNamePattern() + " fileName=" + fileName);
	}

	private void validateXmlStructure(XmlSourceConfig.ValidationConfig validation,
	                                Path filePath,
	                                String expectedRootElement,
	                                String expectedRecordElement,
	                                XmlSourceConfig xmlSourceConfig) {
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
							handleValidationFailure(validation, filePath, xmlSourceConfig,
									"XML root element does not match configured rootElement. expected="
											+ expectedRootElement + " actual=" + elementName);
						}
						if (expectedRecordElement.equals(elementName)) {
							recordSeen = true;
						}
						continue;
					}

					if (expectedRecordElement.equals(elementName)) {
						recordSeen = true;
					}
				}
			} finally {
				reader.close();
			}
		} catch (XMLStreamException e) {
			handleValidationFailure(validation, filePath, xmlSourceConfig,
					"XML source must be well-formed and readable: " + filePath + "; " + e.getMessage());
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to read XML file for validation: " + filePath, e);
		}

		if (!rootSeen) {
			handleValidationFailure(validation, filePath, xmlSourceConfig,
					"XML document must contain a root element: " + filePath);
		}
		if (!recordSeen) {
			handleValidationFailure(validation, filePath, xmlSourceConfig,
					"XML record element '" + expectedRecordElement
							+ "' was not found inside root '" + expectedRootElement + "'.");
		}
	}

	private void validateSchema(XmlSourceConfig.ValidationConfig validation, Path filePath, XmlSourceConfig xmlSourceConfig) {
		if (validation == null || validation.getSchemaPath() == null || validation.getSchemaPath().isBlank()) {
			return;
		}

		Path schemaPath = Path.of(validation.getSchemaPath().trim());
		if (!Files.exists(schemaPath)) {
			throw new IllegalArgumentException("XML validation schemaPath must exist: " + schemaPath);
		}
		if (!Files.isRegularFile(schemaPath)) {
			throw new IllegalArgumentException("XML validation schemaPath must be a regular file: " + schemaPath);
		}
		if (!Files.isReadable(schemaPath)) {
			throw new IllegalArgumentException("XML validation schemaPath must be readable: " + schemaPath);
		}

		SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
		disableExternalSchemaResolution(schemaFactory);
		try {
			Schema schema = schemaFactory.newSchema(schemaPath.toFile());
			Validator validator = schema.newValidator();
			try (InputStream inputStream = Files.newInputStream(filePath)) {
				validator.validate(new StreamSource(inputStream));
			}
		} catch (SAXException e) {
			handleValidationFailure(validation, filePath, xmlSourceConfig,
					"XML schema validation failed for schemaPath=" + schemaPath + ": " + e.getMessage());
		} catch (IOException e) {
			throw new IllegalArgumentException("Unable to validate XML file against schemaPath=" + schemaPath + ": " + filePath, e);
		}
	}

	private void disableExternalSchemaResolution(SchemaFactory schemaFactory) {
		setIfSupported(schemaFactory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
		setIfSupported(schemaFactory, XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
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

	private void setIfSupported(SchemaFactory schemaFactory, String propertyName, String value) {
		try {
			schemaFactory.setProperty(propertyName, value);
		} catch (SAXException | IllegalArgumentException ignored) {
			// Keep compatibility with SchemaFactory implementations that do not expose the property.
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

	private void handleValidationFailure(XmlSourceConfig.ValidationConfig validation,
	                                  Path filePath,
	                                  XmlSourceConfig xmlSourceConfig,
	                                  String message) {
		if (validation == null || !"rejectFile".equalsIgnoreCase(normalize(validation.getOnFailure()))) {
			throw new IllegalArgumentException(message);
		}

		Path rejectedPath = moveToRejectPath(fileSourceArtifactSupport.rejectablePath(xmlSourceConfig, filePath), validation.getRejectPath());
		fileSourceArtifactSupport.cleanupPreparedFile(xmlSourceConfig);
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
