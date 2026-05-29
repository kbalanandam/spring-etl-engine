package com.etl.writer;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.etl.config.ColumnConfig;
import com.etl.config.target.XmlTargetConfig;
import com.etl.exception.EtlErrorCategory;
import com.etl.exception.EtlExceptionDetails;
import com.etl.model.target.Customer;
import com.etl.model.target.Customers;
import com.etl.writer.impl.SingleObjectXmlWriter;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.xml.StaxEventItemWriter;

class XmlDynamicWriterTest {

    private final DynamicWriterFactory factory = new DynamicWriterFactory(List.of(new com.etl.writer.impl.XmlDynamicWriter()));

    @Test
    void createsWrapperXmlWriterForTaskletMode(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_test.xml");
    XmlTargetConfig config = getXmlTargetConfig(outputFile);
        ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
        assertNotNull(writer);
        assertInstanceOf(SingleObjectXmlWriter.class, writer);

    Customer customer = new Customer();
    customer.setId(1);
    customer.setName("Jane Doe");
    customer.setEmail("jane@example.com");

    Customers customers = new Customers();
    customers.setCustomer(List.of(customer));

    writer.write(new Chunk<>(List.of(customers)));

    String xml = Files.readString(outputFile);
    assertTrue(xml.contains("<Customers>"));
    assertTrue(xml.contains("<Customer>"));
    assertTrue(xml.contains("Jane Doe"));
    }

  @Test
  void writesDirectoryStyleXmlTargetToTargetNameFile(@TempDir Path tempDir) throws Exception {
    Path outputDirectory = tempDir.resolve("target");
    Files.createDirectories(outputDirectory);
    Path expectedOutputFile = outputDirectory.resolve("customers.xml");
    XmlTargetConfig config = getXmlTargetConfig(outputDirectory);
    ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
    assertInstanceOf(SingleObjectXmlWriter.class, writer);

    Customer customer = new Customer();
    customer.setId(11);
    customer.setName("Directory Jane");
    customer.setEmail("directory@example.com");

    Customers customers = new Customers();
    customers.setCustomer(List.of(customer));

    writer.write(new Chunk<>(List.of(customers)));

    assertTrue(Files.exists(expectedOutputFile));
    assertFalse(Files.exists(tempDir.resolve("targetcustomers.xml")));
    String xml = Files.readString(expectedOutputFile);
    assertTrue(xml.contains("<Customers>"));
    assertTrue(xml.contains("Directory Jane"));
  }

    @Test
    void createsChunkXmlWriterForRecordClass(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_test.xml");
    XmlTargetConfig config = getXmlTargetConfig(outputFile);
        ItemWriter<Object> writer = factory.createWriter(config, Customer.class);
        assertNotNull(writer);
        assertInstanceOf(StaxEventItemWriter.class, writer);

    StaxEventItemWriter<Object> xmlWriter = (StaxEventItemWriter<Object>) writer;
    xmlWriter.afterPropertiesSet();
    xmlWriter.open(new ExecutionContext());

    Customer firstCustomer = new Customer();
    firstCustomer.setId(2);
    firstCustomer.setName("Chunk Jane");
    firstCustomer.setEmail("chunk.jane@example.com");

    Customer secondCustomer = new Customer();
    secondCustomer.setId(3);
    secondCustomer.setName("Chunk John");
    secondCustomer.setEmail("chunk.john@example.com");

    xmlWriter.write(new Chunk<>(List.of(firstCustomer, secondCustomer)));
    xmlWriter.close();

    String xml = Files.readString(outputFile);
    assertTrue(xml.contains("<Customers>"));
    assertTrue(xml.contains("<Customer>"));
    assertTrue(xml.contains("Chunk Jane"));
    assertTrue(xml.contains("Chunk John"));
    }

    @Test
    void replacesExistingFinalXmlOnSuccessfulStandaloneWrite(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_replace.xml");
    Files.writeString(outputFile, "<stale/>");
    XmlTargetConfig config = getXmlTargetConfig(outputFile);
        ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
        assertInstanceOf(SingleObjectXmlWriter.class, writer);

    Customer customer = new Customer();
    customer.setId(4);
    customer.setName("Replacement Jane");
    customer.setEmail("replacement@example.com");

    Customers customers = new Customers();
    customers.setCustomer(List.of(customer));

    writer.write(new Chunk<>(List.of(customers)));

    String xml = Files.readString(outputFile);
    assertFalse(xml.contains("<stale/>"));
    assertTrue(xml.contains("Replacement Jane"));
    }

