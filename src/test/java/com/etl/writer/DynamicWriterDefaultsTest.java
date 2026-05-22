package com.etl.writer;

import com.etl.enums.ModelFormat;
import com.etl.exception.FactoryException;
import com.etl.writer.spi.TestWriterExtensionProvider;
import com.etl.writer.spi.WriterExtensionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ItemWriter;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicWriterDefaultsTest {

    @Test
    void defaultWriters_includeAllBuiltInFormats() {
        Set<ModelFormat> formats = DynamicWriterDefaults.defaultWriters().stream()
                .map(DynamicWriter::getFormat)
                .collect(Collectors.toSet());

        assertTrue(formats.contains(ModelFormat.CSV));
        assertTrue(formats.contains(ModelFormat.JSON));
        assertTrue(formats.contains(ModelFormat.XML));
        assertTrue(formats.contains(ModelFormat.RELATIONAL));
    }

    @Test
    void defaultWriters_includeDiscoveredWriterType() {
        boolean discovered = DynamicWriterDefaults.defaultWriters().stream()
                .anyMatch(writer -> writer instanceof TestWriterExtensionProvider.TestCsvWriter);

        assertTrue(discovered);
    }

    @Test
    void defaultWriters_preferOverrideProviderWhenFormatConflicts() {
        Optional<DynamicWriter> csvWriter = DynamicWriterDefaults.defaultWriters().stream()
                .filter(writer -> writer.getFormat() == ModelFormat.CSV)
                .findFirst();

        assertTrue(csvWriter.isPresent());
        assertTrue(csvWriter.get() instanceof TestWriterExtensionProvider.TestCsvWriter);
    }

    @Test
    void resolveWriters_failsWhenTwoOverrideProvidersClaimSameFormat() {
        WriterExtensionProvider firstOverride = new WriterExtensionProvider() {
            @Override
            public String providerId() {
                return "override-1";
            }

            @Override
            public boolean isOverride() {
                return true;
            }

            @Override
            public List<DynamicWriter> writers() {
                return List.of(new DynamicWriter() {
                    @Override
                    public ModelFormat getFormat() {
                        return ModelFormat.CSV;
                    }

                    @Override
                    public ItemWriter<Object> getWriter(com.etl.config.target.TargetConfig config, Class<?> clazz) {
                        return chunk -> {
                        };
                    }
                });
            }
        };

        WriterExtensionProvider secondOverride = new WriterExtensionProvider() {
            @Override
            public String providerId() {
                return "override-2";
            }

            @Override
            public boolean isOverride() {
                return true;
            }

            @Override
            public List<DynamicWriter> writers() {
                return List.of(new DynamicWriter() {
                    @Override
                    public ModelFormat getFormat() {
                        return ModelFormat.CSV;
                    }

                    @Override
                    public ItemWriter<Object> getWriter(com.etl.config.target.TargetConfig config, Class<?> clazz) {
                        return chunk -> {
                        };
                    }
                });
            }
        };

        FactoryException failure = assertThrows(FactoryException.class,
                () -> DynamicWriterDefaults.resolveWriters(List.of(firstOverride, secondOverride)));
        assertTrue(failure.getMessage().contains("Multiple writers registered for format: CSV"));
        assertTrue(failure.getMessage().contains("override-1"));
        assertTrue(failure.getMessage().contains("override-2"));
    }
}


