package com.etl.common.util;

import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;

import com.etl.reader.DynamicReaderFactory;
import com.etl.writer.DynamicWriterFactory;

/**
 * Utility class to dynamically create ItemReader and ItemWriter instances
 * using reflection based on fully qualified class names.
 */
public class DynamicBatchUtils {

    /**
     * Create an ItemReader dynamically for the given source class.
     *
     * @param readerFactory the factory to create readers
     * @param config the SourceConfig for the reader
     * @return an ItemReader instance
     * @throws Exception if creation fails
     */
    public static <T> ItemReader<T> getDynamicReader(
            DynamicReaderFactory readerFactory,
            SourceConfig config,
            ResolvedModelMetadata metadata) throws Exception {

        Class<T> clazz = GeneratedModelClassResolver.resolveSourceClass(metadata);
        return readerFactory.createReader(config, clazz);
    }


    /**
     * Create an ItemWriter dynamically for the given target class.
     *
     * @param writerFactory the factory to create writers
     * @param targetConfig the TargetConfig configuration
     * @param targetClass the resolved class the writer should serialize
     * @return an ItemWriter instance
     * @throws Exception if creation fails
     */
    public static ItemWriter<Object> getDynamicWriter(DynamicWriterFactory writerFactory,
                                                      TargetConfig targetConfig,
                                                      Class<?> targetClass) throws Exception {
        return writerFactory.createWriter(targetConfig, targetClass);
    }
}
