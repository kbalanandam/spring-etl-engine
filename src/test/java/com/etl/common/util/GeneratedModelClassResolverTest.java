package com.etl.common.util;

import com.etl.config.ColumnConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.target.CsvTargetConfig;
import com.etl.config.target.XmlTargetConfig;
import com.etl.model.target.Customer;
import com.etl.model.target.Customers;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GeneratedModelClassResolverTest {

    @Test
    void resolvesXmlProcessingAndWriteClassesSeparately() throws Exception {
        CsvSourceConfig sourceConfig = new CsvSourceConfig(
                "Customers",
                "com.etl.model.source",
                List.of(column("id", "int"), column("name", "String")),
                "target/customers.csv",
                ","
        );
        XmlTargetConfig config = new XmlTargetConfig(
                "Customers",
                "com.etl.model.target",
                List.of(column("id", "int"), column("name", "String")),
                "target/customers.xml",
                "Customers",
                "Customer"
        );

        assertEquals("com.etl.model.target.Customers", GeneratedModelClassResolver.resolveTargetWriteClassName(config));
        assertEquals("com.etl.model.target.Customer", GeneratedModelClassResolver.resolveTargetProcessingClassName(config));
        assertEquals("customer", GeneratedModelClassResolver.resolveXmlWrapperFieldName(config));

        ResolvedModelMetadata metadata = GeneratedModelClassResolver.resolveMetadata(sourceConfig, config);
        assertEquals("com.etl.model.source.Customers", metadata.getSourceClassName());
        assertEquals("com.etl.model.target.Customer", metadata.getTargetProcessingClassName());
        assertEquals("com.etl.model.target.Customers", metadata.getTargetWriteClassName());
        assertTrue(metadata.isWrapperRequired());
        assertEquals("customer", metadata.getWrapperFieldName());

        assertEquals(Customers.class, GeneratedModelClassResolver.resolveTargetWriteClass(config));
        assertEquals(Customers.class, GeneratedModelClassResolver.resolveTargetWriteClass(metadata));
        assertEquals(Customer.class, GeneratedModelClassResolver.resolveTargetProcessingClass(config));
        assertEquals(Customer.class, GeneratedModelClassResolver.resolveTargetProcessingClass(metadata));

        Object wrapper = GeneratedModelClassResolver.createXmlWrapper(config, List.of(new Customer()));
        assertInstanceOf(Customers.class, wrapper);
        assertEquals(1, ((Customers) wrapper).getCustomer().size());

        Object resolvedWrapper = GeneratedModelClassResolver.createWrapper(metadata, List.of(new Customer()));
        assertInstanceOf(Customers.class, resolvedWrapper);
        assertEquals(1, ((Customers) resolvedWrapper).getCustomer().size());
    }

  @Test
  void xmlGeneratedClassesExposeRootAnnotationsNeededForChunkWriting() {
    XmlRootElement recordAnnotation = Customer.class.getAnnotation(XmlRootElement.class);
    XmlRootElement wrapperAnnotation = Customers.class.getAnnotation(XmlRootElement.class);

    assertNotNull(recordAnnotation);
    assertEquals("Customer", recordAnnotation.name());
    assertNotNull(wrapperAnnotation);
    assertEquals("Customers", wrapperAnnotation.name());
  }

    @Test
    void resolvesNonXmlTargetToTargetNameForBothPhases() {
        CsvSourceConfig sourceConfig = new CsvSourceConfig(
                "Customers",
                "com.etl.model.source",
                List.of(column("id", "int")),
                "target/customers.csv",
                ","
        );
        CsvTargetConfig config = new CsvTargetConfig(
                "Customers",
                "com.etl.model.target",
                List.of(column("id", "int")),
                "target/customers.csv",
                ","
        );

        assertEquals("com.etl.model.target.Customers", GeneratedModelClassResolver.resolveTargetWriteClassName(config));
        assertEquals("com.etl.model.target.Customers", GeneratedModelClassResolver.resolveTargetProcessingClassName(config));

        ResolvedModelMetadata metadata = GeneratedModelClassResolver.resolveMetadata(sourceConfig, config);
        assertEquals("com.etl.model.source.Customers", metadata.getSourceClassName());
        assertEquals("com.etl.model.target.Customers", metadata.getTargetProcessingClassName());
        assertEquals("com.etl.model.target.Customers", metadata.getTargetWriteClassName());
        assertFalse(metadata.isWrapperRequired());
        assertNull(metadata.getWrapperFieldName());
    }

    private static ColumnConfig column(String name, String type) {
        ColumnConfig column = new ColumnConfig();
        column.setName(name);
        column.setType(type);
        return column;
    }
}

