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

class GeneratedModelPackageResolverTest {

    @Test
    void resolvesSourceAndTargetPackagesFromResolvedConfigState() {
        CsvSourceConfig sourceConfig = new CsvSourceConfig(
                "Customers",
                "com.etl.generated.job.customerfeed.source",
                List.of(column("id", "int")),
                "input/customers.csv",
                ","
        );
        JsonTargetConfig targetConfig = new JsonTargetConfig(
                "CustomersJson",
                "com.etl.generated.job.customerfeed.target",
                List.of(column("id", "int")),
                "output/customers.json"
        );

        assertEquals("com.etl.generated.job.customerfeed.source", GeneratedModelPackageResolver.resolveSourcePackage(sourceConfig));
        assertEquals("com.etl.generated.job.customerfeed.target", GeneratedModelPackageResolver.resolveTargetPackage(targetConfig));
    }

    @Test
    void failsFastWhenSourcePackageIsBlank() {
        XmlSourceConfig sourceConfig = new XmlSourceConfig(
                "Customers",
                null,
                List.of(column("id", "int")),
                "input/customers.xml",
                "Customers",
                "Customer"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GeneratedModelPackageResolver.resolveSourcePackage(sourceConfig)
        );

        assertTrue(exception.getMessage().contains("must define a non-blank packageName"));
        assertTrue(exception.getMessage().contains("Customers"));
        assertTrue(exception.getMessage().contains("ConfigLoader package defaulting"));
    }

    @Test
    void failsFastWhenTargetPackageIsInvalid() {
        XmlTargetConfig targetConfig = new XmlTargetConfig(
                "CustomersOut",
                "com.etl.invalid-package",
                List.of(column("id", "int")),
                "output/customers.xml",
                "Customers",
                "Customer"
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> GeneratedModelPackageResolver.resolveTargetPackage(targetConfig)
        );

        assertTrue(exception.getMessage().contains("invalid packageName"));
        assertTrue(exception.getMessage().contains("CustomersOut"));
    }

    private static ColumnConfig column(String name, String type) {
        ColumnConfig column = new ColumnConfig();
        column.setName(name);
        column.setType(type);
        return column;
    }
}

