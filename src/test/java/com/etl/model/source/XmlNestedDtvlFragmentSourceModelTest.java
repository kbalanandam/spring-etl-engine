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

class XmlNestedDtvlFragmentSourceModelTest {

    @TempDir
    Path tempDir;

    @Test
    void unmarshalsSampleDtvlFragmentsIntoFullNestedSourceRecords() throws Exception {
        try (GeneratedScenarioModelSupport.CompiledGeneratedModels compiledModels = GeneratedScenarioModelSupport.compileJobScopedModels(
                Path.of("src", "main", "resources", "config-jobs", "xml-nested-to-csv-tag-validation", "job-config.yaml"),
                tempDir
        )) {
            Class<?> recordClass = compiledModels.loadClass("com.etl.generated.job.xmlnestedtocsvtagvalidation.source.TVLTagDetails");
            List<Object> records = readRecords(
                    Path.of("src", "main", "resources", "config-jobs", "xml-nested-to-csv-tag-validation", "input", "tag-validation-sample.xml"),
                    recordClass,
                    2
            );

            assertEquals(2, records.size());

            Object first = records.get(0);
            assertEquals("1001", getString(first, "getHomeAgencyID"));
            assertEquals("2001", getString(first, "getTagAgencyID"));
            assertEquals("SAMPLE-0001", getString(first, "getTagSerialNumber"));
            Object firstPlate = first.getClass().getMethod("getTVLPlateDetails").invoke(first);
            assertNotNull(firstPlate);
            assertEquals("US", getString(firstPlate, "getPlateCountry"));
            assertEquals("CA", getString(firstPlate, "getPlateState"));
            assertEquals("SAMPLE100", getString(firstPlate, "getPlateNumber"));
            Object firstAccount = first.getClass().getMethod("getTVLAccountDetails").invoke(first);
            assertNotNull(firstAccount);
            assertEquals("ACCT-1001", getString(firstAccount, "getAccountNumber"));

            Object second = records.get(1);
            assertEquals("1002", getString(second, "getHomeAgencyID"));
            assertEquals("2002", getString(second, "getTagAgencyID"));
            assertEquals("SAMPLE-0002", getString(second, "getTagSerialNumber"));
            Object secondAccount = second.getClass().getMethod("getTVLAccountDetails").invoke(second);
            assertEquals("ACCT-1002", getString(secondAccount, "getAccountNumber"));
        }
    }

    private List<Object> readRecords(Path xmlPath, Class<?> recordClass, int maxRecords) throws Exception {
        JAXBContext context = JAXBContext.newInstance(recordClass);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        List<Object> records = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(xmlPath)) {
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
            while (reader.hasNext() && records.size() < maxRecords) {
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




