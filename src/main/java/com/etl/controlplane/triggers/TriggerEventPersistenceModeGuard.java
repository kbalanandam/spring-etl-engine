package com.etl.controlplane.triggers;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Guardrail that prevents accidental trigger-history mode switches across restarts.
 */
@Component
public class TriggerEventPersistenceModeGuard {

	private static final Logger log = LoggerFactory.getLogger(TriggerEventPersistenceModeGuard.class);
	private static final Set<String> SUPPORTED_MODES = Set.of("jdbc", "memory");

	private final String currentMode;
	private final Path markerPath;
	private final boolean allowReset;

	public TriggerEventPersistenceModeGuard(
			@Value("${controlplane.triggers.persistence.mode:memory}") String currentMode,
			@Value("${controlplane.triggers.persistence.mode-marker-path:.controlplane/trigger-event-persistence-mode.marker}") String markerPath,
			@Value("${controlplane.triggers.persistence.allow-mode-switch:false}") boolean allowReset) {
		this.currentMode = normalizeMode(currentMode);
		this.markerPath = Path.of(markerPath);
		this.allowReset = allowReset;
	}

	@PostConstruct
	void validateModeSwitch() {
		if (!SUPPORTED_MODES.contains(currentMode)) {
			throw new IllegalStateException("Unsupported controlplane.triggers.persistence.mode='" + currentMode
					+ "'. Supported values: jdbc, memory.");
		}

		String previousMode = readExistingMode();
		if (!previousMode.isBlank() && !previousMode.equals(currentMode)) {
			if (!allowReset) {
				throw new IllegalStateException("Trigger-event persistence mode switch detected: previous='" + previousMode
						+ "', current='" + currentMode + "'. Switching between JDBC and memory can cause trigger-history gaps"
						+ " or duplicate operator interpretation. If this reset is intentional, restart with"
						+ " controlplane.triggers.persistence.allow-mode-switch=true.");
			}
			log.warn("CONTROL_PLANE_GUARDRAIL event=trigger_persistence_mode_switched previousMode={} currentMode={} markerPath={}",
					previousMode,
					currentMode,
					markerPath);
		}

		writeCurrentMode();
	}

	private String readExistingMode() {
		if (!Files.exists(markerPath)) {
			return "";
		}
		try {
			return normalizeMode(Files.readString(markerPath, StandardCharsets.UTF_8));
		} catch (IOException ex) {
			throw new IllegalStateException("Failed reading trigger persistence marker at " + markerPath + ".", ex);
		}
	}

	private void writeCurrentMode() {
		try {
			Path parent = markerPath.getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Files.writeString(markerPath, currentMode + System.lineSeparator(), StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new IllegalStateException("Failed writing trigger persistence marker at " + markerPath + ".", ex);
		}
	}

	private String normalizeMode(String mode) {
		if (mode == null) {
			return "";
		}
		return mode.trim().toLowerCase(Locale.ROOT);
	}
}

