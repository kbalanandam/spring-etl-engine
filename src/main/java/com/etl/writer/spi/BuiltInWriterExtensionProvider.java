package com.etl.writer.spi;

import com.etl.writer.DynamicWriter;
import com.etl.writer.impl.CsvDynamicWriter;
import com.etl.writer.impl.JsonDynamicWriter;
import com.etl.writer.impl.RelationalDynamicWriter;
import com.etl.writer.impl.XmlDynamicWriter;

import java.util.List;

/**
 * Built-in writer registrations shipped with the engine.
 */
public final class BuiltInWriterExtensionProvider implements WriterExtensionProvider {

    @Override
    public String providerId() {
        return "builtin";
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

    @Override
    public List<DynamicWriter> writers() {
        return List.of(
                new CsvDynamicWriter(),
                new JsonDynamicWriter(),
                new XmlDynamicWriter(),
                new RelationalDynamicWriter()
        );
    }
}
