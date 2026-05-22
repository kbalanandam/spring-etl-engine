package com.etl.reader;

import com.etl.enums.ModelFormat;
import com.etl.exception.FactoryException;
import com.etl.reader.spi.TestReaderExtensionProvider;
import com.etl.reader.spi.ReaderExtensionProvider;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ItemReader;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicReaderDefaultsTest {

    @Test
    void defaultReaders_includeBuiltInAndDiscoveredReaders() {
        Set<ModelFormat> formats = DynamicReaderDefaults.defaultReaders().stream()
                .map(DynamicReader::getFormat)
                .collect(Collectors.toSet());

        assertTrue(formats.contains(ModelFormat.CSV));
        assertTrue(formats.contains(ModelFormat.XML));
        assertTrue(formats.contains(ModelFormat.RELATIONAL));
        assertTrue(formats.contains(ModelFormat.JSON));
    }

    @Test
    void defaultReaders_includeDiscoveredReaderType() {
        boolean discovered = DynamicReaderDefaults.defaultReaders().stream()
                .anyMatch(reader -> reader instanceof TestReaderExtensionProvider.TestJsonReader);

        assertTrue(discovered);
    }

    @Test
    void resolveReaders_failsWhenTwoOverrideProvidersClaimSameFormat() {
        ReaderExtensionProvider firstOverride = new ReaderExtensionProvider() {
            @Override
            public String providerId() {
                return "override-1";
            }

            @Override
            public boolean isOverride() {
                return true;
            }

            @Override
            public List<DynamicReader<?>> readers() {
                return List.of(new DynamicReader<>() {
                    @Override
                    public ModelFormat getFormat() {
                        return ModelFormat.CSV;
                    }

                    @Override
                    public ItemReader<Object> getReader(com.etl.config.source.SourceConfig config, Class<Object> clazz) {
                        return () -> null;
                    }
                });
            }
        };

        ReaderExtensionProvider secondOverride = new ReaderExtensionProvider() {
            @Override
            public String providerId() {
                return "override-2";
            }

            @Override
            public boolean isOverride() {
                return true;
            }

            @Override
            public List<DynamicReader<?>> readers() {
                return List.of(new DynamicReader<>() {
                    @Override
                    public ModelFormat getFormat() {
                        return ModelFormat.CSV;
                    }

                    @Override
                    public ItemReader<Object> getReader(com.etl.config.source.SourceConfig config, Class<Object> clazz) {
                        return () -> null;
                    }
                });
            }
        };

        FactoryException failure = assertThrows(FactoryException.class,
                () -> DynamicReaderDefaults.resolveReaders(List.of(firstOverride, secondOverride)));
        assertTrue(failure.getMessage().contains("Multiple readers registered for format: CSV"));
        assertTrue(failure.getMessage().contains("override-1"));
        assertTrue(failure.getMessage().contains("override-2"));
    }
}

