package com.etl.reader.spi;

import com.etl.reader.DynamicReader;
import com.etl.reader.impl.CsvDynamicReader;
import com.etl.reader.impl.RelationalDynamicReader;
import com.etl.reader.impl.XmlDynamicReader;

import java.util.List;

/**
 * Built-in reader registrations shipped with the engine.
 */
public final class BuiltInReaderExtensionProvider implements ReaderExtensionProvider {

    @Override
    public String providerId() {
        return "builtin";
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

    @Override
    public List<DynamicReader<?>> readers() {
        return List.of(
                new CsvDynamicReader<>(),
                new XmlDynamicReader<>(),
                new RelationalDynamicReader<>()
        );
    }
}
