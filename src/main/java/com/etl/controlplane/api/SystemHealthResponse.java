package com.etl.controlplane.api;

import java.time.Instant;

public record SystemHealthResponse(String status, Instant timestamp) {
}

