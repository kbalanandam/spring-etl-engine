package com.etl.common.util;

import com.etl.config.ColumnConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.JsonTargetConfig;
import com.etl.config.target.XmlTargetConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedModelNamingPolicyTest {

    @Test
    void derivesFlatAndXmlSimpleNamesForJobScopedPackages() {
        CsvSourceConfig csvSource = new CsvSourceConfig(
                "Customer Feed",
                "com.etl.generated.job.customerfeed.source",
                List.of(column("id", "String")),
                "input/customers.csv",
                ","
        );
        XmlSourceConfig xmlSource = new XmlSourceConfig(
                "Transaction Record",
                "com.etl.generated.job.customerfeed.source",
                List.of(column("id", "String")),
                "input/transactions.xml",
                "TransactionData",
                "TransactionRecord"
        );
        JsonTargetConfig jsonTarget = new JsonTargetConfig(
                "Customer Export",
                "com.etl.generated.job.customerfeed.target",
                List.of(column("id", "String")),
                "output/customers.json"
        );
        XmlTargetConfig xmlTarget = new XmlTargetConfig(
                "Transaction Export",
                "com.etl.generated.job.customerfeed.target",
                List.of(column("id", "String")),
                "output/transactions.xml",
                "Transactions",
                "Transaction"
        );

        assertEquals("CustomerFeedModel", GeneratedModelNamingPolicy.resolveSourceSimpleClassName(csvSource));
        assertEquals("TransactionRecordXmlRecord", GeneratedModelNamingPolicy.resolveSourceSimpleClassName(xmlSource));
        assertEquals("TransactionRecordXmlRoot", GeneratedModelNamingPolicy.resolveSourceRootSimpleClassName(xmlSource));
        assertEquals("CustomerExportModel", GeneratedModelNamingPolicy.resolveTargetWriteSimpleClassName(jsonTarget));
        assertEquals("TransactionExportXmlRecord", GeneratedModelNamingPolicy.resolveTargetProcessingSimpleClassName(xmlTarget));
        assertEquals("TransactionExportXmlRoot", GeneratedModelNamingPolicy.resolveTargetWriteSimpleClassName(xmlTarget));
    }

    @Test
    void keepsLegacySimpleNamesForNonDerivedPackages() {
        XmlSourceConfig xmlSource = new XmlSourceConfig(
                "Logical Source Name",
                "com.etl.model.source.xml",
                List.of(column("id", "String")),
                "input/transactions.xml",
                "TransactionData",
                "TransactionRecord"
        );
        XmlTargetConfig xmlTarget = new XmlTargetConfig(
                "Logical Target Name",
                "com.etl.model.target.xml",
                List.of(column("id", "String")),
                "output/transactions.xml",
                "Transactions",
                "Transaction"
        );

        assertEquals("TransactionRecord", GeneratedModelNamingPolicy.resolveSourceSimpleClassName(xmlSource));
        assertEquals("TransactionData", GeneratedModelNamingPolicy.resolveSourceRootSimpleClassName(xmlSource));
        assertEquals("Transaction", GeneratedModelNamingPolicy.resolveTargetProcessingSimpleClassName(xmlTarget));
        assertEquals("Transactions", GeneratedModelNamingPolicy.resolveTargetWriteSimpleClassName(xmlTarget));
        assertEquals("transaction", GeneratedModelNamingPolicy.resolveWrapperFieldName(xmlTarget.getPackageName(), xmlTarget.getRecordElement(), "IgnoredXmlRecord"));
        assertEquals("Transaction", GeneratedModelNamingPolicy.resolveWrapperAccessorBase(xmlTarget.getPackageName(), xmlTarget.getRecordElement(), "IgnoredXmlRecord"));
    }

    @Test
    void usesRecordClassNameForDerivedXmlWrapperFieldAndAccessorNames() {
        String packageName = "com.etl.generated.job.transactionfeed.target";

        assertEquals("transactionExportXmlRecord",
                GeneratedModelNamingPolicy.resolveWrapperFieldName(packageName, "Transaction", "TransactionExportXmlRecord"));
        assertEquals("TransactionExportXmlRecord",
                GeneratedModelNamingPolicy.resolveWrapperAccessorBase(packageName, "Transaction", "TransactionExportXmlRecord"));
    }

    @Test
    void normalizesLogicalNamesIntoJavaFriendlyUpperCamelCase() {
        assertEquals("CustomerFeed2026", GeneratedModelNamingPolicy.normalizeLogicalName("customer-feed 2026"));
        assertEquals("X9002Transactions", GeneratedModelNamingPolicy.normalizeLogicalName("9002 transactions"));
    }

    @Test
    void failsFastWhenLogicalNameCannotProduceAJavaIdentifier() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GeneratedModelNamingPolicy.normalizeLogicalName("---")
        );

        assertTrue(exception.getMessage().contains("could not derive a Java class name"));
    }

    @Test
    void exposesStableGeneratedSourceHeader() {
        assertEquals("Generated by OneFlow. Do not edit manually.", GeneratedModelNamingPolicy.generatedSourceHeader());
    }

    private static ColumnConfig column(String name, String type) {
        ColumnConfig column = new ColumnConfig();
        column.setName(name);
        column.setType(type);
        return column;
    }
}

