package com.etl.writer.impl;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import javax.xml.transform.stream.StreamResult;
import java.io.OutputStream;
import java.io.FileOutputStream;

public class SingleObjectXmlWriter implements ItemWriter<Object> {
    private final Jaxb2Marshaller marshaller;
    private final String filePath;

    public SingleObjectXmlWriter(Jaxb2Marshaller marshaller, String filePath) {
        this.marshaller = marshaller;
        this.filePath = filePath;
    }

    @Override
    public void write(Chunk<? extends Object> chunk) throws Exception {
        if (chunk == null || chunk.isEmpty()) return;
        Object wrapper = chunk.getItems().get(0); // Only one wrapper object expected
        try (OutputStream os = new FileOutputStream(filePath)) {
            marshaller.marshal(wrapper, new StreamResult(os));
        }
    }
}