  @Test
  void packagesSuccessfulXmlOutputAsZipWhenConfigured(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers-zipped.xml");
    ColumnConfig col1 = new ColumnConfig();
    col1.setName("id");
    col1.setType("integer");
    ColumnConfig col2 = new ColumnConfig();
    col2.setName("name");
    col2.setType("string");
    ColumnConfig col3 = new ColumnConfig();
    col3.setName("email");
    col3.setType("string");
    XmlTargetConfig config = new XmlTargetConfig(
        "customers",
        "com.etl.model.target",
        List.of(col1, col2, col3),
        outputFile.toString(),
        "Customers",
        "Customer",
        null,
        true
    );
    ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
    assertInstanceOf(SingleObjectXmlWriter.class, writer);

    Customer customer = new Customer();
    customer.setId(44);
    customer.setName("Zip Jane");
    customer.setEmail("zip.xml@example.com");
    Customers customers = new Customers();
    customers.setCustomer(List.of(customer));

    writer.write(new Chunk<>(List.of(customers)));

    Path publishedZip = outputFile.resolveSibling(outputFile.getFileName() + ".zip");
    assertTrue(Files.exists(publishedZip));
    assertFalse(Files.exists(outputFile));
    assertEquals(List.of("customers-zipped.xml"), zipEntryNames(publishedZip));
    String xml = readZipEntryContents(publishedZip, "customers-zipped.xml");
    assertTrue(xml.contains("<Customers>"));
    assertTrue(xml.contains("Zip Jane"));
  }

    @Test
    void doesNotPromoteFinalXmlWhenStepFails(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_failed.xml");
    Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
    XmlTargetConfig config = getXmlTargetConfig(outputFile);
        ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
        assertInstanceOf(SingleObjectXmlWriter.class, writer);

    Customer customer = new Customer();
    customer.setId(5);
    customer.setName("Failed Jane");
    customer.setEmail("failed@example.com");

    Customers customers = new Customers();
    customers.setCustomer(List.of(customer));

    StepExecutionListener listener = (StepExecutionListener) writer;
    StepExecution stepExecution = new StepExecution("customers-step", new JobExecution(2L));

    StepSynchronizationManager.register(stepExecution);
    try {
        writer.write(new Chunk<>(List.of(customers)));
        ((SingleObjectXmlWriter) writer).close();
    } finally {
        StepSynchronizationManager.close();
    }

    stepExecution.setExitStatus(ExitStatus.FAILED);
    listener.afterStep(stepExecution);

    assertFalse(Files.exists(outputFile));
    assertFalse(Files.exists(stagingFile));
    }

    @Test
    void cleansOrphanedChunkXmlPartFileAtStepStart(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_chunk_orphaned.xml");
    Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
    Files.writeString(stagingFile, "<partial/>");
    ItemWriter<Object> writer = factory.createWriter(getXmlTargetConfig(outputFile), Customer.class);
    StepExecutionListener listener = (StepExecutionListener) writer;

    listener.beforeStep(new StepExecution("customers-chunk-step", new JobExecution(5L)));

    assertFalse(Files.exists(stagingFile));
    }

    @Test
    void cleansOrphanedWrapperXmlPartFileAtStepStart(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_wrapper_orphaned.xml");
    Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
    Files.writeString(stagingFile, "<partial/>");
    ItemWriter<Object> writer = factory.createWriter(getXmlTargetConfig(outputFile), Customers.class);
    StepExecutionListener listener = (StepExecutionListener) writer;

    listener.beforeStep(new StepExecution("customers-wrapper-step", new JobExecution(6L)));

    assertFalse(Files.exists(stagingFile));
    }

    @Test
    void promotesChunkXmlAfterAfterStepThenCloseWithinActiveStep(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_chunk_step.xml");
    Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
    XmlTargetConfig config = getXmlTargetConfig(outputFile);
        ItemWriter<Object> writer = factory.createWriter(config, Customer.class);
        assertInstanceOf(StaxEventItemWriter.class, writer);

    Customer customer = new Customer();
    customer.setId(6);
    customer.setName("Step Chunk Jane");
    customer.setEmail("step.chunk@example.com");

    StaxEventItemWriter<Object> xmlWriter = (StaxEventItemWriter<Object>) writer;
    StepExecutionListener listener = (StepExecutionListener) writer;
    StepExecution stepExecution = new StepExecution("customers-chunk-step", new JobExecution(3L));

    xmlWriter.afterPropertiesSet();
    StepSynchronizationManager.register(stepExecution);
    try {
        xmlWriter.open(new ExecutionContext());
        xmlWriter.write(new Chunk<>(List.of(customer)));

        stepExecution.setExitStatus(ExitStatus.COMPLETED);
        listener.afterStep(stepExecution);

        assertFalse(Files.exists(outputFile));
        assertTrue(Files.exists(stagingFile));

        xmlWriter.close();
    } finally {
        StepSynchronizationManager.close();
    }

    assertTrue(Files.exists(outputFile));
    assertFalse(Files.exists(stagingFile));
    assertTrue(Files.readString(outputFile).contains("Step Chunk Jane"));
    }

