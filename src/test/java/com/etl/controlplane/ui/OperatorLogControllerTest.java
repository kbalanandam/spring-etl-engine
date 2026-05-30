package com.etl.controlplane.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperatorLogController.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.main.web-application-type=servlet",
        "etl.logging.base-dir=target/operator-log-controller-test-logs"
})
class OperatorLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesExistingLogFileUnderOperatorLogsPath() throws Exception {
        Path logsRoot = Path.of("target/operator-log-controller-test-logs").toAbsolutePath().normalize();
        Path logFile = logsRoot.resolve("2026-04-23/department-load.log");
        Files.createDirectories(logFile.getParent());
        Files.writeString(logFile, "line-1\nline-2\n");

        mockMvc.perform(get("/operator/logs/2026-04-23/department-load.log"))
                .andExpect(status().isOk())
                .andExpect(content().string("line-1\nline-2\n"));
    }

    @Test
    void returnsNotFoundForMissingLogFile() throws Exception {
        mockMvc.perform(get("/operator/logs/2026-04-23/missing.log"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsPathTraversalOutsideLogRoot() throws Exception {
        mockMvc.perform(get("/operator/logs/..%2F..%2Fpom.xml"))
                .andExpect(status().is4xxClientError());
    }
}



