package com.etl.common.util;

import java.lang.reflect.Method;

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
     * @param sourceClassName fully qualified class name of the source model
     * @return an ItemReader instance
     * @throws Exception if creation fails
     */
    @SuppressWarnings("unchecked")
    public static <T> ItemReader<T> getDynamicReader(
            DynamicReaderFactory readerFactory,
            SourceConfig config,
            String sourceClassName) throws Exception {

        Class<T> clazz = (Class<T>) Class.forName(sourceClassName);
        return readerFactory.createReader(config, clazz);
    }


    /**
     * Create an ItemWriter dynamically for the given target class.
     *
     * @param writerFactory the factory to create writers
     * @param targetConfig the TargetConfig configuration
     * @param targetClassName fully qualified class name of the target model
     * @return an ItemWriter instance
     * @throws Exception if creation fails
     */
    @SuppressWarnings("unchecked")
    public static ItemWriter<Object> getDynamicWriter(DynamicWriterFactory writerFactory,
                                                      TargetConfig targetConfig,
                                                      String targetClassName) throws Exception {
      //  Class<?> targetClass = Class.forName(targetClassName);
        Class<?> targetClass = Class.forName(targetConfig.getPackageName() + "." + targetConfig.getTargetName());

        Method createWriterMethod = writerFactory.getClass()
                .getMethod("createWriter", TargetConfig.class, Class.class);

        Object writer = createWriterMethod.invoke(writerFactory, targetConfig, targetClass);

        return (ItemWriter<Object>) writer;
    }
}
