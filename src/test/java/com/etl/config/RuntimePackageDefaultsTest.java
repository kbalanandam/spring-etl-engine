package com.etl.config;

import com.etl.common.util.JobScopedPackageNameResolver;
import com.etl.config.relational.RelationalConnectionConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.source.SourceWrapper;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.JsonTargetConfig;
import com.etl.config.target.RelationalTargetConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.TargetWrapper;
import com.etl.config.target.XmlTargetConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class RuntimePackageDefaultsTest {

    @Test
    void applyDefaultSourcePackagesAssignsJobScopedSourcePackage() {
        SourceWrapper sourceWrapper = sourceWrapper(csvSource("Customers"), xmlSource("Orders"));

        RuntimePackageDefaults.applyDefaultSourcePackages(sourceWrapper, "customer-load");

        String expected = JobScopedPackageNameResolver.resolveSourcePackage("customer-load");
        assertEquals(expected, sourceWrapper.getSources().get(0).getPackageName());
        assertEquals(expected, sourceWrapper.getSources().get(1).getPackageName());
    }

    @Test
    void applyDirectConfigSourcePackagesAssignsDirectSourcePackage() {
        SourceWrapper sourceWrapper = sourceWrapper(csvSource("Customers"), xmlSource("Orders"));

        RuntimePackageDefaults.applyDirectConfigSourcePackages(sourceWrapper);

        assertEquals("com.etl.model.source", sourceWrapper.getSources().get(0).getPackageName());
        assertEquals("com.etl.model.source", sourceWrapper.getSources().get(1).getPackageName());
    }

    @Test
    void applyDefaultTargetPackagesRebuildsTargetConfigsWithJobScopedPackage() {
        RelationalConnectionConfig connection = relationalConnection();
        TargetWrapper targetWrapper = targetWrapper(
                new CsvTargetConfig("CustomersCsv", "old.pkg", List.of(), "output/customers.csv", ";", true, true),
                new JsonTargetConfig("CustomersJson", "old.pkg", List.of(), "output/customers.json", true),
                new XmlTargetConfig("CustomersXml", "old.pkg", List.of(), "output/customers.xml", "Customers", "Customer", "definitions/customers.yaml", true),
                new RelationalTargetConfig("CustomersDb", "old.pkg", List.of(), connection, "customers", "dbo", "insert", 250)
        );

        List<TargetConfig> originalTargets = targetWrapper.getTargets();
        RuntimePackageDefaults.applyDefaultTargetPackages(targetWrapper, "customer-load");

        String expectedPackage = JobScopedPackageNameResolver.resolveTargetPackage("customer-load");

        CsvTargetConfig csvTarget = assertInstanceOf(CsvTargetConfig.class, targetWrapper.getTargets().get(0));
        assertEquals(expectedPackage, csvTarget.getPackageName());
        assertEquals("output/customers.csv", csvTarget.getFilePath());
        assertEquals(";", csvTarget.getDelimiter());
        assertEquals(true, csvTarget.isIncludeHeader());
        assertEquals(true, csvTarget.isPackageAsZip());

        JsonTargetConfig jsonTarget = assertInstanceOf(JsonTargetConfig.class, targetWrapper.getTargets().get(1));
        assertEquals(expectedPackage, jsonTarget.getPackageName());
        assertEquals("output/customers.json", jsonTarget.getFilePath());
        assertEquals(true, jsonTarget.isPackageAsZip());

        XmlTargetConfig xmlTarget = assertInstanceOf(XmlTargetConfig.class, targetWrapper.getTargets().get(2));
        assertEquals(expectedPackage, xmlTarget.getPackageName());
        assertEquals("output/customers.xml", xmlTarget.getFilePath());
        assertEquals("Customers", xmlTarget.getRootElement());
        assertEquals("Customer", xmlTarget.getRecordElement());
        assertEquals("definitions/customers.yaml", xmlTarget.getModelDefinitionPath());
        assertEquals(true, xmlTarget.isPackageAsZip());

        RelationalTargetConfig relationalTarget = assertInstanceOf(RelationalTargetConfig.class, targetWrapper.getTargets().get(3));
        assertEquals(expectedPackage, relationalTarget.getPackageName());
        assertEquals("customers", relationalTarget.getTable());
        assertEquals("dbo", relationalTarget.getSchema());
        assertEquals(250, relationalTarget.getBatchSize());
        assertNotSame(connection, relationalTarget.getConnection());

        for (int i = 0; i < originalTargets.size(); i++) {
            assertNotSame(originalTargets.get(i), targetWrapper.getTargets().get(i));
        }
    }

    @Test
    void applyDirectConfigTargetPackagesRebuildsTargetConfigsWithDirectPackage() {
        RelationalConnectionConfig connection = relationalConnection();
        TargetWrapper targetWrapper = targetWrapper(
                new CsvTargetConfig("CustomersCsv", "old.pkg", List.of(), "output/customers.csv", ",", false, false),
                new JsonTargetConfig("CustomersJson", "old.pkg", List.of(), "output/customers.json", false),
                new XmlTargetConfig("CustomersXml", "old.pkg", List.of(), "output/customers.xml", "Customers", "Customer", "definitions/customers.yaml", false),
                new RelationalTargetConfig("CustomersDb", "old.pkg", List.of(), connection, "customers", "dbo", "insert", 100)
        );

        RuntimePackageDefaults.applyDirectConfigTargetPackages(targetWrapper);

        String expectedPackage = "com.etl.model.target";
        assertEquals(expectedPackage, targetWrapper.getTargets().get(0).getPackageName());
        assertEquals(expectedPackage, targetWrapper.getTargets().get(1).getPackageName());
        assertEquals(expectedPackage, targetWrapper.getTargets().get(2).getPackageName());
        assertEquals(expectedPackage, targetWrapper.getTargets().get(3).getPackageName());
    }

    private SourceWrapper sourceWrapper(SourceConfig... sources) {
        SourceWrapper sourceWrapper = new SourceWrapper();
        sourceWrapper.setSources(List.of(sources));
        return sourceWrapper;
    }

    private TargetWrapper targetWrapper(TargetConfig... targets) {
        TargetWrapper targetWrapper = new TargetWrapper();
        targetWrapper.setTargets(List.of(targets));
        return targetWrapper;
    }

    private CsvSourceConfig csvSource(String name) {
        CsvSourceConfig sourceConfig = new CsvSourceConfig();
        sourceConfig.setSourceName(name);
        sourceConfig.setFilePath("input.csv");
        sourceConfig.setDelimiter(",");
        return sourceConfig;
    }

    private XmlSourceConfig xmlSource(String name) {
        XmlSourceConfig sourceConfig = new XmlSourceConfig();
        sourceConfig.setSourceName(name);
        sourceConfig.setFilePath("input.xml");
        sourceConfig.setRootElement("Root");
        sourceConfig.setRecordElement("Record");
        return sourceConfig;
    }

    private RelationalConnectionConfig relationalConnection() {
        RelationalConnectionConfig connection = new RelationalConnectionConfig();
        connection.setVendor("sqlserver");
        connection.setHost("localhost");
        connection.setPort(1433);
        connection.setDatabase("etl");
        connection.setSchema("dbo");
        connection.setUsername("sa");
        connection.setPassword("password");
        return connection;
    }
}

