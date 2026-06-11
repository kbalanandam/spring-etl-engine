package com.etl.controlplane.monitoring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Shared structured-log scanner that preserves raw line order and parser metadata.
 */
final class StructuredScenarioLogScanner {

    private StructuredScenarioLogScanner() {
    }

    static List<ParsedScenarioLogLine> read(Path logPath, StructuredLogEventParser parser) throws IOException {
        List<ParsedScenarioLogLine> lines = new ArrayList<>();
        try (Stream<String> stream = Files.lines(logPath)) {
            List<String> rawLines = stream.toList();
            for (int index = 0; index < rawLines.size(); index++) {
                String rawLine = rawLines.get(index);
                int lineNumber = index + 1;
                Optional<StructuredLogEvent> event = parser.parse(rawLine, logPath, lineNumber);
                lines.add(new ParsedScenarioLogLine(rawLine, lineNumber, event.orElse(null)));
            }
        }
        return lines;
    }

    record ParsedScenarioLogLine(
            String rawLine,
            int lineNumber,
            StructuredLogEvent event
    ) {
    }
}

