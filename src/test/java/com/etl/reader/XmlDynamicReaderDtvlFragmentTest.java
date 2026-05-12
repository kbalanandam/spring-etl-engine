package com.etl.reader;

import com.etl.config.source.XmlSourceConfig;
import com.etl.reader.impl.XmlDynamicReader;
import com.etl.testsupport.GeneratedScenarioModelSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStream;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class XmlDynamicReaderDtvlFragmentTest {

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("unchecked")
    void flattensSampleDtvlFragmentRecordsIntoFullMaps() throws Exception {
        try (GeneratedScenarioModelSupport.CompiledGeneratedModels compiledModels = GeneratedScenarioModelSupport.compileJobScopedModels(
                Path.of("src", "main", "resources", "config-jobs", "xml-nested-to-csv-tag-validation", "job-config.yaml"),
                tempDir
        )) {
            Class<?> recordClass = compiledModels.loadClass("com.etl.generated.job.xmlnestedtocsvtagvalidation.source.TVLTagDetails");

            XmlSourceConfig config = new XmlSourceConfig();
            config.setSourceName("TagValidationSource");
            config.setPackageName("com.etl.generated.job.xmlnestedtocsvtagvalidation.source");
            config.setFilePath(Path.of("src", "main", "resources", "config-jobs", "xml-nested-tag-validation", "input", "nested-sample.xml").toString());
            config.setRootElement("TagValidationList");
            config.setRecordElement("TVLTagDetails");
            config.setFlatteningStrategy("NestedXml");

            ItemReader<Object> reader = new XmlDynamicReader<>().getReader(config, (Class<Object>) recordClass);
            ItemStream itemStream = (ItemStream) reader;
            itemStream.open(new ExecutionContext());
            Object first;
            try {
                first = reader.read();
            } finally {
                itemStream.close();
            }

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
}



