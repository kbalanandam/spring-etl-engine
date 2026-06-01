package com.etl.controlplane.ui;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Serves scenario log files under /operator/logs/** from the configured log base directory.
 */
@Controller
public class OperatorLogController {

    private static final String LOGS_PREFIX = "/operator/logs/";

    private final Path loggingBaseDir;

    public OperatorLogController(@Value("${etl.logging.base-dir:logs}") String loggingBaseDir) {
        this.loggingBaseDir = Path.of(loggingBaseDir).toAbsolutePath().normalize();
    }

    @GetMapping(value = "/operator/logs/**", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Resource> logFile(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith(LOGS_PREFIX)) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        String encodedRelativePath = requestUri.substring(LOGS_PREFIX.length());
        if (encodedRelativePath.isBlank()) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        String relativePath = URLDecoder.decode(encodedRelativePath, StandardCharsets.UTF_8);
        Path resolvedPath = loggingBaseDir.resolve(relativePath).normalize();
        if (!resolvedPath.startsWith(loggingBaseDir)) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid log path.");
        }

        if (!Files.isRegularFile(resolvedPath)) {
            throw new ResponseStatusException(NOT_FOUND);
        }

        try {
            InputStream stream = Files.newInputStream(resolvedPath);
            InputStreamResource resource = new InputStreamResource(stream);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resolvedPath.getFileName() + "\"")
                    .body(resource);
        } catch (IOException ex) {
            throw new ResponseStatusException(NOT_FOUND, "Unable to read log file.", ex);
        }
    }
}

