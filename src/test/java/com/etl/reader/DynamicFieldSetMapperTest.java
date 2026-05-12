package com.etl.reader;

import com.etl.config.ColumnConfig;
import com.etl.exception.EtlErrorCategory;
import com.etl.exception.EtlExceptionDetails;
import com.etl.reader.mapper.DynamicFieldSetMapper;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.file.transform.DefaultFieldSet;
import org.springframework.batch.item.file.transform.FieldSet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicFieldSetMapperTest {

    @Test
    void mapsMultipleRowsUsingCachedFieldMetadata() {
        DynamicFieldSetMapper<CustomerRow> mapper = new DynamicFieldSetMapper<>(
                List.of(column("id", "integer"), column("name", "string"), column("email", "string")),
                CustomerRow.class
        );

        CustomerRow first = mapper.mapFieldSet(fieldSet("101", "Alice", "alice@example.com"));
        CustomerRow second = mapper.mapFieldSet(fieldSet("102", "Bob", "bob@example.com"));

        assertEquals(101, first.getId());
        assertEquals("Alice", first.getName());
        assertEquals("alice@example.com", first.getEmail());
        assertEquals(102, second.getId());
        assertEquals("Bob", second.getName());
        assertEquals("bob@example.com", second.getEmail());
    }

  @Test
  void categorizesMappingFailureAsRuntime() {
    DynamicFieldSetMapper<CustomerRow> mapper = new DynamicFieldSetMapper<>(
        List.of(column("id", "integer"), column("name", "string"), column("email", "string")),
        CustomerRow.class
    );

    Exception failure = assertThrows(Exception.class,
        () -> mapper.mapFieldSet(fieldSet("not-an-int", "Alice", "alice@example.com")));

    assertEquals(EtlErrorCategory.RUNTIME, EtlExceptionDetails.categoryOf(failure));
  }

  @Test
  void failsFastWhenConfiguredFieldIsNotWritable() {
    Exception failure = assertThrows(
        Exception.class,
        () -> new DynamicFieldSetMapper<>(
            List.of(column("id", "integer"), column("email", "string")),
            GetterOnlyCustomerRow.class
        )
    );

    assertEquals(EtlErrorCategory.RUNTIME, EtlExceptionDetails.categoryOf(failure));
    assertTrue(failure.getMessage().contains("Configured field 'email' is not writable"));
    assertTrue(failure.getMessage().contains(GetterOnlyCustomerRow.class.getName()));
  }

    private static ColumnConfig column(String name, String type) {
        ColumnConfig column = new ColumnConfig();
        column.setName(name);
        column.setType(type);
        return column;
    }

    private static FieldSet fieldSet(String id, String name, String email) {
        return new DefaultFieldSet(
                new String[]{id, name, email},
                new String[]{"id", "name", "email"}
        );
    }

    public static class CustomerRow {
        private int id;
        private String name;
        private String email;

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

  public static class GetterOnlyCustomerRow {
    private int id;
    private String email;

    public int getId() {
      return id;
    }

    public void setId(int id) {
      this.id = id;
    }

    public String getEmail() {
      return email;
    }
  }
}


