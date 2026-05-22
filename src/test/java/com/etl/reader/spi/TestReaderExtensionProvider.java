package com.etl.reader.spi;

import com.etl.config.source.SourceConfig;
import com.etl.enums.ModelFormat;
import com.etl.reader.DynamicReader;
import org.springframework.batch.item.ItemReader;

import java.util.List;

public class TestReaderExtensionProvider implements ReaderExtensionProvider {

    @Override
    public String providerId() {
        return "test-reader-provider";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public List<DynamicReader<?>> readers() {
        return List.of(new TestJsonReader());
    }

    public static final class TestJsonReader implements DynamicReader<Object> {
        @Override
        public ModelFormat getFormat() {
            return ModelFormat.JSON;
        }

        @Override
        public ItemReader<Object> getReader(SourceConfig config, Class<Object> clazz) {
            return () -> null;
        }
    }
}
