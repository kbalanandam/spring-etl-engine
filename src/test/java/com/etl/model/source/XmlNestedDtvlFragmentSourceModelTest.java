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

class XmlNestedDtvlFragmentSourceModelTest {

    @Test
    void unmarshalsRealDtvlFragmentsIntoFullNestedSourceRecords() throws Exception {
        List<TVLTagDetails> records = readRecords(Path.of(
                "input", "9002_9002_20260427070109.DTVL"
        ), 2);

        assertEquals(2, records.size());

        TVLTagDetails first = records.get(0);
        assertEquals("0056", first.getHomeAgencyID());
        assertEquals("1300", first.getTagAgencyID());
        assertEquals("0003514777", first.getTagSerialNumber());
        assertNotNull(first.getTVLPlateDetails());
        assertEquals("US", first.getTVLPlateDetails().getPlateCountry());
        assertEquals("MO", first.getTVLPlateDetails().getPlateState());
        assertEquals("17AF93", first.getTVLPlateDetails().getPlateNumber());
        assertNotNull(first.getTVLAccountDetails());
        assertEquals("6084909", first.getTVLAccountDetails().getAccountNumber());

        TVLTagDetails second = records.get(1);
        assertEquals("0056", second.getHomeAgencyID());
        assertEquals("1300", second.getTagAgencyID());
        assertEquals("0003514224", second.getTagSerialNumber());
        assertEquals("6716966", second.getTVLAccountDetails().getAccountNumber());
    }

    private List<TVLTagDetails> readRecords(Path xmlPath, int maxRecords) throws Exception {
        JAXBContext context = JAXBContext.newInstance(TVLTagDetails.class);
        Unmarshaller unmarshaller = context.createUnmarshaller();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        List<TVLTagDetails> records = new ArrayList<>();

        try (InputStream inputStream = Files.newInputStream(xmlPath)) {
            XMLStreamReader reader = factory.createXMLStreamReader(inputStream);
            while (reader.hasNext() && records.size() < maxRecords) {
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
