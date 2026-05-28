package com.etl.controlplane.api;

public record SystemInfoResponse(String service, String javaVersion, String profile) {
}

