package com.etl.reader;

import com.etl.config.source.XmlSourceConfig;
import com.etl.generated.job.xmlnestedtocsvtagvalidation.source.TVLTagDetails;
import com.etl.reader.impl.XmlDynamicReader;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ItemReader;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class XmlDynamicReaderDtvlFragmentTest {

    @Test
    @SuppressWarnings("unchecked")
    void flattensRealDtvlFragmentRecordsIntoFullMaps() throws Exception {
        XmlSourceConfig config = new XmlSourceConfig();
        config.setSourceName("TagValidationSource");
        config.setPackageName("com.etl.generated.job.xmlnestedtocsvtagvalidation.source");
        config.setFilePath(Path.of("input", "9002_9002_20260427070109.DTVL").toString());
        config.setRootElement("TagValidationList");
        config.setRecordElement("TVLTagDetails");
        config.setFlatteningStrategy("NestedXml");

        ItemReader<Object> reader = new XmlDynamicReader<>().getReader(config, (Class<Object>) TVLTagDetails.class);
        Object first = reader.read();

        assertInstanceOf(Map.class, first);
        Map<String, Object> firstRow = (Map<String, Object>) first;
        assertEquals("0056", firstRow.get("HomeAgencyID"));
        assertEquals("1300", firstRow.get("TagAgencyID"));
        assertEquals("0003518358", firstRow.get("TagSerialNumber"));
        assertEquals("US", firstRow.get("TVLPlateDetails.PlateCountry"));
        assertEquals("KS", firstRow.get("TVLPlateDetails.PlateState"));
        assertEquals("7064AFP", firstRow.get("TVLPlateDetails.PlateNumber"));
        assertEquals("4773316", firstRow.get("TVLAccountDetails.AccountNumber"));
        assertNotNull(firstRow.get("TVLAccountDetails.FleetIndicator"));
    }
}
