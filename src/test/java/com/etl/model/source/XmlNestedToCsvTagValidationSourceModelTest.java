package com.etl.model.source;

import com.etl.generated.job.xmlnestedtocsvtagvalidation.source.TVLTagDetails;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;

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

    @Test
    void checkedInSourceModelUnmarshalsNestedFieldsFromTagValidationXml() throws Exception {
        List<TVLTagDetails> records = readRecords(Path.of(
                "src", "main", "resources", "config-scenarios", "xml-nested-to-csv-tag-validation", "input", "nested-sample.xml"
        ));

        assertEquals(2, records.size());

        TVLTagDetails first = records.get(0);
        assertEquals("0056", first.getHomeAgencyID());
        assertEquals("1300", first.getTagAgencyID());
        assertEquals("0003518358", first.getTagSerialNumber());
        assertNotNull(first.getTVLPlateDetails());
        assertEquals("US", first.getTVLPlateDetails().getPlateCountry());
        assertEquals("KS", first.getTVLPlateDetails().getPlateState());
        assertEquals("7064AFP", first.getTVLPlateDetails().getPlateNumber());
        assertNotNull(first.getTVLAccountDetails());
        assertEquals("4773316", first.getTVLAccountDetails().getAccountNumber());
        assertEquals("N", first.getTVLAccountDetails().getFleetIndicator());

        TVLTagDetails second = records.get(1);
        assertEquals("0041", second.getHomeAgencyID());
        assertEquals("1112", second.getTagAgencyID());
        assertEquals("0001547304", second.getTagSerialNumber());
        assertNotNull(second.getTVLPlateDetails());
        assertEquals("US", second.getTVLPlateDetails().getPlateCountry());
        assertEquals("TX", second.getTVLPlateDetails().getPlateState());
        assertEquals("VYH2086", second.getTVLPlateDetails().getPlateNumber());
        assertNotNull(second.getTVLAccountDetails());
        assertEquals("2025753547", second.getTVLAccountDetails().getAccountNumber());
        assertEquals("N", second.getTVLAccountDetails().getFleetIndicator());
    }

    private List<TVLTagDetails> readRecords(Path xmlPath) throws Exception {
        JAXBContext context = JAXBContext.newInstance(TVLTagDetails.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        List<TVLTagDetails> records = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(xmlPath)) {
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
            while (reader.hasNext()) {
                if (reader.isStartElement() && "TVLTagDetails".equals(reader.getLocalName())) {
                    records.add(unmarshaller.unmarshal(reader, TVLTagDetails.class).getValue());
                    continue;
                }
                reader.next();
            }
            reader.close();
        }

        return records;
    }
}
