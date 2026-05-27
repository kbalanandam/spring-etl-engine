package com.etl.controlplane.api;

/**
 * Minimal trigger-now request payload for UI integration.
 */
public record TriggerNowRequest(String reason, String requestedBy) {
}

