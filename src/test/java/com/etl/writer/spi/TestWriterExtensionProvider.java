package com.etl.writer.spi;

import com.etl.config.target.TargetConfig;
import com.etl.enums.ModelFormat;
import com.etl.writer.DynamicWriter;
import org.springframework.batch.item.ItemWriter;

import java.util.List;

public class TestWriterExtensionProvider implements WriterExtensionProvider {

    @Override
    public String providerId() {
        return "test-writer-provider";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public boolean isOverride() {
        return true;
    }

    @Override
    public List<DynamicWriter> writers() {
        return List.of(new TestCsvWriter());
    }

    public static final class TestCsvWriter implements DynamicWriter {
        @Override
        public ModelFormat getFormat() {
            return ModelFormat.CSV;
        }

        @Override
        public ItemWriter<Object> getWriter(TargetConfig config, Class<?> clazz) {
            return chunk -> {
            };
        }
    }
}