  @Test
  void cleansStagingAndCategorizesChunkXmlWriteFailureAsTargetWrite(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_write_failed.xml");
    Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
    XmlTargetConfig config = getXmlTargetConfig(outputFile, "BrokenChunkXmlRecord");
    ItemWriter<Object> writer = factory.createWriter(config, BrokenChunkXmlRecord.class);
    assertInstanceOf(StaxEventItemWriter.class, writer);

    StaxEventItemWriter<Object> xmlWriter = (StaxEventItemWriter<Object>) writer;
    xmlWriter.afterPropertiesSet();
    xmlWriter.open(new ExecutionContext());

    Exception failure = assertThrows(
        Exception.class,
        () -> xmlWriter.write(new Chunk<>(List.of(new BrokenChunkXmlRecord("8"))))
    );
    assertEquals(EtlErrorCategory.TARGET_WRITE, EtlExceptionDetails.categoryOf(failure));

    xmlWriter.close();

    assertFalse(Files.exists(outputFile));
    assertFalse(Files.exists(stagingFile));
  }

    @Test
    void promotesSingleObjectXmlAfterCloseThenAfterStepWithinActiveStep(@TempDir Path tempDir) throws Exception {
    Path outputFile = tempDir.resolve("customers_tasklet_step.xml");
    Path stagingFile = outputFile.resolveSibling(outputFile.getFileName() + ".part");
    XmlTargetConfig config = getXmlTargetConfig(outputFile);
        ItemWriter<Object> writer = factory.createWriter(config, Customers.class);
        assertInstanceOf(SingleObjectXmlWriter.class, writer);

    Customer customer = new Customer();
    customer.setId(7);
    customer.setName("Step Wrapper Jane");
    customer.setEmail("step.wrapper@example.com");

    Customers customers = new Customers();
    customers.setCustomer(List.of(customer));

    StepExecutionListener listener = (StepExecutionListener) writer;
    StepExecution stepExecution = new StepExecution("customers-tasklet-step", new JobExecution(4L));

    StepSynchronizationManager.register(stepExecution);
    try {
        writer.write(new Chunk<>(List.of(customers)));
        ((SingleObjectXmlWriter) writer).close();

        assertFalse(Files.exists(outputFile));
        assertTrue(Files.exists(stagingFile));

        stepExecution.setExitStatus(ExitStatus.COMPLETED);
        listener.afterStep(stepExecution);
    } finally {
        StepSynchronizationManager.close();
    }

    assertTrue(Files.exists(outputFile));
    assertFalse(Files.exists(stagingFile));
    assertTrue(Files.readString(outputFile).contains("Step Wrapper Jane"));
    }

  private static XmlTargetConfig getXmlTargetConfig(Path outputFile) {
		return getXmlTargetConfig(outputFile, "Customer");
	}

	private static XmlTargetConfig getXmlTargetConfig(Path outputFile, String recordElement) {
        ColumnConfig col1 = new ColumnConfig();
        col1.setName("id");
        col1.setType("integer");

        ColumnConfig col2 = new ColumnConfig();
        col2.setName("name");
        col2.setType("string");

        ColumnConfig col3 = new ColumnConfig();
        col3.setName("email");
        col3.setType("string");

        List<ColumnConfig> columnConfig = List.of(col1, col2, col3);
        return new XmlTargetConfig(
                "customers",
                "com.etl.model.target",
                columnConfig,
                outputFile.toString(),
                "Customers",
                recordElement
        );
    }

  private List<String> zipEntryNames(Path zipFile) throws Exception {
    List<String> entryNames = new ArrayList<>();
    try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
      ZipEntry entry;
      while ((entry = zipInputStream.getNextEntry()) != null) {
        if (!entry.isDirectory()) {
          entryNames.add(entry.getName());
        }
      }
    }
    return entryNames;
  }

  private String readZipEntryContents(Path zipFile, String expectedEntryName) throws Exception {
    try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipFile))) {
      ZipEntry entry;
      while ((entry = zipInputStream.getNextEntry()) != null) {
        if (!entry.isDirectory() && expectedEntryName.equals(entry.getName())) {
          return new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
      }
    }
    return "";
  }

  @XmlRootElement(name = "BrokenChunkXmlRecord")
  @XmlAccessorType(XmlAccessType.PROPERTY)
  public static final class BrokenChunkXmlRecord {
    private String id;

    public BrokenChunkXmlRecord() {
    }

    public BrokenChunkXmlRecord(String id) {
      this.id = id;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getName() {
      throw new IllegalStateException("boom");
    }

    public void setName(String name) {
      // no-op; getter throws so the marshalling path still fails deterministically
    }
  }
}
