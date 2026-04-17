package com.etl.validation.rules;

import org.springframework.batch.item.validator.ValidationException;

import java.io.File;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

public class XsdValidationRule implements ValidationRule<File> {
    private final String xsdPath;

    public XsdValidationRule(String xsdPath) {
        this.xsdPath = xsdPath;
    }

    @Override
    public void validate(File xmlFile) throws ValidationException {
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = factory.newSchema(new File(xsdPath));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(xmlFile));
        } catch (Exception e) {
            throw new ValidationException("XML file does not conform to XSD: " + e.getMessage());
        }
    }
}
