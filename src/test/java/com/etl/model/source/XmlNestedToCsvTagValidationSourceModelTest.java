package com.etl.model.source;

import com.etl.testsupport.GeneratedScenarioModelSupport;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class XmlNestedToCsvTagValidationSourceModelTest {

    @TempDir
    Path tempDir;

    @Test
    void checkedInSourceModelUnmarshalsNestedFieldsFromTagValidationXml() throws Exception {
        try (GeneratedScenarioModelSupport.CompiledGeneratedModels compiledModels = GeneratedScenarioModelSupport.compileJobScopedModels(
                Path.of("src", "main", "resources", "config-scenarios", "xml-nested-to-csv-tag-validation", "job-config.yaml"),
                tempDir
        )) {
            Class<?> recordClass = compiledModels.loadClass("com.etl.generated.job.xmlnestedtocsvtagvalidation.source.TVLTagDetails");
            List<Object> records = readRecords(
                    Path.of("src", "main", "resources", "config-scenarios", "xml-nested-tag-validation", "input", "nested-sample.xml"),
                    recordClass
            );

            assertEquals(1, records.size());

            Object first = records.get(0);
            assertEquals("0056", getString(first, "getHomeAgencyID"));
            assertEquals("1300", getString(first, "getTagAgencyID"));
            assertEquals("0003518358", getString(first, "getTagSerialNumber"));
            Object firstPlate = first.getClass().getMethod("getTVLPlateDetails").invoke(first);
            assertNotNull(firstPlate);
            assertEquals("US", getString(firstPlate, "getPlateCountry"));
            assertEquals("KS", getString(firstPlate, "getPlateState"));
            assertEquals("7064AFP", getString(firstPlate, "getPlateNumber"));
            Object firstAccount = first.getClass().getMethod("getTVLAccountDetails").invoke(first);
            assertNotNull(firstAccount);
            assertEquals("4773316", getString(firstAccount, "getAccountNumber"));
            assertEquals("N", getString(firstAccount, "getFleetIndicator"));
        }
    }

    private List<Object> readRecords(Path xmlPath, Class<?> recordClass) throws Exception {
        JAXBContext context = JAXBContext.newInstance(recordClass);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        List<Object> records = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(xmlPath)) {
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
            while (reader.hasNext()) {
                if (reader.isStartElement() && "TVLTagDetails".equals(reader.getLocalName())) {
                    records.add(unmarshaller.unmarshal(reader, recordClass).getValue());
                    continue;
                }
                reader.next();
            }
            reader.close();
        }

        return records;
    }

    private String getString(Object target, String methodName) throws Exception {
        return (String) target.getClass().getMethod(methodName).invoke(target);
    }
}


