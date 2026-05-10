package com.etl.source.xml.runtime;

import java.util.List;
import java.util.Map;

/**
 * Standardized output returned by XML flattening strategies.
 */
public final class XmlFlatteningResult {

    private final List<Map<String, Object>> rows;
    private final int recordCount;
    private final List<String> warningMessages;
    private final Map<String, Object> metadata;

    private XmlFlatteningResult(List<Map<String, Object>> rows,
                                List<String> warningMessages,
                                Map<String, Object> metadata) {
        this.rows = rows == null
                ? List.of()
                : rows.stream().map(Map::copyOf).toList();
        this.recordCount = this.rows.size();
        this.warningMessages = warningMessages == null ? List.of() : List.copyOf(warningMessages);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static XmlFlatteningResult ofRows(List<Map<String, Object>> rows) {
        return new XmlFlatteningResult(rows, List.of(), Map.of());
    }

    public static XmlFlatteningResult ofRows(List<Map<String, Object>> rows,
                                             List<String> warningMessages,
                                             Map<String, Object> metadata) {
        return new XmlFlatteningResult(rows, warningMessages, metadata);
    }

    public static XmlFlatteningResult empty() {
        return new XmlFlatteningResult(List.of(), List.of(), Map.of());
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public int getRecordCount() {
        return recordCount;
    }

    public List<String> getWarningMessages() {
        return warningMessages;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}

