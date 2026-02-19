//package com.etl.reader;
//
//import static org.junit.jupiter.api.Assertions.assertNotNull;
//
//import java.util.List;
//
//import org.assertj.core.api.Assertions;
//import org.junit.jupiter.api.Test;
//import org.springframework.batch.item.ExecutionContext;
//import org.springframework.batch.item.ItemReader;
//import org.springframework.batch.item.file.FlatFileItemReader;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//
//import com.etl.config.source.ColumnConfig;
//import com.etl.config.source.SourceConfig;
//import com.etl.model.source.Customers;
//
//@SpringBootTest
//class DynamicReaderFactoryTest {
//
//	@Autowired
//	private DynamicReaderFactory factory;
//
//	@Test
//	void testCsvReaderCreation() throws Exception {
//		SourceConfig config = new SourceConfig();
//		ColumnConfig col1 = new ColumnConfig();
//		col1.setName("id");
//		col1.setType("integer");
//
//		ColumnConfig col2 = new ColumnConfig();
//		col2.setName("name");
//		col2.setType("string");
//
//		ColumnConfig col3 = new ColumnConfig();
//		col3.setName("email");
//		col3.setType("string");
//
////		ColumnConfig col4 = new ColumnConfig();
////		col3.setName("phone");
////		col3.setType("string");
//
//		List<ColumnConfig> colconfig = List.of(col1, col2, col3);
//		config.setColumns(colconfig);
//
//		config.setType("csv");
//		config.setFilePath("D:/ETLDemo/data/input/customers.csv");
//		config.setDelimiter(",");
//		config.setSourceName("customers");
//
//		ItemReader<Customers> reader = factory.createReader(config, Customers.class);
//		assertNotNull(reader);
//		System.out.println("CSV Reader created successfully: " + reader.getClass().getName());
//		try {
//			// Check if the reader is an instance of FlatFileItemReader
//			if (reader instanceof FlatFileItemReader) {
//				FlatFileItemReader<Customers> flatReader = (FlatFileItemReader<Customers>) reader;
//
//				flatReader.afterPropertiesSet();
//				ExecutionContext executionContext = new ExecutionContext();
//				flatReader.open(executionContext);
//
//				Customers record;
//				while ((record = flatReader.read()) != null) {
//					System.out.println(record); // or use assertions
//				}
//
//				flatReader.close();
//			} else {
//				System.out.println("Reader is not a FlatFileItemReader!");
//			}
//		} catch (
//
//		Exception e) {
//			e.printStackTrace();
//			Assertions.fail("Failed to read records from CSV: " + e.getMessage());
//		}
//
//	}
//}
